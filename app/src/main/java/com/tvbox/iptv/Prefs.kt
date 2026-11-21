package com.tvbox.iptv

import android.content.Context

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("iptv", Context.MODE_PRIVATE)

    fun url(c: Context): String = sp(c).getString("url", "") ?: ""
    fun count(c: Context): Int = sp(c).getInt("count", 0)
    fun saveList(c: Context, url: String, count: Int) {
        sp(c).edit().putString("url", url).putInt("count", count).apply()
    }

    fun lastUrl(c: Context): String = sp(c).getString("last", "") ?: ""
    fun saveLast(c: Context, url: String) {
        sp(c).edit().putString("last", url).apply()
    }
}
