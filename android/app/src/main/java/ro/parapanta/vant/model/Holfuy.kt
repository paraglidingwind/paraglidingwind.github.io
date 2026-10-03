package ro.parapanta.vant.model

import kotlinx.serialization.Serializable
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

@Serializable
data class HolfuyStation(val id: Int, val n: String, val lat: Double, val lon: Double, val alt: Int? = null)

/** Stația atașată unui sit și distanța până la ea (null dacă stația nu e în lista cunoscută). */
data class HolfuyLink(val station: HolfuyStation, val km: Double?)

const val HOLFUY_MAX_KM = 5.0

fun distKm(a: Double, b: Double, c: Double, d: Double): Double {
    fun r(x: Double) = Math.toRadians(x)
    val h = sin(r(c - a) / 2).pow(2) + cos(r(a)) * cos(r(c)) * sin(r(d - b) / 2).pow(2)
    return 2 * 6371 * asin(sqrt(h))
}

/** hf > 0 = stație aleasă explicit, 0 = fără, -1 = cea mai apropiată stație la cel mult 5 km. Identic cu stationOf() de pe web. */
fun holfuyFor(site: Site, stations: List<HolfuyStation>): HolfuyLink? = when {
    site.hf == 0 -> null
    site.hf > 0 -> stations.firstOrNull { it.id == site.hf }
        ?.let { HolfuyLink(it, distKm(site.lat, site.lon, it.lat, it.lon)) }
        ?: HolfuyLink(HolfuyStation(site.hf, "Stația ${site.hf}", site.lat, site.lon), null)
    else -> stations.map { HolfuyLink(it, distKm(site.lat, site.lon, it.lat, it.lon)) }
        .filter { it.km!! <= HOLFUY_MAX_KM }.minByOrNull { it.km!! }
}

/** Extrage ID-ul stației din „774” sau dintr-un link holfuy.com/en/weather/774. */
fun parseHolfuyId(input: String): Int? =
    (Regex("""weather/(\d+)""").find(input) ?: Regex("""(\d+)\s*/?\s*$""").find(input.trim()))?.groupValues?.get(1)?.toIntOrNull()
