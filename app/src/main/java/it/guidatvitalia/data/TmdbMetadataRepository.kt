package it.guidatvitalia.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL

data class CastMember(
    val name: String,
    val character: String,
    val biography: String?,
    val wikipediaUrl: String?
)

data class ProgramMetadata(
    val posterUrl: String?,
    val rating: Double?,
    val voteCount: Int,
    val cast: List<CastMember>
)

/**
 * TMDB metadata client. The user's API key is kept in app preferences and is
 * never written to the repository. Missing matches are represented as null.
 */
object TmdbMetadataRepository {
    private val cache = mutableMapOf<String, ProgramMetadata?>()

    suspend fun find(title: String, apiKey: String, includeCast: Boolean = false): ProgramMetadata? = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isBlank() || title.isBlank()) return@withContext null
        val cacheKey = title.lowercase() + if (includeCast) "|cast" else "|basic"
        synchronized(cache) { if (cache.containsKey(cacheKey)) return@withContext cache[cacheKey] }
        val result = runCatching { lookup(title, cleanKey, includeCast) }.getOrNull()
        synchronized(cache) { cache[cacheKey] = result }
        result
    }

    private fun lookup(title: String, key: String, includeCast: Boolean): ProgramMetadata? {
        val encoded = URLEncoder.encode(title, "UTF-8")
        val movieSearch = request("https://api.themoviedb.org/3/search/movie?api_key=$key&language=it-IT&query=$encoded")
        val tvSearch = request("https://api.themoviedb.org/3/search/tv?api_key=$key&language=it-IT&query=$encoded")
        val movie = movieSearch?.optJSONArray("results")?.optJSONObject(0)
        val tv = tvSearch?.optJSONArray("results")?.optJSONObject(0)
        val useTv = tv != null && (movie == null ||
            tv.optDouble("popularity", 0.0) >= movie.optDouble("popularity", 0.0))
        val selected = if (useTv) tv else movie
        val id = selected?.optInt("id", 0) ?: 0
        if (id <= 0) return null
        val isTv = useTv
        val detailPath = if (isTv) "tv/$id" else "movie/$id"
        val details = request("https://api.themoviedb.org/3/$detailPath?api_key=$key&language=it-IT")
        val creditsPath = if (isTv) "tv/$id/aggregate_credits" else "movie/$id/credits"
        val credits = if (includeCast) request("https://api.themoviedb.org/3/$creditsPath?api_key=$key&language=it-IT") else null
        val posterPath = details?.optString("poster_path").orEmpty()
        val rating = details?.optDouble("vote_average")?.takeIf { it > 0.0 && !it.isNaN() }
        val voteCount = details?.optInt("vote_count", 0) ?: 0
        val castArray = credits?.optJSONArray("cast")
        val cast = mutableListOf<CastMember>()
        if (castArray != null) {
            for (i in 0 until minOf(castArray.length(), 8)) {
                val person = castArray.optJSONObject(i) ?: continue
                val personId = person.optInt("id", 0)
                val name = person.optString("name").trim()
                if (name.isBlank()) continue
                val role = if (isTv) person.optJSONArray("roles")?.optJSONObject(0)?.optString("character").orEmpty()
                    else person.optString("character")
                val personDetails = if (personId > 0) request("https://api.themoviedb.org/3/person/$personId?api_key=$key&language=it-IT&append_to_response=external_ids") else null
                val wiki = wikipediaUrl(name, personDetails)
                val bio = wiki?.let { wikipediaSummary(it) }
                cast += CastMember(name, role, bio, wiki)
            }
        }
        ProgramMetadata(
            posterUrl = posterPath.takeIf { it.isNotBlank() }?.let { "https://image.tmdb.org/t/p/w342$it" },
            rating = rating,
            voteCount = voteCount,
            cast = cast
        )
    }

    private fun wikipediaUrl(name: String, details: JSONObject?): String? {
        val wikidataId = details?.optJSONObject("external_ids")?.optString("wikidata_id").orEmpty()
        if (wikidataId.isNotBlank()) {
            val entity = request("https://www.wikidata.org/wiki/Special:EntityData/$wikidataId.json")
            val sitelink = entity?.optJSONObject("entities")?.optJSONObject(wikidataId)
                ?.optJSONObject("sitelinks")?.optJSONObject("itwiki")?.optString("title")
            if (!sitelink.isNullOrBlank()) return "https://it.wikipedia.org/wiki/" + URLEncoder.encode(sitelink.replace(' ', '_'), "UTF-8")
        }
        return "https://it.wikipedia.org/w/index.php?search=" + URLEncoder.encode(name, "UTF-8")
    }

    private fun wikipediaSummary(url: String): String? {
        val title = url.substringAfter("/wiki/", "").takeIf { it.isNotBlank() } ?: return null
        if (title.contains("search=")) return null
        val json = request("https://it.wikipedia.org/api/rest_v1/page/summary/$title")
        return json?.optString("extract")?.takeIf { it.isNotBlank() }?.let {
            if (it.length > 220) it.take(217).substringBeforeLast(' ') + "…" else it
        }
    }

    private fun request(address: String): JSONObject? {
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            connectTimeout = 9000
            readTimeout = 12000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "GuidaTvItalia/1.0 (Android app)")
        }
        return try {
            if (connection.responseCode !in 200..299) null
            else JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } finally { connection.disconnect() }
    }
}
