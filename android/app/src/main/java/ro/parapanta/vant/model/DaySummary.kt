package ro.parapanta.vant.model

import kotlin.math.roundToInt

data class DaySummary(val statuses: List<Status>, val status: Status, val text: String)

/** Rezumatul unei zile. Identic cu summarize() din src/core.js: „Favorabil” doar pentru minimum [MIN_WINDOW] ore bune la rând;
 *  [extra] = orele de după interval, ca o furtună care vine să conteze. */
fun summarize(site: Site, hours: List<String>, data: List<Hour?>, th: Thresholds, extra: List<Hour?> = emptyList()): DaySummary {
    val all = data + extra
    val rs = data.indices.map { i -> rate(site, data[i], th, listOf(all.getOrNull(i + 1), all.getOrNull(i + 2))) }
    val sts = rs.map { it.status }
    fun runs(t: Status): List<IntRange> {
        val out = mutableListOf<IntRange>()
        var a = -1
        sts.forEachIndexed { i, s ->
            if (s == t) { if (a < 0) a = i } else if (a >= 0) { out += a until i; a = -1 }
        }
        if (a >= 0) out += a until sts.size
        return out
    }
    fun longest(l: List<IntRange>): IntRange? = l.fold(null as IntRange?) { b, r -> if (b == null || r.last - r.first > b.last - b.first) r else b }
    fun span(r: IntRange) = if (r.first == r.last) "ora ${hours[r.first]}" else "${hours[r.first]}–${hours[r.last]}"
    fun n(r: IntRange) = r.last - r.first + 1
    fun wind(r: IntRange): String {
        val part = r.mapNotNull { data[it] }.filter { it.ws != null && it.wd != null }
        val counts = LinkedHashMap<String, Int>()
        part.forEach { val k = DIRS[sectorOf(it.wd!!)]; counts[k] = (counts[k] ?: 0) + 1 }
        var dir = "–"
        var mx = 0
        for ((k, v) in counts) if (v > mx) { dir = k; mx = v }
        val lo = part.minOf { it.ws!! }.roundToInt()
        val hi = part.maxOf { it.ws!! }.roundToInt()
        return "vânt din $dir ${if (lo == hi) "$lo" else "$lo–$hi"} m/s"
    }
    val goRuns = runs(Status.GO)
    val g = longest(goRuns.filter { n(it) >= MIN_WINDOW })
    val m = longest(runs(Status.MAYBE))
    val c = longest(runs(Status.CALM))
    val si = rs.indexOfFirst { "storm" in it.warn }
    val tail = if (si >= 0) " · risc de supradezvoltare de la ${hours[si]}:00" else ""
    if (g != null) return DaySummary(sts, Status.GO, "Favorabil ${span(g)} (${n(g)} ${if (n(g) == 1) "oră" else "ore"}) · ${wind(g)}$tail")
    if (goRuns.isNotEmpty()) return DaySummary(sts, Status.MAYBE,
        "Doar ${goRuns.joinToString(", ") { span(it) }}, sub fereastra minimă de $MIN_WINDOW ore" + (m?.let { " · marginal ${span(it)}" } ?: "") + tail)
    if (m != null) return DaySummary(sts, Status.MAYBE, "Cel mult marginal: ${span(m)} · ${wind(m)}$tail")
    if (sts.all { it == Status.NA || it == Status.NIGHT }) return DaySummary(sts, Status.NA, "Fără date")
    if (c != null) return DaySummary(sts, Status.CALM, "Nicio fereastră bună · calm (sub ${th.calm.trim()} m/s) ${span(c)}$tail")
    return DaySummary(sts, Status.NO, "Nicio fereastră bună$tail")
}
