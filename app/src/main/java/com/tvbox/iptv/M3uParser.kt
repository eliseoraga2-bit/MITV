package com.tvbox.iptv

object M3uParser {
    private val attrRegex = Regex("""([\w-]+)="([^"]*)"""")

    private val countryNames = mapOf(
        "MX" to "México", "US" to "Estados Unidos", "ES" to "España", "AR" to "Argentina",
        "CO" to "Colombia", "CL" to "Chile", "PE" to "Perú", "VE" to "Venezuela",
        "EC" to "Ecuador", "UY" to "Uruguay", "PY" to "Paraguay", "BO" to "Bolivia",
        "CR" to "Costa Rica", "PA" to "Panamá", "GT" to "Guatemala", "HN" to "Honduras",
        "SV" to "El Salvador", "NI" to "Nicaragua", "DO" to "República Dominicana",
        "CU" to "Cuba", "PR" to "Puerto Rico", "BR" to "Brasil", "PT" to "Portugal",
        "FR" to "Francia", "IT" to "Italia", "DE" to "Alemania", "GB" to "Reino Unido",
        "UK" to "Reino Unido", "CA" to "Canadá"
    )

    fun parse(text: String): List<Channel> {
        val out = ArrayList<Channel>()
        var info: String? = null
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            when {
                line.startsWith("#EXTINF") -> info = line
                line.isEmpty() || line.startsWith("#") -> {}
                else -> {
                    val i = info
                    if (i != null) {
                        val attrs = attrRegex.findAll(i).associate {
                            it.groupValues[1].lowercase() to it.groupValues[2]
                        }
                        val q = i.lastIndexOf('"')
                        val comma = i.indexOf(',', if (q >= 0) q else 0)
                        var name = if (comma >= 0) i.substring(comma + 1).trim() else ""
                        if (name.isEmpty()) name = attrs["tvg-name"] ?: "Canal"
                        out += Channel(
                            name = name,
                            url = line,
                            logo = attrs["tvg-logo"]?.takeIf { it.startsWith("http") },
                            country = countryOf(attrs),
                            tvgId = attrs["tvg-id"]
                        )
                        info = null
                    }
                }
            }
        }
        return out
    }

    private fun normalize(s: String): String =
        if (s.length == 2) countryNames[s.uppercase()] ?: s.uppercase() else s

    private fun countryOf(a: Map<String, String>): String {
        a["tvg-country"]?.split(';', ',')?.firstOrNull()?.trim()
            ?.takeIf { it.isNotEmpty() }?.let { return normalize(it) }

        a["tvg-id"]?.substringAfterLast('.', "")?.substringBefore('@')
            ?.takeIf { it.length == 2 }
            ?.let { code -> countryNames[code.uppercase()]?.let { return it } }

        a["group-title"]?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        return "Otros"
    }
}
