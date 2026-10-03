package ro.parapanta.vant.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SkyTest {
    private fun h(cc: Double, lo: Double = 0.0, mi: Double = 0.0, hi: Double = 0.0, cape: Double = 0.0, code: Int = 0, day: Int = 1) =
        Hour(3.0, 90.0, 4.0, 0.0, cc, cape, 15.0, null, null, code, lo, mi, hi, day)

    @Test fun clearDay() = assertEquals(Sky("sun", "Senin", null), sky(h(5.0)))
    @Test fun clearNight() = assertEquals("moon", sky(h(5.0, day = 0)).icon)
    @Test fun cumulus() = assertEquals(Sky("cumulus", "Parțial noros", "Cumulus"), sky(h(40.0, lo = 40.0, cape = 400.0)))
    @Test fun congestus() = assertEquals("Cumulus congestus", sky(h(60.0, lo = 60.0, cape = 1500.0)).type)
    @Test fun stratus() = assertEquals(Sky("overcast", "Înnorat", "Stratus"), sky(h(95.0, lo = 95.0)))
    @Test fun cirrus() = assertEquals(Sky("cirrus", "Parțial noros", "Cirrus"), sky(h(30.0, hi = 30.0)))
    @Test fun altocumulus() = assertEquals("Altocumulus", sky(h(60.0, mi = 60.0)).type)
    @Test fun thunder() = assertEquals(Sky("thunder", "Furtună", "Cumulonimbus"), sky(h(90.0, lo = 80.0, code = 95)))
    @Test fun showers() = assertEquals("rain", sky(h(70.0, lo = 70.0, code = 80)).icon)
    @Test fun snow() = assertEquals("snow", sky(h(90.0, code = 73)).icon)
    @Test fun fog() = assertEquals("fog", sky(h(100.0, lo = 100.0, code = 45)).icon)
}
