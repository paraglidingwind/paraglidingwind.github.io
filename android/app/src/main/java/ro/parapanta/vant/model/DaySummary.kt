package ro.parapanta.vant.model

import kotlin.math.roundToInt

data class DaySummary(val statuses: List<Status>, val status: Status, val text: String)

/** Rezumatul unei zile: starea pe fiecare oră și cel mai lung interval bun (sau marginal). Identic cu daySummary() de pe web. */
fun summarize(site: Site, hours: List<String>, data: List<Hour?>, th: Thresholds): DaySummary {
    val sts = data.map { rate(site, it, th).status }
    fun run(t: Status): IntRange? {
        var best: IntRange? = null
        var start = -1
        sts.forEachIndexed { i, s ->
            if (s == t) {
                if (start < 0) start = i
                val b = best
                if (b == null || i - start > b.last - b.first) best = start..i
            } else start = -1
        }
        return best
    }
    fun span(r: IntRange) = if (r.first == r.last) "ora ${hours[r.first]}" else "${hours[r.first]}–${hours[r.last]}"
    fun n(r: IntRange) = r.last - r.first + 1
    fun wind(r: IntRange): String {
        val part = r.mapNotNull { data[it] }.filter { it.ws != null && it.wd != null }
        val dir = part.groupingBy { DIRS[sectorOf(it.wd!!)] }.eachCount().maxByOrNull { it.value }?.key ?: "–"
        val lo = part.minOf { it.ws!! }.roundToInt()
        val hi = part.maxOf { it.ws!! }.roundToInt()
        return "vânt din $dir ${if (lo == hi) "$lo" else "$lo–$hi"} m/s"
    }
    run(Status.GO)?.let { g ->
        return DaySummary(sts, Status.GO, "Bun de zbor ${span(g)} (${n(g)} ${if (n(g) == 1) "oră" else "ore"}) · ${wind(g)}")
    }
    run(Status.MAYBE)?.let { m -> return DaySummary(sts, Status.MAYBE, "Cel mult marginal: ${span(m)} · ${wind(m)}") }
    if (sts.all { it == Status.NA }) return DaySummary(sts, Status.NA, "Fără date")
    run(Status.CALM)?.let { c -> return DaySummary(sts, Status.CALM, "Nicio oră bună · calm (sub ${th.calm.trim()} m/s) ${span(c)}") }
    return DaySummary(sts, Status.NO, "Nicio oră bună de zbor")
}
