package ro.parapanta.vant.model

import kotlin.math.roundToInt

enum class Status(val rank: Int, val label: String) {
    NIGHT(-2, "Noapte"), NA(-1, "Fără date"), GO(0, "Favorabil"), CALM(1, "Calm (fără vânt)"), MAYBE(2, "Marginal"), NO(3, "Nu e de zbor")
}

/** [warn]: "alt" (vânt la altitudine), "storm" (supradezvoltare / furtună), "nodir" (decolare fără direcții). [warnings] = motivele care sunt avertizări. */
data class Rating(val status: Status, val reasons: List<String>, val warn: List<String> = emptyList(), val warnings: Set<String> = emptySet())

val DIRS = listOf("N", "NE", "E", "SE", "S", "SV", "V", "NV")

/** Pragurile avertizărilor (SIG-01, SIG-02) și fereastra minimă (SIG-05). Aceleași ca în src/core.js. */
const val ALT_MARG = 8.0
const val ALT_NO = 12.0
const val ALT_BEHIND = 5.0
const val CAPE_RISK = 800.0
const val LI_RISK = -2.0
const val STORM_AHEAD = 2
const val MIN_WINDOW = 2

fun sectorOf(deg: Double): Int = Math.round((((deg % 360) + 360) % 360) / 45.0).toInt() % 8

fun Site.knownDirs(): Boolean = o.any { it > 0 }

/** Aceleași reguli ca rate() din src/core.js. Starea finală = cea mai rea componentă. [next] = următoarele ore (furtuni). */
fun rate(site: Site, w: Hour?, s: Thresholds, next: List<Hour?> = emptyList()): Rating {
    val ws = w?.ws
    val wd = w?.wd
    if (w == null || ws == null || wd == null) return Rating(Status.NA, emptyList())
    if (w.day == 0) return Rating(Status.NIGHT, listOf("Noapte: fără lumină de zbor"))
    val why = mutableListOf<String>()
    val warn = mutableListOf<String>()
    val warnings = mutableSetOf<String>()
    var st = Status.GO
    fun bump(to: Status, msg: String, kind: String? = null) {
        why += msg
        if (kind != null) { warnings += msg; if (kind !in warn) warn += kind }
        if (to.rank > st.rank) st = to
    }
    val known = site.knownDirs()
    if (ws < s.calm) {
        st = Status.CALM
        why += "Vânt slab (${ws.f1()} m/s), direcția nu contează"
    } else {
        val sec = sectorOf(wd)
        when {
            !known -> bump(Status.MAYBE, "Decolarea nu are direcții setate: verifică dacă vântul din ${DIRS[sec]} e bun aici", "nodir")
            site.o.getOrElse(sec) { 0 } == 2 -> why += "Direcție bună (${DIRS[sec]})"
            site.o.getOrElse(sec) { 0 } == 1 -> bump(Status.MAYBE, "Direcție marginală (${DIRS[sec]})")
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
    // SIG-01: vântul de la ~1500 m. Tare, sau din spatele decolării (zonă de sub vânt, rotori).
    val w8 = w.w8
    val d8 = w.d8
    if (w8 != null && d8 != null) {
        val s8 = sectorOf(d8)
        if (w8 > ALT_NO) bump(Status.NO, "Vânt foarte tare la ~1500 m: ${w8.f1()} m/s (peste ${ALT_NO.trim()})", "alt")
        else if (w8 > ALT_MARG) bump(Status.MAYBE, "Vânt tare la ~1500 m: ${w8.f1()} m/s (peste ${ALT_MARG.trim()})", "alt")
        if (known && site.o.getOrElse(s8) { 0 } == 0 && w8 > ALT_BEHIND)
            bump(Status.MAYBE, "Vânt la ~1500 m din spatele decolării (${DIRS[s8]}, ${w8.f1()} m/s): risc de rotori", "alt")
    }
    // SIG-02: furtună acum sau în următoarele ore; altfel instabilitate mare (supradezvoltare).
    val storm = (listOf(w) + next).take(STORM_AHEAD + 1).indexOfFirst { (it?.code ?: -1) >= 95 }
    val cape = w.cape
    val li = w.li
    when {
        storm == 0 -> bump(Status.NO, "Furtună prognozată la această oră", "storm")
        storm == 1 -> bump(Status.NO, "Furtună prognozată în ora următoare", "storm")
        storm > 1 -> bump(Status.NO, "Furtună prognozată peste $storm ore", "storm")
        cape != null && li != null && cape > CAPE_RISK && li < LI_RISK ->
            bump(Status.MAYBE, "Risc de supradezvoltare: CAPE ${cape.roundToInt()} J/kg, LI ${(Math.round(li * 10) / 10.0).trim().replace('-', '−')}", "storm")
    }
    return Rating(st, why, warn, warnings)
}

/** Cea mai bună stare din intervalul de ore al unei zile. */
fun bestOf(statuses: List<Status>): Status =
    statuses.filter { it != Status.NA && it != Status.NIGHT }.minByOrNull { it.rank } ?: Status.NA

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
