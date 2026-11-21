package it.guidatvitalia.data

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

data class EpgChannel(val id: String, val name: String, val icon: String? = null)
data class EpgProgram(
    val channelId: String,
    val title: String,
    val description: String?,
    val category: String?,
    val startMillis: Long,
    val stopMillis: Long?
)
data class EpgData(val channels: List<EpgChannel>, val programs: List<EpgProgram>)

object XmlTvParser {
    fun parse(input: InputStream): EpgData {
        val parser = Xml.newPullParser()
        parser.setInput(input, null)
        val channels = mutableListOf<EpgChannel>()
        val programs = mutableListOf<EpgProgram>()
        var event = parser.eventType
        var channelId: String? = null
        var channelName: String? = null
        var channelIcon: String? = null
        var programmeChannel: String? = null
        var start: Long? = null
        var stop: Long? = null
        var title: String? = null
        var desc: String? = null
        var category: String? = null
        var currentTag: String? = null

        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    currentTag = parser.name
                    when (parser.name) {
                        "channel" -> {
                            channelId = parser.getAttributeValue(null, "id")
                            channelName = null
                            channelIcon = null
                        }
                        "programme" -> {
                            programmeChannel = parser.getAttributeValue(null, "channel")
                            start = parseXmlTvDate(parser.getAttributeValue(null, "start"))
                            stop = parseXmlTvDate(parser.getAttributeValue(null, "stop"))
                            title = null; desc = null; category = null
                        }
                        "icon" -> if (channelId != null) channelIcon = parser.getAttributeValue(null, "src")
                    }
                }
                XmlPullParser.TEXT -> {
                    val value = parser.text?.trim().orEmpty()
                    when (currentTag) {
                        "display-name" -> if (value.isNotEmpty()) channelName = value
                        "title" -> if (value.isNotEmpty()) title = value
                        "desc" -> if (value.isNotEmpty()) desc = value
                        "category" -> if (value.isNotEmpty()) category = value
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "channel" -> {
                            val id = channelId
                            if (!id.isNullOrBlank()) channels += EpgChannel(id, channelName ?: id, channelIcon)
                            channelId = null
                        }
                        "programme" -> {
                            val channel = programmeChannel
                            val begin = start
                            val name = title
                            if (!channel.isNullOrBlank() && begin != null && !name.isNullOrBlank()) {
                                programs += EpgProgram(channel, name, desc, category, begin, stop)
                            }
                            programmeChannel = null
                        }
                    }
                    currentTag = null
                }
            }
            event = parser.next()
        }
        return EpgData(channels.distinctBy { it.id }, programs.sortedBy { it.startMillis })
    }

    private fun parseXmlTvDate(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        val formats = listOf("yyyyMMddHHmmss Z", "yyyyMMddHHmm Z")
        for (pattern in formats) {
            try {
                val format = SimpleDateFormat(pattern, Locale.ROOT).apply {
                    isLenient = false
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                return format.parse(value.trim())?.time
            } catch (_: Exception) { }
        }
        return null
    }
}
