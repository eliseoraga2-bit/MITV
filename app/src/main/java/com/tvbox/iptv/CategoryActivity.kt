package com.tvbox.iptv

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

class CategoryActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val cat = intent.getStringExtra("cat") ?: "tv"
        val sources = Prefs.sources(this).filter { cat in it.cats }

        if (sources.size == 1) {
            Ui.openSource(this, sources[0])
            finish()
            return
        }

        setContentView(R.layout.activity_category)
        findViewById<TextView>(R.id.title).text = Cats.byKey(cat).label

        val grid = findViewById<RecyclerView>(R.id.grid)
        grid.layoutManager = GridLayoutManager(this, 3)
        val adapter = TileAdapter { t ->
            sources.firstOrNull { it.id == t.key }?.let { Ui.openSource(this, it) }
        }
        grid.adapter = adapter
        adapter.submit(sources.map {
            Tile(
                it.id, it.name,
                if (it.isList) "Lista IPTV" else "Página web",
                if (it.isList) 0xFF1E40AF.toInt() else 0xFF334155.toInt()
            )
        })
        grid.postDelayed({ grid.layoutManager?.findViewByPosition(0)?.requestFocus() }, 250)
    }
}
