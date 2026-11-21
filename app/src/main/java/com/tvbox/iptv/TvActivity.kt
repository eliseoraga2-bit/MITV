package com.tvbox.iptv

import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@androidx.annotation.OptIn(UnstableApi::class)
class TvActivity : AppCompatActivity() {

    private lateinit var sidebar: View
    private lateinit var countryBtn: TextView
    private lateinit var channelList: RecyclerView
    private lateinit var playerView: PlayerView
    private lateinit var banner: TextView
    private lateinit var message: TextView
    private lateinit var adapter: ChannelAdapter
    private lateinit var layoutManager: LinearLayoutManager

    private var countries: List<String> = emptyList()
    private var byCountry: Map<String, List<Channel>> = emptyMap()
    private var countryIndex = 0
    private var current: Channel? = null
    private var fullscreen = false
    private var retriedHls = false
    private var player: ExoPlayer? = null
    private val hideBanner = Runnable { banner.visibility = View.GONE }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_tv)
        hideSystemUi()

        sidebar = findViewById(R.id.sidebar)
        countryBtn = findViewById(R.id.countryBtn)
        channelList = findViewById(R.id.channelList)
        playerView = findViewById(R.id.playerView)
        banner = findViewById(R.id.banner)
        message = findViewById(R.id.message)

        layoutManager = LinearLayoutManager(this)
        channelList.layoutManager = layoutManager
        adapter = ChannelAdapter { ch ->
            if (ch.url == current?.url) enterFullscreen() else play(ch)
        }
        channelList.adapter = adapter

        countryBtn.setOnClickListener { showCountryDialog() }
        countryBtn.setOnKeyListener { _, code, ev ->
            if (ev.action == KeyEvent.ACTION_DOWN && code == KeyEvent.KEYCODE_DPAD_LEFT) {
                selectCountry(countryIndex - 1); true
            } else if (ev.action == KeyEvent.ACTION_DOWN && code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                selectCountry(countryIndex + 1); true
            } else false
        }
        playerView.setOnClickListener {
            if (!fullscreen) enterFullscreen() else showBanner(current?.name)
        }

        loadChannels()
    }

    // ---------- Datos ----------

    private fun loadChannels() {
        lifecycleScope.launch {
            val channels = withContext(Dispatchers.Default) {
                runCatching { M3uParser.parse(File(filesDir, "playlist.m3u").readText()) }
                    .getOrDefault(emptyList())
            }
            if (channels.isEmpty()) {
                Toast.makeText(this@TvActivity, "No hay canales cargados", Toast.LENGTH_LONG).show()
                finish()
                return@launch
            }
            byCountry = channels.groupBy { it.country }
            countries = byCountry.keys.sortedWith(
                compareBy<String>({ when (it) { "México" -> 0; "Otros" -> 2; else -> 1 } }, { it })
            )
            val lastUrl = Prefs.lastUrl(this@TvActivity)
            val start = channels.firstOrNull { it.url == lastUrl }
                ?: byCountry.getValue(countries[0]).first()
            current = start
            selectCountry(countries.indexOf(start.country))
            adapter.setPlaying(start.url)
            showBanner(start.name)
            if (player != null) startMedia(start.url, false)
            channelList.postDelayed({ focusCurrent() }, 300)
        }
    }

    private fun selectCountry(i: Int) {
        if (countries.isEmpty()) return
        countryIndex = ((i % countries.size) + countries.size) % countries.size
        val name = countries[countryIndex]
        val list = byCountry[name].orEmpty()
        countryBtn.text = "‹  $name (${list.size})  ›"
        adapter.submit(list)
        val pos = adapter.positionOf(current?.url).coerceAtLeast(0)
        layoutManager.scrollToPositionWithOffset(pos, 0)
    }

    private fun showCountryDialog() {
        if (countries.isEmpty()) return
        AlertDialog.Builder(this)
            .setTitle("Elige un país")
            .setItems(countries.toTypedArray()) { _, i -> selectCountry(i) }
            .show()
    }

    private fun focusCurrent() {
        channelList.post {
            val pos = adapter.positionOf(current?.url).coerceAtLeast(0)
            channelList.findViewHolderForAdapterPosition(pos)?.itemView?.requestFocus()
        }
    }

    // ---------- Reproducción ----------

    private fun initPlayer() {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent("VLC/3.0.18 LibVLC/3.0.18")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(15000)
        val renderers = DefaultRenderersFactory(this).setEnableDecoderFallback(true)
        val p = ExoPlayer.Builder(this, renderers)
            .setMediaSourceFactory(DefaultMediaSourceFactory(http))
            .build()
        p.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                val ch = current ?: return
                if (!retriedHls && !ch.url.contains(".m3u8")) {
                    retriedHls = true
                    startMedia(ch.url, true)
                } else {
                    message.text = "Este canal no está disponible.\nPrueba con otro."
                    message.visibility = View.VISIBLE
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) message.visibility = View.GONE
            }
        })
        playerView.player = p
        player = p
    }

    private fun releasePlayer() {
        playerView.player = null
        player?.release()
        player = null
    }

    private fun startMedia(url: String, asHls: Boolean) {
        val p = player ?: return
        val item = MediaItem.Builder().setUri(url).apply {
            if (asHls) setMimeType(MimeTypes.APPLICATION_M3U8)
        }.build()
        p.setMediaItem(item)
        p.prepare()
        p.playWhenReady = true
    }

    private fun play(ch: Channel) {
        current = ch
        retriedHls = false
        Prefs.saveLast(this, ch.url)
        adapter.setPlaying(ch.url)
        message.visibility = View.GONE
        showBanner(ch.name)
        startMedia(ch.url, false)
    }

    private fun zap(delta: Int) {
        val cur = current ?: return
        val list = byCountry[cur.country].orEmpty()
        if (list.isEmpty()) return
        val i = list.indexOfFirst { it.url == cur.url }
        val next = list[(((i + delta) % list.size) + list.size) % list.size]
        play(next)
        val pos = adapter.positionOf(next.url)
        if (pos >= 0) layoutManager.scrollToPosition(pos)
    }

    private fun showBanner(text: String?) {
        banner.text = text ?: ""
        banner.visibility = View.VISIBLE
        banner.removeCallbacks(hideBanner)
        banner.postDelayed(hideBanner, 3000)
    }

    // ---------- Pantalla completa ----------

    private fun enterFullscreen() {
        fullscreen = true
        sidebar.visibility = View.GONE
        playerView.requestFocus()
        showBanner(current?.name)
    }

    private fun exitFullscreen() {
        fullscreen = false
        sidebar.visibility = View.VISIBLE
        banner.visibility = View.GONE
        focusCurrent()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (fullscreen) exitFullscreen() else super.onBackPressed()
    }

    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        if (e.action == KeyEvent.ACTION_DOWN) {
            when (e.keyCode) {
                KeyEvent.KEYCODE_CHANNEL_UP -> { zap(1); return true }
                KeyEvent.KEYCODE_CHANNEL_DOWN -> { zap(-1); return true }
                KeyEvent.KEYCODE_DPAD_UP -> if (fullscreen) { zap(-1); return true }
                KeyEvent.KEYCODE_DPAD_DOWN -> if (fullscreen) { zap(1); return true }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER ->
                    if (fullscreen) { showBanner(current?.name); return true }
            }
        }
        return super.dispatchKeyEvent(e)
    }

    @Suppress("DEPRECATION")
    private fun hideSystemUi() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUi()
    }

    // ---------- Ciclo de vida ----------

    override fun onStart() {
        super.onStart()
        initPlayer()
        retriedHls = false
        current?.let { startMedia(it.url, false) }
    }

    override fun onStop() {
        super.onStop()
        releasePlayer()
    }
}
