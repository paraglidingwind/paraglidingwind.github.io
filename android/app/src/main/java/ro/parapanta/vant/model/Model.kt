package ro.parapanta.vant.model

import kotlinx.serialization.Serializable
import java.util.Locale

/** Un sit de decolare. [o] = scorul pe cele 8 direcții (N, NE, E, SE, S, SV, V, NV): 0 nu, 1 marginal, 2 bun. */
@Serializable
data class Site(val n: String, val lat: Double, val lon: Double, val alt: Int? = null, val o: List<Int>, val hf: Int = -1)

val Site.key: String get() = String.format(Locale.US, "%.4f,%.4f", lat, lon)

@Serializable
data class Thresholds(
    val good: Double = 5.0,
    val marg: Double = 7.0,
    val calm: Double = 1.0,
    val gust: Double = 9.0,
    val spread: Double = 4.0,
    val rain: Double = 0.2,
    val from: Int = 8,
    val to: Int = 20,
)

/** Prognoza pe o oră pentru un sit. */
data class Hour(
    val ws: Double?, val wd: Double?, val wg: Double?, val pr: Double?,
    val cc: Double?, val cape: Double?, val tt: Double?, val w8: Double?, val d8: Double?,
    val code: Int? = null, val lo: Double? = null, val mi: Double? = null, val hi: Double? = null, val day: Int? = null,
)

@Serializable
data class SiteForecast(
    val t: List<String>,
    val ws: List<Double?>, val wd: List<Double?>, val wg: List<Double?>, val pr: List<Double?>,
    val cc: List<Double?>, val cape: List<Double?>, val tt: List<Double?>, val w8: List<Double?>, val d8: List<Double?>,
    val code: List<Int?> = emptyList(), val lo: List<Double?> = emptyList(), val mi: List<Double?> = emptyList(),
    val hi: List<Double?> = emptyList(), val day: List<Int?> = emptyList(),
) {
    fun at(time: String): Hour? {
        val i = t.indexOf(time)
        if (i < 0) return null
        return Hour(ws[i], wd[i], wg[i], pr[i], cc[i], cape[i], tt[i], w8[i], d8[i],
            code.getOrNull(i), lo.getOrNull(i), mi.getOrNull(i), hi.getOrNull(i), day.getOrNull(i))
    }
}

@Serializable
data class ForecastCache(val at: Long, val bySite: Map<String, SiteForecast>)

fun Double.f1(): String = String.format(Locale.US, "%.1f", this)
