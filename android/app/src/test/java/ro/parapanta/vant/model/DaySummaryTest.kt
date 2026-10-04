package ro.parapanta.vant.model

import org.junit.Assert.assertEquals
import org.junit.Test

class DaySummaryTest {
    private val liteni = Site("Liteni", 46.6129, 23.435, 895, listOf(0, 1, 2, 2, 1, 0, 0, 0))
    private val hours = listOf("10", "11", "12", "13", "14")
    private fun h(ws: Double, wd: Double) = Hour(ws, wd, ws + 1, 0.0, null, null, null, null, null)

    @Test fun longestGoodWindow() {
        // V (nu), E 2, E 3, V (nu), E 2  -> fereastra bună cea mai lungă e 11–12
        val s = summarize(liteni, hours, listOf(h(2.0, 270.0), h(2.0, 90.0), h(3.0, 95.0), h(2.0, 270.0), h(2.0, 90.0)), Thresholds())
        assertEquals(Status.GO, s.status)
        assertEquals("Favorabil 11–12 (2 ore) · vânt din E 2–3 m/s", s.text)
        assertEquals(listOf(Status.NO, Status.GO, Status.GO, Status.NO, Status.GO), s.statuses)
    }

    // SIG-05: o singură oră bună nu face ziua „Favorabil”.
    @Test fun singleHour() {
        val s = summarize(liteni, hours, listOf(h(2.0, 270.0), h(2.0, 270.0), h(2.0, 135.0), h(2.0, 270.0), null), Thresholds())
        assertEquals(Status.MAYBE, s.status)
        assertEquals("Doar ora 12, sub fereastra minimă de 2 ore", s.text)
    }

    @Test fun marginalOnly() =
        assertEquals("Cel mult marginal: 10–11 · vânt din S 3 m/s",
            summarize(liteni, hours, listOf(h(3.0, 180.0), h(3.0, 180.0), h(2.0, 270.0), h(2.0, 270.0), h(2.0, 270.0)), Thresholds()).text)

    @Test fun calmOnly() =
        assertEquals("Nicio fereastră bună · calm (sub 1 m/s) 12–14",
            summarize(liteni, hours, listOf(h(2.0, 270.0), h(2.0, 270.0), h(0.5, 270.0), h(0.5, 270.0), h(0.5, 270.0)), Thresholds()).text)

    @Test fun nothing() =
        assertEquals(Status.NO, summarize(liteni, hours, List(5) { h(2.0, 270.0) }, Thresholds()).status)
}
