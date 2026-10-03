package ro.parapanta.vant.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HolfuyTest {
    private val stations = listOf(
        HolfuyStation(243, "Rimetea", 46.45452, 23.53722, 1200),
        HolfuyStation(774, "Liteni", 46.61272, 23.43508, 920),
    )
    private fun site(lat: Double, lon: Double, hf: Int = -1) = Site("x", lat, lon, null, List(8) { 0 }, hf)

    @Test fun nearestWithin5km() = assertEquals(774, holfuyFor(site(46.6129, 23.435), stations)?.station?.id)
    @Test fun rimeteaWest() = assertEquals(243, holfuyFor(site(46.4532, 23.5869), stations)?.station?.id)
    @Test fun agrisTooFar() = assertNull(holfuyFor(site(46.5971, 23.5455), stations))
    @Test fun explicitNone() = assertNull(holfuyFor(site(46.6129, 23.435, hf = 0), stations))
    @Test fun explicitUnknown() = assertEquals(999, holfuyFor(site(46.0, 23.0, hf = 999), stations)?.station?.id)
    @Test fun parseId() {
        assertEquals(774, parseHolfuyId("774"))
        assertEquals(774, parseHolfuyId("https://holfuy.com/en/weather/774"))
        assertEquals(774, parseHolfuyId("holfuy.com/en/weather/774?x=1"))
        assertNull(parseHolfuyId("Liteni"))
    }
}
