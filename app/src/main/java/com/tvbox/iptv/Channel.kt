package com.tvbox.iptv

data class Channel(
    val name: String,
    val url: String,
    val logo: String?,
    val country: String,
    val tvgId: String?
)
