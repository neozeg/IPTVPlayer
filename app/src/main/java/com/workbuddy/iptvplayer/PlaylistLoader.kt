package com.workbuddy.iptvplayer

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

/** Downloads / reads the playlist, with an offline cache fallback. */
object PlaylistLoader {

    const val DEFAULT_UA =
        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"

    data class LoadResult(
        val channels: List<Channel>,
        val epgUrl: String?,
        val fromCache: Boolean
    )

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    suspend fun load(context: Context, prefs: AppPrefs): LoadResult = withContext(Dispatchers.IO) {
        val cacheFile = File(context.filesDir, "playlist_cache.m3u")

        // 1) Text pasted by the user always wins (no network needed).
        val pasted = prefs.pastedPlaylist
        if (pasted.isNotBlank()) {
            return@withContext parseAndCache(pasted, prefs.sourceUrl.ifBlank { null }, cacheFile, false)
        }

        // 2) A local .m3u / .m3u8 document picked through SAF.
        val local = prefs.fileUri
        if (local.isNotBlank()) {
            val stream = context.contentResolver.openInputStream(Uri.parse(local))
                ?: throw IllegalStateException("无法打开本地文件，请重新导入")
            val text = stream.use { decodeBytes(it.readBytes(), "") }
            return@withContext parseAndCache(text, null, cacheFile, false)
        }

        // 3) Remote URL, with offline cache when the network is unavailable.
        val url = prefs.sourceUrl
        if (url.isBlank()) return@withContext LoadResult(emptyList(), null, false)

        var fromCache = false
        val text = try {
            fetchText(url, prefs)
        } catch (e: Exception) {
            if (cacheFile.exists() && cacheFile.length() > 0) {
                fromCache = true
                cacheFile.readText()
            } else {
                throw e
            }
        }
        parseAndCache(text, url, cacheFile, fromCache)
    }

    private fun parseAndCache(
        text: String,
        base: String?,
        cache: File,
        fromCache: Boolean
    ): LoadResult {
        val parsed = M3uParser.parse(text, base)
        runCatching { cache.writeText(text) }
        return LoadResult(parsed.channels, parsed.epgUrl, fromCache)
    }

    /** Shared HTTP text fetch, also used for XMLTV EPG (handles .gz payloads). */
    fun fetchText(url: String, prefs: AppPrefs): String {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", prefs.userAgent.ifBlank { DEFAULT_UA })
            .header("Accept", "*/*")
        if (prefs.referer.isNotBlank()) builder.header("Referer", prefs.referer)
        for ((key, value) in parseHeaders(prefs.extraHeaders)) builder.header(key, value)

        client.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code}：$url")
            }
            val bytes = response.body?.bytes() ?: throw IllegalStateException("空响应：$url")
            return decodeBytes(bytes, response.header("Content-Type") ?: "")
        }
    }

    /** "Key: Value" per line -> request headers. */
    fun parseHeaders(raw: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for (line in raw.lines()) {
            val trimmed = line.trim()
            val colon = trimmed.indexOf(':')
            if (colon > 0 && !trimmed.startsWith("#")) {
                val key = trimmed.substring(0, colon).trim()
                val value = trimmed.substring(colon + 1).trim()
                if (key.isNotEmpty() && value.isNotEmpty()) out[key] = value
            }
        }
        return out
    }

    private fun decodeBytes(raw: ByteArray, contentType: String): String {
        val bytes = if (raw.size > 2 && raw[0] == 0x1f.toByte() && raw[1] == 0x8b.toByte()) {
            runCatching { GZIPInputStream(ByteArrayInputStream(raw)).use { it.readBytes() } }
                .getOrDefault(raw)
        } else {
            raw
        }

        val declared = Regex("""charset\s*=\s*([\w\-]+)""", RegexOption.IGNORE_CASE)
            .find(contentType)?.groupValues?.get(1)
        val charset = if (declared.isNullOrBlank()) {
            Charsets.UTF_8
        } else {
            runCatching { Charset.forName(declared) }.getOrDefault(Charsets.UTF_8)
        }

        var text = String(bytes, charset)
        // Chinese playlists are frequently GBK without declaring a charset.
        if (declared.isNullOrBlank() && text.count { it == '\uFFFD' } > 2) {
            val gbk = runCatching { String(bytes, Charset.forName("GBK")) }.getOrNull()
            if (gbk != null && gbk.count { it == '\uFFFD' } < text.count { it == '\uFFFD' }) {
                text = gbk
            }
        }
        return text.removePrefix("\uFEFF")
    }
}
