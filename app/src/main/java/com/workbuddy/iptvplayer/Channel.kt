package com.workbuddy.iptvplayer

/**
 * A single channel parsed from an M3U / M3U8 playlist.
 *
 * [headers] and [userAgent] are per-channel overrides coming from
 * #KODIPROP / #EXTVLCOPT tags or Kodi-style "|" URL options.
 */
data class Channel(
    val name: String,
    val url: String,
    val group: String = DEFAULT_GROUP,
    val logo: String? = null,
    val tvgId: String? = null,
    val tvgName: String? = null,
    val number: Int = 0,
    val headers: Map<String, String> = emptyMap(),
    val userAgent: String? = null
) {
    /** Stable key used for favorites and "last watched channel". */
    val key: String get() = url

    companion object {
        const val DEFAULT_GROUP = "未分组"
    }
}
