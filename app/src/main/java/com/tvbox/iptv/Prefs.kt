package com.tvbox.iptv

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Source(
    val id: String,
    val name: String,
    val type: String,
    val url: String,
    val cats: List<String>
) {
    val isList: Boolean get() = type == TYPE_LIST

    companion object {
        const val TYPE_LIST = "list"
        const val TYPE_WEB = "web"
    }
}

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("iptv", Context.MODE_PRIVATE)

    fun sources(c: Context): List<Source> {
        val raw = sp(c).getString("sources", null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val cats = o.getJSONArray("cats")
                Source(
                    o.getString("id"), o.getString("name"), o.getString("type"), o.getString("url"),
                    (0 until cats.length()).map { cats.getString(it) }
                )
            }
        }.getOrDefault(emptyList())
    }

    fun saveSources(c: Context, list: List<Source>) {
        val arr = JSONArray()
        list.forEach { s ->
            arr.put(
                JSONObject().put("id", s.id).put("name", s.name).put("type", s.type)
                    .put("url", s.url).put("cats", JSONArray(s.cats))
            )
        }
        sp(c).edit().putString("sources", arr.toString()).apply()
    }

    fun listFile(c: Context, id: String): File = File(c.filesDir, "list_$id.m3u")

    fun pin(c: Context): String = sp(c).getString("pin", "0000") ?: ""
    fun setPin(c: Context, pin: String) {
        sp(c).edit().putString("pin", pin).apply()
    }

    fun lastUrl(c: Context, sourceId: String): String = sp(c).getString("last_$sourceId", "") ?: ""
    fun saveLast(c: Context, sourceId: String, url: String) {
        sp(c).edit().putString("last_$sourceId", url).apply()
    }

    // Si venias de la version 1 (una sola lista), la conserva como fuente de TV.
    fun migrate(c: Context) {
        val p = sp(c)
        if (p.contains("sources")) return
        val old = File(c.filesDir, "playlist.m3u")
        val oldUrl = p.getString("url", "") ?: ""
        if (old.exists() && oldUrl.isNotEmpty()) {
            val s = Source("legacy", "Mi lista", Source.TYPE_LIST, oldUrl, listOf("tv"))
            old.renameTo(listFile(c, s.id))
            saveSources(c, listOf(s))
            p.edit().putString("last_legacy", p.getString("last", "") ?: "").apply()
        }
    }
}
