package com.workbuddy.iptvplayer

/**
 * In-process holder for the loaded playlist. Passing a few thousand channels
 * through an Intent would exceed the Binder transaction limit, so both
 * activities read from here instead. MainActivity always reloads on start.
 */
object PlaylistStore {
    @Volatile var channels: List<Channel> = emptyList()
    @Volatile var epgUrlFromPlaylist: String? = null
    @Volatile var currentIndex: Int = 0

    fun groups(): List<String> = channels.map { it.group }.distinct().sorted()
}
