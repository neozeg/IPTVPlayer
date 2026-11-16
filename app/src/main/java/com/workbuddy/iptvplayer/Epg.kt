package com.workbuddy.iptvplayer

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Locale

/** Minimal XMLTV reader: enough to show "what's on now" for a channel. */
object Epg {

    data class Programme(
        val title: String,
        val start: Long,
        val stop: Long,
        val channelId: String
    )

    @Volatile
    private var programmes: Map<String, List<Programme>> = emptyMap()

    suspend fun load(context: Context, url: String, prefs: AppPrefs) = withContext(Dispatchers.IO) {
        programmes = parse(PlaylistLoader.fetchText(url, prefs))
    }

    fun clear() {
        programmes = emptyMap()
    }

    fun isLoaded(): Boolean = programmes.isNotEmpty()

    fun now(tvgId: String?, tvgName: String?, name: String): Programme? {
        val map = programmes
        if (map.isEmpty()) return null
        val now = System.currentTimeMillis()
        for (raw in listOfNotNull(tvgId, tvgName, name)) {
            val list = map[normalize(raw)] ?: continue
            list.firstOrNull { now >= it.start && now < it.stop }?.let { return it }
        }
        return null
    }

    private fun normalize(value: String): String =
        value.trim().lowercase(Locale.ROOT).replace(Regex("""[\s\-_.]"""), "")

    private fun parse(xml: String): Map<String, List<Programme>> {
        val out = HashMap<String, MutableList<Programme>>()
        val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }
        val parser = factory.newPullParser()
        parser.setInput(StringReader(xml.removePrefix("\uFEFF")))

        var channelId: String? = null
        var start = 0L
        var stop = 0L
        var title = StringBuilder()
        var inTitle = false
        var event = parser.eventType

        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "programme" -> {
                        channelId = parser.getAttributeValue(null, "channel")
                        start = parseTime(parser.getAttributeValue(null, "start"))
                        stop = parseTime(parser.getAttributeValue(null, "stop"))
                        title = StringBuilder()
                    }
                    "title" -> if (channelId != null) inTitle = true
                }
                XmlPullParser.TEXT -> if (inTitle) title.append(parser.text)
                XmlPullParser.END_TAG -> when (parser.name) {
                    "title" -> inTitle = false
                    "programme" -> {
                        val id = channelId
                        val text = title.toString().trim()
                        if (id != null && text.isNotEmpty() && stop > start) {
                            out.getOrPut(normalize(id)) { ArrayList() }
                                .add(Programme(text, start, stop, id))
                        }
                        channelId = null
                    }
                }
            }
            event = parser.next()
        }
        return out
    }

    private fun parseTime(value: String?): Long {
        if (value.isNullOrBlank()) return 0L
        val raw = value.trim().replace(Regex("""([+\-]\d{2}):(\d{2})"""), "$1$2")
        val patterns = listOf("yyyyMMddHHmmss Z", "yyyyMMddHHmmss")
        for (pattern in patterns) {
            runCatching {
                SimpleDateFormat(pattern, Locale.US).parse(raw)?.let { return it.time }
            }
        }
        return 0L
    }
}
