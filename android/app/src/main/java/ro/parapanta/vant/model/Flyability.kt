package ro.parapanta.vant.model

import kotlin.math.roundToInt

enum class Status(val rank: Int, val label: String) {
    NA(-1, "Fără date"), GO(0, "Bun de zbor"), CALM(1, "Calm (fără vânt)"), MAYBE(2, "Marginal"), NO(3, "Nu e de zbor")
}

data class Rating(val status: Status, val reasons: List<String>)

val DIRS = listOf("N", "NE", "E", "SE", "S", "SV", "V", "NV")

fun sectorOf(deg: Double): Int = Math.round((((deg % 360) + 360) % 360) / 45.0).toInt() % 8

/** Aceleași reguli ca rate() din pagina web (src/index.html). Starea finală = cea mai rea componentă. */
fun rate(site: Site, w: Hour?, s: Thresholds): Rating {
    val ws = w?.ws
    val wd = w?.wd
    if (w == null || ws == null || wd == null) return Rating(Status.NA, emptyList())
    val why = mutableListOf<String>()
    var st = Status.GO
    fun bump(to: Status, msg: String) {
        why += msg
        if (to.rank > st.rank) st = to
    }
    if (ws < s.calm) {
        st = Status.CALM
        why += "Vânt slab (${ws.f1()} m/s), direcția nu contează"
    } else {
        val sec = sectorOf(wd)
        when (site.o.getOrElse(sec) { 0 }) {
            2 -> why += "Direcție bună (${DIRS[sec]})"
            1 -> bump(Status.MAYBE, "Direcție marginală (${DIRS[sec]})")
            else -> bump(Status.NO, "Direcție nepotrivită (${DIRS[sec]}) pentru decolare")
        }
        if (ws > s.marg) bump(Status.NO, "Vânt prea tare: ${ws.f1()} m/s (peste ${s.marg.trim()})")
        else if (ws > s.good) bump(Status.MAYBE, "Vânt tare: ${ws.f1()} m/s (peste ${s.good.trim()})")
    }
    val wg = w.wg
    if (wg != null) {
        if (wg > s.gust) bump(Status.NO, "Rafale ${wg.f1()} m/s (peste ${s.gust.trim()})")
        else if (wg - ws > s.spread) bump(Status.MAYBE, "Turbulent: rafalele depășesc vântul cu ${(wg - ws).f1()} m/s")
    }
    val pr = w.pr
    if (pr != null && pr > s.rain) bump(Status.NO, "Ploaie ${pr.f1()} mm/h")
    return Rating(st, why)
}

/** Cea mai bună stare din intervalul de ore al unei zile. */
fun bestOf(statuses: List<Status>): Status =
    statuses.filter { it != Status.NA }.minByOrNull { it.rank } ?: Status.NA

fun Double.trim(): String = if (this == roundToInt().toDouble()) roundToInt().toString() else f1()

/** Extrage coordonatele din text liber sau dintr-un link Google Maps. */
fun parseCoords(input: String): Pair<Double, Double>? {
    val s = try { java.net.URLDecoder.decode(input, "UTF-8") } catch (e: Exception) { input }
    val patterns = listOf(
        Regex("""!3d(-?\d+\.\d+)!4d(-?\d+\.\d+)"""),
        Regex("""@(-?\d+\.\d+),(-?\d+\.\d+)"""),
        Regex("""[?&](?:q|query|ll|center)=(-?\d+\.\d+),\s*(-?\d+\.\d+)"""),
        Regex("""(-?\d{1,2}\.\d+)\s*[,;\s]\s*(-?\d{1,3}\.\d+)"""),
    )
    for (p in patterns) {
        val m = p.find(s) ?: continue
        val lat = m.groupValues[1].toDouble()
        val lon = m.groupValues[2].toDouble()
        return if (kotlin.math.abs(lat) <= 90 && kotlin.math.abs(lon) <= 180) lat to lon else null
    }
    return null
}
