package ro.parapanta.vant.model

data class Sky(val icon: String, val label: String, val type: String?)

val WMO = mapOf(
    0 to "Senin", 1 to "Predominant senin", 2 to "Parțial noros", 3 to "Înnorat", 45 to "Ceață", 48 to "Ceață cu chiciură",
    51 to "Burniță slabă", 53 to "Burniță", 55 to "Burniță densă", 56 to "Burniță înghețată", 57 to "Burniță înghețată",
    61 to "Ploaie slabă", 63 to "Ploaie", 65 to "Ploaie puternică", 66 to "Ploaie înghețată", 67 to "Ploaie înghețată",
    71 to "Ninsoare slabă", 73 to "Ninsoare", 75 to "Ninsoare puternică", 77 to "Grăunțe de zăpadă",
    80 to "Averse slabe", 81 to "Averse", 82 to "Averse violente", 85 to "Averse de ninsoare", 86 to "Averse de ninsoare",
    95 to "Furtună", 96 to "Furtună cu grindină", 99 to "Furtună cu grindină",
)

val CLOUD_NOTES = mapOf(
    "Cumulus" to "Nori de convecție: semn de termice.",
    "Cumulus congestus" to "Dezvoltare verticală puternică: risc de supradezvoltare și averse.",
    "Cumulonimbus" to "Nor de furtună: nu se zboară.",
    "Stratocumulus" to "Strat de nori joși: termice slabe.",
    "Stratus" to "Plafon jos și uniform.",
    "Altocumulus" to "Nori la altitudine medie.",
    "Altostratus" to "Strat la altitudine medie: umbrește solul, termice slabe.",
    "Cirrus" to "Nori înalți subțiri: adesea anunță un front.",
    "Cirrostratus" to "Văl înalt: slăbește termicele.",
)

/** Aceeași clasificare ca sky() din pagina web. */
fun sky(w: Hour): Sky {
    val code = w.code ?: -1
    val cc = w.cc ?: 0.0
    val lo = w.lo ?: 0.0
    val mi = w.mi ?: 0.0
    val hi = w.hi ?: 0.0
    val cape = w.cape ?: 0.0
    val day = w.day != 0
    var type: String? = null
    val top = maxOf(lo, mi, hi)
    if (top >= 20) {
        type = when (top) {
            lo -> if (day && cape >= 200) (if (cape >= 1000) "Cumulus congestus" else "Cumulus") else (if (lo >= 80) "Stratus" else "Stratocumulus")
            mi -> if (mi >= 80) "Altostratus" else "Altocumulus"
            else -> if (hi >= 80) "Cirrostratus" else "Cirrus"
        }
    }
    val icon: String
    val label: String
    when {
        code >= 95 -> { icon = "thunder"; label = WMO[code] ?: "Furtună"; type = "Cumulonimbus" }
        code in listOf(71, 73, 75, 77, 85, 86) -> { icon = "snow"; label = WMO[code] ?: "Ninsoare" }
        code in 51..82 -> { icon = "rain"; label = WMO[code] ?: "Ploaie" }
        code == 45 || code == 48 -> { icon = "fog"; label = WMO[code] ?: "Ceață" }
        else -> {
            var i: String
            when {
                cc < 15 -> { i = if (day) "sun" else "moon"; label = "Senin" }
                cc < 50 -> { i = if (day) "partly" else "cloud"; label = "Parțial noros" }
                cc < 85 -> { i = "cloud"; label = "Mai mult noros" }
                else -> { i = "overcast"; label = "Înnorat" }
            }
            if (day && type?.startsWith("Cumulus") == true && cc < 85) i = "cumulus"
            else if (day && (type == "Cirrus" || type == "Cirrostratus") && lo < 20 && mi < 20) i = "cirrus"
            icon = i
        }
    }
    return Sky(icon, label, type)
}
