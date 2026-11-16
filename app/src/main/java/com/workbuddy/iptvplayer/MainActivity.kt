package com.workbuddy.iptvplayer

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: AppPrefs
    private lateinit var adapter: ChannelAdapter

    private lateinit var etSearch: EditText
    private lateinit var btnGroup: Button
    private lateinit var btnFav: Button
    private lateinit var btnSource: Button
    private lateinit var btnRefresh: Button
    private lateinit var btnAddSource: Button
    private lateinit var rvChannels: RecyclerView
    private lateinit var pbLoading: ProgressBar
    private lateinit var emptyView: View
    private lateinit var tvEmptyTitle: TextView
    private lateinit var tvStatus: TextView

    private var allChannels: List<Channel> = emptyList()
    private var groups: List<String> = emptyList()
    private var currentGroup: String = "" // empty means "all groups"
    private var favOnly = false
    private var query = ""
    private var sourceDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = AppPrefs(this)
        bindViews()

        adapter = ChannelAdapter(
            onClick = { channel -> openChannel(channel) },
            onToggleFavorite = { channel -> toggleFavorite(channel) },
            isFavorite = { channel -> prefs.isFavorite(channel.key) }
        )
        rvChannels.layoutManager = LinearLayoutManager(this)
        rvChannels.adapter = adapter

        etSearch.doOnTextChanged { text, _, _, _ ->
            query = text?.toString()?.trim() ?: ""
            applyFilter()
        }
        btnSource.setOnClickListener { showSourceDialog() }
        btnAddSource.setOnClickListener { showSourceDialog() }
        btnRefresh.setOnClickListener { refresh() }
        btnFav.setOnClickListener {
            favOnly = !favOnly
            btnFav.text = if (favOnly) "★" else "☆"
            applyFilter()
        }
        btnGroup.setOnClickListener { showGroupPicker() }

        currentGroup = prefs.lastGroup
        btnGroup.text = currentGroup.ifEmpty { getString(R.string.all_groups) }

        if (!prefs.hasSource()) showSourceDialog() else refresh()
    }

    override fun onResume() {
        super.onResume()
        // Favorites may have changed inside PlayerActivity.
        adapter.notifyDataSetChanged()
    }

    private fun bindViews() {
        etSearch = findViewById(R.id.et_search)
        btnGroup = findViewById(R.id.btn_group)
        btnFav = findViewById(R.id.btn_fav)
        btnSource = findViewById(R.id.btn_source)
        btnRefresh = findViewById(R.id.btn_refresh)
        btnAddSource = findViewById(R.id.btn_add_source)
        rvChannels = findViewById(R.id.rv_channels)
        pbLoading = findViewById(R.id.pb_loading)
        emptyView = findViewById(R.id.empty_view)
        tvEmptyTitle = findViewById(R.id.tv_empty_title)
        tvStatus = findViewById(R.id.tv_status)
    }

    // ---------------------------------------------------------------- source

    private fun showSourceDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_add_source, null)
        val etUrl = view.findViewById<EditText>(R.id.et_url)
        val etPaste = view.findViewById<EditText>(R.id.et_paste)
        val etEpg = view.findViewById<EditText>(R.id.et_epg)
        val etUa = view.findViewById<EditText>(R.id.et_ua)
        val etReferer = view.findViewById<EditText>(R.id.et_referer)
        val etHeaders = view.findViewById<EditText>(R.id.et_headers)
        val btnPick = view.findViewById<Button>(R.id.btn_pick)

        etUrl.setText(prefs.sourceUrl)
        etPaste.setText(prefs.pastedPlaylist)
        etEpg.setText(prefs.epgUrl.ifBlank { PlaylistStore.epgUrlFromPlaylist ?: "" })
        etUa.setText(prefs.userAgent)
        etReferer.setText(prefs.referer)
        etHeaders.setText(prefs.extraHeaders)

        fun saveForm() {
            prefs.sourceUrl = etUrl.text.toString()
            prefs.pastedPlaylist = etPaste.text.toString()
            prefs.epgUrl = etEpg.text.toString()
            prefs.userAgent = etUa.text.toString()
            prefs.referer = etReferer.text.toString()
            prefs.extraHeaders = etHeaders.text.toString()
            // A newly entered URL / pasted text supersedes an earlier local file.
            if (prefs.sourceUrl.isNotBlank() || prefs.pastedPlaylist.isNotBlank()) {
                prefs.fileUri = ""
            }
        }

        btnPick.setOnClickListener {
            saveForm()
            val picker = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            runCatching { startActivityForResult(picker, REQ_PICK_FILE) }
                .onFailure { toast("无法打开文件选择器") }
        }

        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                saveForm()
                if (!prefs.hasSource()) {
                    toast("请填写 M3U 链接、粘贴内容或导入本地文件")
                    return@setOnClickListener
                }
                dialog.dismiss()
                toast(getString(R.string.source_saved))
                Epg.clear()
                refresh()
            }
        }
        sourceDialog = dialog
        dialog.show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_PICK_FILE && resultCode == Activity.RESULT_OK) {
            val uri = data?.data ?: return
            runCatching {
                contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            prefs.fileUri = uri.toString()
            prefs.sourceUrl = ""
            prefs.pastedPlaylist = ""
            sourceDialog?.dismiss()
            toast(getString(R.string.source_saved))
            Epg.clear()
            refresh()
        }
    }

    // ---------------------------------------------------------------- listing

    private fun refresh() {
        pbLoading.visibility = View.VISIBLE
        emptyView.visibility = View.GONE
        tvStatus.text = getString(R.string.loading)
        lifecycleScope.launch {
            try {
                val result = PlaylistLoader.load(this@MainActivity, prefs)
                allChannels = result.channels
                PlaylistStore.channels = result.channels
                PlaylistStore.epgUrlFromPlaylist = result.epgUrl
                groups = PlaylistStore.groups()
                if (currentGroup.isNotEmpty() && currentGroup !in groups) {
                    currentGroup = ""
                    prefs.lastGroup = ""
                    btnGroup.text = getString(R.string.all_groups)
                }
                applyFilter()
                val cacheNote = if (result.fromCache) " · 离线缓存" else ""
                val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                tvStatus.text = "共 ${result.channels.size} 个频道 · ${groups.size} 个分组$cacheNote · 更新 $time"

                val epgUrl = prefs.epgUrl.ifBlank { result.epgUrl ?: "" }
                if (epgUrl.isNotBlank() && !Epg.isLoaded()) {
                    launch { runCatching { Epg.load(this@MainActivity, epgUrl, prefs) } }
                }
            } catch (e: Exception) {
                val message = "载入失败：${e.message}"
                tvStatus.text = message
                toast(message)
            } finally {
                pbLoading.visibility = View.GONE
                updateEmptyState()
            }
        }
    }

    private fun applyFilter() {
        val filtered = allChannels.filter { channel ->
            (currentGroup.isEmpty() || channel.group == currentGroup) &&
                (!favOnly || prefs.isFavorite(channel.key)) &&
                (query.isEmpty() || channel.name.contains(query, ignoreCase = true) ||
                    channel.group.contains(query, ignoreCase = true) ||
                    (channel.tvgName ?: "").contains(query, ignoreCase = true))
        }
        adapter.submit(filtered)
        updateEmptyState()
    }

    private fun updateEmptyState() {
        val loading = pbLoading.visibility == View.VISIBLE
        val empty = adapter.itemCount == 0
        emptyView.visibility = if (empty && !loading) View.VISIBLE else View.GONE
        tvEmptyTitle.text = if (!prefs.hasSource()) {
            getString(R.string.empty_title)
        } else {
            "没有匹配的频道"
        }
        btnAddSource.text = if (!prefs.hasSource()) {
            getString(R.string.add_source)
        } else {
            getString(R.string.source)
        }
    }

    private fun showGroupPicker() {
        val labels = (listOf(getString(R.string.all_groups)) + groups).toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("选择分组")
            .setItems(labels) { _, which ->
                currentGroup = if (which == 0) "" else groups[which - 1]
                prefs.lastGroup = currentGroup
                btnGroup.text = currentGroup.ifEmpty { getString(R.string.all_groups) }
                applyFilter()
            }
            .show()
    }

    private fun toggleFavorite(channel: Channel) {
        val added = prefs.toggleFavorite(channel.key)
        adapter.notifyDataSetChanged()
        toast(if (added) "已收藏：${channel.name}" else "已取消收藏：${channel.name}")
    }

    private fun openChannel(channel: Channel) {
        val index = PlaylistStore.channels.indexOfFirst { it.key == channel.key }
        if (index < 0) return
        PlaylistStore.currentIndex = index
        prefs.lastChannelKey = channel.key
        val intent = Intent(this, PlayerActivity::class.java)
            .putExtra(PlayerActivity.EXTRA_INDEX, index)
        startActivity(intent)
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private companion object {
        const val REQ_PICK_FILE = 1001
    }
}
