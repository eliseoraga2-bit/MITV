package com.tvbox.iptv

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class Tile(val key: String, val title: String, val subtitle: String, val color: Int)

class TileAdapter(private val onClick: (Tile) -> Unit) :
    RecyclerView.Adapter<TileAdapter.VH>() {

    private var items: List<Tile> = emptyList()

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val box: View = v.findViewById(R.id.box)
        val title: TextView = v.findViewById(R.id.title)
        val subtitle: TextView = v.findViewById(R.id.subtitle)
    }

    fun submit(list: List<Tile>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_tile, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, position: Int) {
        val t = items[position]
        h.title.text = t.title
        h.subtitle.text = t.subtitle
        h.subtitle.visibility = if (t.subtitle.isEmpty()) View.GONE else View.VISIBLE
        val bg = GradientDrawable()
        bg.setColor(t.color)
        bg.cornerRadius = 16f * h.itemView.resources.displayMetrics.density
        h.box.background = bg
        h.itemView.setOnClickListener { onClick(t) }
    }
}
