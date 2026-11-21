package com.tvbox.iptv

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private lateinit var urlInput: EditText
    private lateinit var btnLoad: Button
    private lateinit var btnStart: Button
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        urlInput = findViewById(R.id.urlInput)
        btnLoad = findViewById(R.id.btnLoad)
        btnStart = findViewById(R.id.btnStart)
        status = findViewById(R.id.status)

        btnLoad.setOnClickListener { loadList() }
        btnStart.setOnClickListener { startActivity(Intent(this, TvActivity::class.java)) }
        urlInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) { loadList(); true } else false
        }

        val saved = Prefs.url(this)
        urlInput.setText(if (saved.isNotEmpty()) saved else getString(R.string.default_playlist_url))

        if (Prefs.count(this) > 0) showReady()
        else if (urlInput.text.isNotBlank()) loadList()
    }

    private fun showReady() {
        status.text = "Lista lista: ${Prefs.count(this)} canales"
        btnStart.visibility = View.VISIBLE
        btnStart.requestFocus()
    }

    private fun loadList() {
        val url = urlInput.text.toString().trim()
        if (url.isEmpty()) {
            status.text = "Escribe o pega la dirección de la lista"
            return
        }
        status.text = "Descargando lista…"
        btnLoad.isEnabled = false
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val req = Request.Builder().url(url)
                        .header("User-Agent", "VLC/3.0.18 LibVLC/3.0.18").build()
                    val text = client.newCall(req).execute().use { r ->
                        if (!r.isSuccessful) error("HTTP ${r.code}")
                        r.body!!.string()
                    }
                    val n = M3uParser.parse(text).size
                    if (n == 0) error("sin canales")
                    openFileOutput("playlist.m3u", MODE_PRIVATE).use { it.write(text.toByteArray()) }
                    n
                }
            }
            btnLoad.isEnabled = true
            result.onSuccess {
                Prefs.saveList(this@MainActivity, url, it)
                showReady()
            }.onFailure {
                status.text = "No se pudo cargar la lista. Revisa la dirección y la conexión a internet."
            }
        }
    }
}
