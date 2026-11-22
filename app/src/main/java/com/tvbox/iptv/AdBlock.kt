package com.tvbox.iptv

import android.content.Context

object AdBlock {
    private var hosts: Set<String> = emptySet()

    fun load(c: Context) {
        if (hosts.isNotEmpty()) return
        hosts = runCatching {
            c.assets.open("adhosts.txt").bufferedReader().readLines()
                .map { it.trim().lowercase() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .toHashSet()
        }.getOrDefault(emptySet())
    }

    fun rootOf(h: String): String = h.split('.').takeLast(2).joinToString(".")

    fun isBlocked(host: String?, pageHost: String?): Boolean {
        if (host == null) return false
        val h = host.lowercase()
        if (pageHost != null && rootOf(h) == rootOf(pageHost.lowercase())) return false
        var cur = h
        while (true) {
            if (cur in hosts) return true
            val i = cur.indexOf('.')
            if (i < 0) return false
            cur = cur.substring(i + 1)
        }
    }
}
