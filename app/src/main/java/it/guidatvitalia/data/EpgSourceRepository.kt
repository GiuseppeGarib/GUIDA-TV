package it.guidatvitalia.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

class EpgSourceRepository {
    suspend fun download(sourceUrl: String): EpgData = withContext(Dispatchers.IO) {
        require(sourceUrl.startsWith("https://", ignoreCase = true)) {
            "Per sicurezza è consentita soltanto una sorgente HTTPS."
        }
        val connection = (URL(sourceUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 45_000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "GuidaTVItalia/1.0 (Android)")
            instanceFollowRedirects = true
        }
        try {
            val status = connection.responseCode
            check(status in 200..299) { "Download non riuscito (HTTP $status)." }
            val stream = connection.inputStream.buffered()
            val decoded = if (sourceUrl.endsWith(".gz", true) ||
                connection.contentEncoding.equals("gzip", true) ||
                connection.contentType?.contains("gzip", true) == true) {
                GZIPInputStream(stream)
            } else stream
            decoded.use { XmlTvParser.parse(it) }.also {
                check(it.channels.isNotEmpty()) { "Il file non contiene canali XMLTV validi." }
                check(it.programs.isNotEmpty()) { "Il file non contiene programmi XMLTV." }
            }
        } finally {
            connection.disconnect()
        }
    }
}
