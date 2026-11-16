package com.workbuddy.iptvplayer

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView

class PlayerActivity : AppCompatActivity() {

    private lateinit var prefs: AppPrefs
    private lateinit var playerView: PlayerView
    private lateinit var infoBar: LinearLayout
    private lateinit var controlBar: LinearLayout
    private lateinit var tvChannel: TextView
    private lateinit var tvMeta: TextView
    private lateinit var tvEpg: TextView
    private lateinit var tvError: TextView
    private lateinit var pbBuffering: ProgressBar
    private lateinit var btnFav: Button

    private var player: ExoPlayer? = null
    private var httpFactory: DefaultHttpDataSource.Factory? = null
    private var currentIndex = 0
    private var retryCount = 0

    private val handler = Handler(Looper.getMainLooper())
    private val hideBarsRunnable = Runnable {
        infoBar.visibility = View.GONE
        controlBar.visibility = View.GONE
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)
        prefs = AppPrefs(this)

        playerView = findViewById(R.id.player_view)
        infoBar = findViewById(R.id.info_bar)
        controlBar = findViewById(R.id.control_bar)
        tvChannel = findViewById(R.id.tv_channel)
        tvMeta = findViewById(R.id.tv_meta)
        tvEpg = findViewById(R.id.tv_epg)
        tvError = findViewById(R.id.tv_error)
        pbBuffering = findViewById(R.id.pb_buffering)
        btnFav = findViewById(R.id.btn_fav)

        findViewById<Button>(R.id.btn_prev).setOnClickListener { play(currentIndex - 1) }
        findViewById<Button>(R.id.btn_next).setOnClickListener { play(currentIndex + 1) }
        findViewById<Button>(R.id.btn_list).setOnClickListener { showChannelList() }
        findViewById<Button>(R.id.btn_back).setOnClickListener { finish() }
        btnFav.setOnClickListener { toggleFavorite() }
        tvError.setOnClickListener {
            tvError.visibility = View.GONE
            player?.prepare()
            player?.play()
        }
        hideSystemUi()
    }

    override fun onResume() {
        super.onResume()
        hideSystemUi()
        if (player == null) initPlayer()
    }

    override fun onPause() {
        releasePlayer()
        super.onPause()
    }

    // ---------------------------------------------------------------- player

    private fun initPlayer() {
        val channels = PlaylistStore.channels
        if (channels.isEmpty()) {
            Toast.makeText(this, "频道列表已失效，请返回重新加载", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val factory = DefaultHttpDataSource.Factory()
            .setUserAgent(prefs.userAgent.ifBlank { PlaylistLoader.DEFAULT_UA })
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)
            .setAllowCrossProtocolRedirects(true)
        httpFactory = factory

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(factory))
            .build()
            .also { exo ->
                exo.playWhenReady = true
                exo.addListener(playerListener)
                playerView.player = exo
            }

        val startIndex = intent.getIntExtra(EXTRA_INDEX, PlaylistStore.currentIndex)
        play(startIndex)
        showBars()
    }

    private fun releasePlayer() {
        handler.removeCallbacks(hideBarsRunnable)
        player?.removeListener(playerListener)
        player?.release()
        player = null
        playerView.player = null
        httpFactory = null
    }

    private fun play(index: Int) {
        val channels = PlaylistStore.channels
        if (channels.isEmpty()) return
        currentIndex = ((index % channels.size) + channels.size) % channels.size
        val channel = channels[currentIndex]
        PlaylistStore.currentIndex = currentIndex
        prefs.lastChannelKey = channel.key
        retryCount = 0
        tvError.visibility = View.GONE
        updateInfo(channel)

        // Per-channel UA / headers from #KODIPROP or "|" URL options.
        val headers = LinkedHashMap<String, String>().apply {
            if (prefs.referer.isNotBlank()) put("Referer", prefs.referer)
            putAll(PlaylistLoader.parseHeaders(prefs.extraHeaders))
            putAll(channel.headers)
            channel.userAgent?.let { put("User-Agent", it) }
        }
        httpFactory?.setDefaultRequestProperties(headers)

        val item = MediaItem.Builder().setUri(channel.url).build()
        player?.apply {
            setMediaItem(item)
            prepare()
            play()
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            pbBuffering.visibility = if (state == Player.STATE_BUFFERING) View.VISIBLE else View.GONE
            if (state == Player.STATE_READY) {
                tvError.visibility = View.GONE
                retryCount = 0
                hideBarsLater()
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) hideBarsLater()
        }

        override fun onPlayerError(error: PlaybackException) {
            pbBuffering.visibility = View.GONE
            tvError.text = buildString {
                append(getString(R.string.play_failed))
                append("\n")
                append(error.errorCodeName)
                error.message?.let { append("\n").append(it) }
                append("\n\n点此重试")
            }
            tvError.visibility = View.VISIBLE
            showBars()
            if (retryCount < 2) {
                retryCount++
                handler.postDelayed({
                    if (!isFinishing && !isDestroyed) {
                        player?.prepare()
                        player?.play()
                    }
                }, 2_000)
            }
        }
    }

    // ---------------------------------------------------------------- ui

    private fun updateInfo(channel: Channel) {
        tvChannel.text = channel.name
        tvMeta.text = "${channel.group} · 频道 ${channel.number} · ${currentIndex + 1}/${PlaylistStore.channels.size}"
        btnFav.text = if (prefs.isFavorite(channel.key)) "★" else "☆"
        val programme = Epg.now(channel.tvgId, channel.tvgName, channel.name)
        if (programme != null) {
            tvEpg.text = "正在播放：${programme.title}"
            tvEpg.visibility = View.VISIBLE
        } else {
            tvEpg.visibility = View.GONE
        }
    }

    private fun showBars() {
        infoBar.visibility = View.VISIBLE
        controlBar.visibility = View.VISIBLE
        hideBarsLater()
    }

    private fun hideBarsLater() {
        handler.removeCallbacks(hideBarsRunnable)
        handler.postDelayed(hideBarsRunnable, 4_000)
    }

    private fun toggleFavorite() {
        val channels = PlaylistStore.channels
        if (channels.isEmpty()) return
        val channel = channels[currentIndex]
        val added = prefs.toggleFavorite(channel.key)
        btnFav.text = if (added) "★" else "☆"
        Toast.makeText(this, if (added) "已收藏" else "已取消收藏", Toast.LENGTH_SHORT).show()
        showBars()
    }

    private fun showChannelList() {
        val channels = PlaylistStore.channels
        if (channels.isEmpty()) return
        val labels = Array(channels.size) { i ->
            "${channels[i].number}. ${channels[i].name}"
        }
        AlertDialog.Builder(this)
            .setTitle("频道列表（${channels.size}）")
            .setItems(labels) { _, which -> play(which) }
            .show()
        showBars()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN) showBars()
        return super.dispatchTouchEvent(ev)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        showBars()
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_CHANNEL_UP,
            KeyEvent.KEYCODE_MEDIA_PREVIOUS,
            KeyEvent.KEYCODE_PAGE_UP -> {
                play(currentIndex - 1)
                true
            }
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_CHANNEL_DOWN,
            KeyEvent.KEYCODE_MEDIA_NEXT,
            KeyEvent.KEYCODE_PAGE_DOWN -> {
                play(currentIndex + 1)
                true
            }
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> {
                finish()
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    @SuppressLint("NewApi")
    private fun hideSystemUi() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.systemBars())
                controller.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                )
        }
    }

    companion object {
        const val EXTRA_INDEX = "channel_index"
    }
}
