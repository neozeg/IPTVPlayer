package com.workbuddy.iptvplayer

import java.net.URI
import java.util.Locale

/**
 * Streaming M3U / M3U8 parser.
 *
 * Handles the tags seen in real-world IPTV playlists:
 *  - #EXTM3U with x-tvg-url / url-tvg (EPG)
 *  - #EXTINF with tvg-id, tvg-name, tvg-logo, tvg-chno, group-title
 *  - #EXTGRP, #KODIPROP, #EXTVLCOPT (UA / Referer / custom headers)
 *  - Kodi style "|" URL options: http://x/y.ts|User-Agent=...&Referer=...
 *  - relative stream URLs (resolved against the playlist URL)
 *  - UTF-8 BOM, CRLF and blank lines
 */
object M3uParser {

    data class Result(
        val channels: List<Channel>,
        val epgUrl: String? = null
    )

    private val ATTRIBUTE = Regex("""([A-Za-z0-9_.\-]+)\s*=\s*"([^"]*)"""")

    fun parse(text: String, baseUrl: String? = null): Result {
        val normalized = text
            .removePrefix("\uFEFF")
            .replace("\r\n", "\n")
            .replace('\r', '\n')

        val channels = ArrayList<Channel>()
        var epgUrl: String? = null

        var pendingName: String? = null
        var pendingGroup: String? = null
        var pendingLogo: String? = null
        var pendingId: String? = null
        var pendingTvgName: String? = null
        var pendingNumber = 0
        var pendingHeaders = LinkedHashMap<String, String>()
        var pendingUa: String? = null
        var extGrp: String? = null
        var index = 0

        fun resetPending() {
            pendingName = null
            pendingGroup = null
            pendingLogo = null
            pendingId = null
            pendingTvgName = null
            pendingNumber = 0
            pendingHeaders = LinkedHashMap()
            pendingUa = null
        }

        for (rawLine in normalized.split('\n')) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            when {
                line.startsWith("#EXTM3U", ignoreCase = true) -> {
                    val attrs = attributes(line)
                    epgUrl = attrs["x-tvg-url"] ?: attrs["url-tvg"] ?: attrs["tvg-url"]
                }

                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    val attrs = attributes(line)
                    val comma = displayNameComma(line)
                    val display = if (comma >= 0) line.substring(comma + 1).trim() else ""
                    pendingName = display.ifEmpty { attrs["tvg-name"] ?: "频道 ${index + 1}" }
                    pendingGroup = (attrs["group-title"] ?: extGrp)?.takeIf { it.isNotBlank() }
                    pendingLogo = attrs["tvg-logo"] ?: attrs["logo"]
                    pendingId = attrs["tvg-id"]
                    pendingTvgName = attrs["tvg-name"]
                    pendingNumber = (attrs["tvg-chno"] ?: attrs["chno"])?.toIntOrNull() ?: 0
                }

                line.startsWith("#EXTGRP", ignoreCase = true) -> {
                    extGrp = line.substringAfter(':', "").trim().takeIf { it.isNotEmpty() }
                    if (pendingGroup == null && extGrp != null) pendingGroup = extGrp
                }

                line.startsWith("#KODIPROP", ignoreCase = true) ||
                    line.startsWith("#EXTVLCOPT", ignoreCase = true) -> {
                    val body = line.substringAfter(':', "")
                    val eq = body.indexOf('=')
                    if (eq > 0) {
                        val key = body.substring(0, eq).trim()
                        val value = body.substring(eq + 1).trim()
                        val lower = key.lowercase(Locale.US)
                        when {
                            lower.contains("user-agent") || lower.contains("useragent") ->
                                pendingUa = value
                            lower.contains("referer") || lower.contains("referrer") ->
                                pendingHeaders["Referer"] = value
                            lower.contains("origin") -> pendingHeaders["Origin"] = value
                            lower.startsWith("http-header") -> {
                                val colon = value.indexOf(':')
                                if (colon > 0) {
                                    pendingHeaders[value.substring(0, colon).trim()] =
                                        value.substring(colon + 1).trim()
                                }
                            }
                        }
                    }
                }

                line.startsWith("#") -> Unit // any other comment / tag

                else -> {
                    val options = splitUrlOptions(line)
                    val resolved = resolveUrl(options.first, baseUrl) ?: continue
                    index++

                    val headers = LinkedHashMap(pendingHeaders).apply { putAll(options.second) }
                    val ua = pendingUa ?: options.second["User-Agent"]

                    channels.add(
                        Channel(
                            name = pendingName ?: "频道 $index",
                            url = resolved,
                            group = pendingGroup ?: Channel.DEFAULT_GROUP,
                            logo = pendingLogo,
                            tvgId = pendingId,
                            tvgName = pendingTvgName,
                            number = if (pendingNumber > 0) pendingNumber else index,
                            headers = headers,
                            userAgent = ua
                        )
                    )
                    resetPending()
                }
            }
        }

        return Result(channels, epgUrl)
    }

    private fun attributes(line: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for (match in ATTRIBUTE.findAll(line)) {
            out[match.groupValues[1].lowercase(Locale.US)] = match.groupValues[2]
        }
        return out
    }

    /** Last comma that is outside quoted attribute values (the display name separator). */
    private fun displayNameComma(line: String): Int {
        var inQuotes = false
        var comma = -1
        for (i in line.indices) {
            when (line[i]) {
                '"' -> inQuotes = !inQuotes
                ',' -> if (!inQuotes) comma = i
            }
        }
        return comma
    }

    /** Splits "url|User-Agent=X&Referer=Y" into the url and request headers. */
    private fun splitUrlOptions(value: String): Pair<String, Map<String, String>> {
        val pipe = value.indexOf('|')
        if (pipe < 0 || pipe == value.length - 1) return value to emptyMap<String, String>()

        val url = value.substring(0, pipe)
        val options = value.substring(pipe + 1)
        if (!options.contains('=', ignoreCase = true)) return value to emptyMap<String, String>()

        val headers = LinkedHashMap<String, String>()
        for (segment in options.split('&', '|')) {
            val eq = segment.indexOf('=')
            if (eq <= 0) continue
            val key = segment.substring(0, eq).trim()
            val v = segment.substring(eq + 1).trim()
            val named = when (key.lowercase(Locale.US)) {
                "user-agent", "ua" -> "User-Agent"
                "referer", "referrer" -> "Referer"
                "origin" -> "Origin"
                else -> key
            }
            if (v.isNotEmpty()) headers[named] = v
        }
        return url to headers
    }

    private fun resolveUrl(value: String, baseUrl: String?): String? {
        val v = value.trim().replace(" ", "%20")
        if (v.isEmpty()) return null
        if (v.startsWith("http://", true) || v.startsWith("https://", true) ||
            v.startsWith("rtmp", true) || v.startsWith("rtsp", true) ||
            v.startsWith("udp", true) || v.startsWith("file:", true) ||
            v.startsWith("content:", true) || v.startsWith("mms", true)
        ) return v

        if (baseUrl.isNullOrBlank()) return null
        return runCatching { URI(baseUrl).resolve(v).toString() }.getOrNull()
    }
}
