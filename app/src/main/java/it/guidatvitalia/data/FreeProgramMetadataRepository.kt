package it.guidatvitalia.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL

data class CastMember(
    val name: String,
    val character: String,
    val biography: String?,
    val wikipediaUrl: String?,
    val imageUrl: String? = null
)

data class ProgramMetadata(
    val posterUrl: String?,
    val rating: Double?,
    val voteCount: Int,
    val cast: List<CastMember>,
    val overview: String? = null,
    val sourceName: String = "Wikipedia",
    val sourceUrl: String? = null
)

/**
 * Free metadata sources, with no API key:
 * - TVmaze: TV series posters, audience rating and ordered cast.
 * - Wikipedia/Wikimedia: summaries, images and actor biographies.
 * TVmaze data is CC BY-SA; attribution is shown in the app.
 */
object FreeProgramMetadataRepository {
    private val cache = mutableMapOf<String, ProgramMetadata?>()

    suspend fun find(title: String, includeCast: Boolean = false): ProgramMetadata? =
        withContext(Dispatchers.IO) {
            if (title.isBlank()) return@withContext null
            val cacheKey = title.trim().lowercase() + if (includeCast) "|cast" else "|basic"
            synchronized(cache) {
                if (cache.containsKey(cacheKey)) return@withContext cache[cacheKey]
            }
            val result = runCatching { lookup(title.trim(), includeCast) }.getOrNull()
            synchronized(cache) { cache[cacheKey] = result }
            result
        }

    private fun lookup(title: String, includeCast: Boolean): ProgramMetadata? {
        val encoded = URLEncoder.encode(title, "UTF-8")
        val search = requestArray("https://api.tvmaze.com/search/shows?q=$encoded")
        val matches = search?.let { array ->
            (0 until array.length()).mapNotNull { array.optJSONObject(it) }
        }.orEmpty()
        val exact = matches.firstOrNull { item ->
            normalize(item.optJSONObject("show")?.optString("name").orEmpty()) == normalize(title)
        }
        val candidate = exact ?: matches.firstOrNull { it.optDouble("score", 0.0) >= 0.80 }
        val showSummary = candidate?.optJSONObject("show")
        val showId = showSummary?.optInt("id", 0) ?: 0

        if (showId > 0) {
            val show = if (includeCast) {
                requestObject("https://api.tvmaze.com/shows/$showId?embed=cast")
            } else showSummary
            if (show != null) {
                val image = show.optJSONObject("image")
                val rating = show.optJSONObject("rating")?.optDouble("average")
                    ?.takeIf { it > 0.0 && !it.isNaN() }
                val summary = cleanHtml(show.optString("summary").takeIf { it != "null" })
                val cast = if (includeCast) readCast(show.optJSONObject("_embedded")?.optJSONArray("cast")) else emptyList()
                return ProgramMetadata(
                    posterUrl = image?.optString("medium")?.takeIf { it.isNotBlank() && it != "null" },
                    rating = rating,
                    voteCount = 0,
                    cast = cast,
                    overview = summary,
                    sourceName = "TVmaze",
                    sourceUrl = show.optString("url").takeIf { it.isNotBlank() && it != "null" }
                )
            }
        }
        return wikipediaFallback(title)
    }

    private fun readCast(array: JSONArray?): List<CastMember> {
        if (array == null) return emptyList()
        val result = mutableListOf<CastMember>()
        for (i in 0 until minOf(array.length(), 6)) {
            val entry = array.optJSONObject(i) ?: continue
            val person = entry.optJSONObject("person") ?: continue
            val name = person.optString("name").trim()
            if (name.isBlank()) continue
            val character = entry.optJSONObject("character")?.optString("name").orEmpty()
            val image = person.optJSONObject("image")?.optString("medium")
            val wiki = wikipediaPerson(name)
            val wikiUrl = wiki?.first ?: "https://it.wikipedia.org/w/index.php?search=" + URLEncoder.encode(name, "UTF-8")
            result += CastMember(name, character, wiki?.second, wikiUrl, image)
        }
        return result
    }

    private fun wikipediaFallback(title: String): ProgramMetadata? {
        val encoded = URLEncoder.encode(title, "UTF-8")
        val search = requestObject("https://it.wikipedia.org/w/api.php?action=query&list=search&srsearch=$encoded&format=json&utf8=1")
        val first = search?.optJSONObject("query")?.optJSONArray("search")?.optJSONObject(0)
        val pageTitle = first?.optString("title")?.takeIf { !it.isNullOrBlank() } ?: return null
        if (!isReasonableMatch(title, pageTitle)) return null
        val summary = wikipediaSummary(pageTitle) ?: return null
        return ProgramMetadata(
            posterUrl = summary.optJSONObject("thumbnail")?.optString("source"),
            rating = null,
            voteCount = 0,
            cast = emptyList(),
            overview = summary.optString("extract").takeIf { it.isNotBlank() },
            sourceName = "Wikipedia",
            sourceUrl = summary.optJSONObject("content_urls")?.optJSONObject("desktop")?.optString("page")
                ?: "https://it.wikipedia.org/wiki/" + URLEncoder.encode(pageTitle.replace(' ', '_'), "UTF-8")
        )
    }

    private fun wikipediaPerson(name: String): Pair<String, String?>? {
        val encoded = URLEncoder.encode(name, "UTF-8")
        val search = requestObject("https://it.wikipedia.org/w/api.php?action=query&list=search&srsearch=$encoded&format=json&utf8=1")
        val pageTitle = search?.optJSONObject("query")?.optJSONArray("search")?.optJSONObject(0)
            ?.optString("title")?.takeIf { !it.isNullOrBlank() } ?: return null
        val summary = wikipediaSummary(pageTitle)
        val url = summary?.optJSONObject("content_urls")?.optJSONObject("desktop")?.optString("page")
            ?: "https://it.wikipedia.org/wiki/" + URLEncoder.encode(pageTitle.replace(' ', '_'), "UTF-8")
        return url to summary?.optString("extract")?.takeIf { it.isNotBlank() }?.let(::shorten)
    }

    private fun wikipediaSummary(title: String): JSONObject? {
        val encoded = URLEncoder.encode(title.replace(' ', '_'), "UTF-8")
        return requestObject("https://it.wikipedia.org/api/rest_v1/page/summary/$encoded")
    }

    private fun isReasonableMatch(query: String, result: String): Boolean {
        val q = normalize(query)
        val r = normalize(result)
        return q == r || (q.length >= 5 && (r.contains(q) || q.contains(r)))
    }

    private fun normalize(value: String): String = value.lowercase()
        .filter { it.isLetterOrDigit() }

    private fun cleanHtml(value: String?): String? = value?.replace(Regex("<[^>]*>"), " ")
        ?.replace("&nbsp;", " ")?.replace("&amp;", "&")?.replace(Regex("\\s+"), " ")
        ?.trim()?.takeIf { it.isNotBlank() }?.let(::shorten)

    private fun shorten(value: String): String =
        if (value.length > 260) value.take(257).substringBeforeLast(' ') + "…" else value

    private fun requestObject(address: String): JSONObject? = requestRaw(address) as? JSONObject
    private fun requestArray(address: String): JSONArray? = requestRaw(address) as? JSONArray

    private fun requestRaw(address: String): Any? {
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 10000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "GuidaTvItalia/1.1 (Android app; personal TV guide)")
        }
        return try {
            if (connection.responseCode !in 200..299) null
            else {
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                if (body.trimStart().startsWith("[")) JSONArray(body) else JSONObject(body)
            }
        } finally { connection.disconnect() }
    }
}
