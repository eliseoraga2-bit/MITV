package com.tvbox.iptv

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load

class ChannelAdapter(private val onClick: (Channel) -> Unit) :
    RecyclerView.Adapter<ChannelAdapter.VH>() {

    private var items: List<Channel> = emptyList()
    private var playingUrl: String? = null

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val logo: ImageView = v.findViewById(R.id.logo)
        val name: TextView = v.findViewById(R.id.name)
    }

    fun submit(list: List<Channel>) {
        items = list
        notifyDataSetChanged()
    }

    fun positionOf(url: String?): Int = items.indexOfFirst { it.url == url }

    fun setPlaying(url: String?) {
        val old = positionOf(playingUrl)
        playingUrl = url
        val new = positionOf(url)
        if (old >= 0) notifyItemChanged(old)
        if (new >= 0) notifyItemChanged(new)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_channel, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, position: Int) {
        val ch = items[position]
        h.name.text = ch.name
        h.logo.load(ch.logo) {
            fallback(R.drawable.ic_launcher)
            error(R.drawable.ic_launcher)
        }
        h.itemView.isSelected = ch.url == playingUrl
        h.itemView.setOnClickListener { onClick(ch) }
    }
}
