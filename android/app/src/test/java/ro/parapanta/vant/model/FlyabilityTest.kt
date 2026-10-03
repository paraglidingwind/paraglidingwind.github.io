package ro.parapanta.vant.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FlyabilityTest {
    private val liteni = Site("Liteni", 46.6129, 23.435, 895, listOf(0, 1, 2, 2, 1, 0, 0, 0))
    private val th = Thresholds()
    private fun h(ws: Double, wd: Double, wg: Double = ws + 1, pr: Double = 0.0) =
        Hour(ws, wd, wg, pr, null, null, null, null, null)

    @Test fun eastLightIsGood() = assertEquals(Status.GO, rate(liteni, h(3.0, 90.0), th).status)
    @Test fun westIsNo() = assertEquals(Status.NO, rate(liteni, h(3.0, 270.0), th).status)
    @Test fun southIsMarginal() = assertEquals(Status.MAYBE, rate(liteni, h(3.0, 180.0), th).status)
    @Test fun eastStrongIsNo() = assertEquals(Status.NO, rate(liteni, h(8.0, 90.0), th).status)
    @Test fun eastFreshIsMarginal() = assertEquals(Status.MAYBE, rate(liteni, h(6.0, 90.0), th).status)
    @Test fun calm() = assertEquals(Status.CALM, rate(liteni, h(0.5, 270.0), th).status)
    @Test fun gustsAreNo() = assertEquals(Status.NO, rate(liteni, h(3.0, 90.0, wg = 10.0), th).status)
    @Test fun gustSpreadIsMarginal() = assertEquals(Status.MAYBE, rate(liteni, h(3.0, 90.0, wg = 7.5), th).status)
    @Test fun rainIsNo() = assertEquals(Status.NO, rate(liteni, h(3.0, 90.0, pr = 1.0), th).status)
    @Test fun missingIsNa() = assertEquals(Status.NA, rate(liteni, null, th).status)

    @Test fun sectors() {
        assertEquals(0, sectorOf(0.0)); assertEquals(0, sectorOf(350.0)); assertEquals(2, sectorOf(100.0))
        assertEquals(7, sectorOf(315.0)); assertEquals(0, sectorOf(337.6))
    }

    @Test fun coords() {
        assertEquals(46.6129 to 23.435, parseCoords("46.6129, 23.4350"))
        assertEquals(46.9301 to 23.5512, parseCoords("https://www.google.com/maps/@46.9301,23.5512,15z"))
        assertEquals(46.1 to 23.2, parseCoords("https://www.google.com/maps/place/X/data=!3d46.1!4d23.2"))
        assertEquals(46.5 to 23.6, parseCoords("https://maps.google.com/?q=46.5,23.6"))
        assertNull(parseCoords("https://maps.app.goo.gl/abcdef"))
        assertNull(parseCoords("Făureni"))
    }
}
