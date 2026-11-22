package com.tvbox.iptv

data class Cat(val key: String, val label: String, val color: Int)

object Cats {
    val all = listOf(
        Cat("tv", "TV", 0xFF2F6FED.toInt()),
        Cat("peliculas", "Películas", 0xFFC2410C.toInt()),
        Cat("series", "Series", 0xFF7C3AED.toInt()),
        Cat("anime", "Anime", 0xFFDB2777.toInt()),
        Cat("infantil", "Infantil", 0xFF059669.toInt()),
        Cat("youtube", "YouTube", 0xFFDC2626.toInt())
    )

    fun byKey(k: String): Cat = all.firstOrNull { it.key == k } ?: all[0]
}
