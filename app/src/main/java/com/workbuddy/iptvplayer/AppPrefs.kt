package com.workbuddy.iptvplayer

import android.content.Context

/** Small SharedPreferences wrapper. Everything stays on the device. */
class AppPrefs(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var sourceUrl: String
        get() = sp.getString(K_SOURCE_URL, "") ?: ""
        set(v) = sp.edit().putString(K_SOURCE_URL, v.trim()).apply()

    var fileUri: String
        get() = sp.getString(K_FILE_URI, "") ?: ""
        set(v) = sp.edit().putString(K_FILE_URI, v).apply()

    var pastedPlaylist: String
        get() = sp.getString(K_PASTED, "") ?: ""
        set(v) = sp.edit().putString(K_PASTED, v).apply()

    var epgUrl: String
        get() = sp.getString(K_EPG_URL, "") ?: ""
        set(v) = sp.edit().putString(K_EPG_URL, v.trim()).apply()

    var userAgent: String
        get() = sp.getString(K_UA, "") ?: ""
        set(v) = sp.edit().putString(K_UA, v.trim()).apply()

    var referer: String
        get() = sp.getString(K_REFERER, "") ?: ""
        set(v) = sp.edit().putString(K_REFERER, v.trim()).apply()

    var extraHeaders: String
        get() = sp.getString(K_HEADERS, "") ?: ""
        set(v) = sp.edit().putString(K_HEADERS, v).apply()

    var lastGroup: String
        get() = sp.getString(K_LAST_GROUP, "") ?: ""
        set(v) = sp.edit().putString(K_LAST_GROUP, v).apply()

    var lastChannelKey: String
        get() = sp.getString(K_LAST_CHANNEL, "") ?: ""
        set(v) = sp.edit().putString(K_LAST_CHANNEL, v).apply()

    fun hasSource(): Boolean =
        sourceUrl.isNotBlank() || fileUri.isNotBlank() || pastedPlaylist.isNotBlank()

    fun favorites(): Set<String> =
        HashSet(sp.getStringSet(K_FAV, emptySet()) ?: emptySet())

    fun isFavorite(key: String): Boolean = favorites().contains(key)

    fun toggleFavorite(key: String): Boolean {
        val set = HashSet(favorites())
        val added = set.add(key)
        if (!added) set.remove(key)
        sp.edit().putStringSet(K_FAV, set).apply()
        return added
    }

    private companion object {
        const val PREFS_NAME = "iptv_player_prefs"
        const val K_SOURCE_URL = "source_url"
        const val K_FILE_URI = "file_uri"
        const val K_PASTED = "pasted_playlist"
        const val K_EPG_URL = "epg_url"
        const val K_UA = "user_agent"
        const val K_REFERER = "referer"
        const val K_HEADERS = "extra_headers"
        const val K_LAST_GROUP = "last_group"
        const val K_LAST_CHANNEL = "last_channel"
        const val K_FAV = "favorites"
    }
}
