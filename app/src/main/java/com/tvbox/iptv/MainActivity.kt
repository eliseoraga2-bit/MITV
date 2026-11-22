package com.tvbox.iptv

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    private lateinit var grid: RecyclerView
    private lateinit var empty: TextView
    private lateinit var adapter: TileAdapter
    private var focused = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Prefs.migrate(this)

        grid = findViewById(R.id.grid)
        empty = findViewById(R.id.empty)
        grid.layoutManager = GridLayoutManager(this, 3)
        adapter = TileAdapter { t ->
            if (t.key == "youtube") Ui.openYouTube(this)
            else startActivity(Intent(this, CategoryActivity::class.java).putExtra("cat", t.key))
        }
        grid.adapter = adapter

        findViewById<TextView>(R.id.btnSettings).setOnClickListener {
            Ui.askPin(this) { startActivity(Intent(this, SettingsActivity::class.java)) }
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val sources = Prefs.sources(this)
        val tiles = Cats.all
            .filter { c -> c.key == "youtube" || sources.any { c.key in it.cats } }
            .map { c ->
                val n = sources.count { c.key in it.cats }
                val sub = if (c.key == "youtube") "" else if (n == 1) "1 fuente" else "$n fuentes"
                Tile(c.key, c.label, sub, c.color)
            }
        adapter.submit(tiles)
        empty.visibility = if (sources.isEmpty()) View.VISIBLE else View.GONE
        if (!focused) {
            focused = true
            grid.postDelayed({ grid.layoutManager?.findViewByPosition(0)?.requestFocus() }, 250)
        }
    }
}
