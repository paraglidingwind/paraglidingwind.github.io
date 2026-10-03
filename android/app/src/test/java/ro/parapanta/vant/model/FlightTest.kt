package ro.parapanta.vant.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FlightTest {
    private val fl = listOf(
        Flight("a1", "2026-10-03", "Liteni", 95), Flight("a2", "2026-09-27", "Rimetea E", 40),
        Flight("a3", "2026-09-14", "Liteni", 180), Flight("a5", "2025-07-12", "Bunloc", 120),
    )

    @Test fun durations() {
        assertEquals("1 h 35 min", fmtDur(95)); assertEquals("3 h", fmtDur(180)); assertEquals("40 min", fmtDur(40))
    }

    @Test fun months() {
        val g = byMonth(fl)
        assertEquals(listOf("2026-10", "2026-09", "2025-07"), g.map { it.key })
        assertEquals(220, g[1].minutes)
        assertEquals(listOf("a2", "a3"), g[1].flights.map { it.id })
        assertEquals(listOf("a1", "a3"), byMonth(fl, "Liteni").flatMap { it.flights }.map { it.id })
    }

    @Test fun sites() {
        val s = bySite(fl)
        assertEquals(SiteTotal("Liteni", 2, 275, "2026-10-03"), s[0])
        assertEquals(listOf("Liteni", "Bunloc", "Rimetea E"), s.map { it.site })
    }

    @Test fun validation() {
        val today = "2026-10-03"
        assertEquals("Alege sau scrie locația.", validateFlight(" ", today, 1, 0, today))
        assertEquals("Data nu poate fi în viitor.", validateFlight("Liteni", "2099-01-01", 1, 0, today))
        assertEquals("Scrie cât timp ai stat în aer.", validateFlight("Liteni", today, 0, 0, today))
        assertEquals("Minutele trebuie să fie între 0 și 59.", validateFlight("Liteni", today, 1, 75, today))
        assertNull(validateFlight("Liteni", "2025-07-12", 1, 30, today))
    }
}
