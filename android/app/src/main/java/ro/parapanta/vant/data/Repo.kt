package ro.parapanta.vant.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import ro.parapanta.vant.model.ForecastCache
import ro.parapanta.vant.model.HolfuyStation
import ro.parapanta.vant.model.Site
import ro.parapanta.vant.model.SiteForecast
import ro.parapanta.vant.model.Thresholds
import ro.parapanta.vant.model.key
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

private const val HOURLY =
    "wind_speed_10m,wind_direction_10m,wind_gusts_10m,precipitation,cloud_cover,cape,temperature_2m,wind_speed_850hPa,wind_direction_850hPa,weather_code,cloud_cover_low,cloud_cover_mid,cloud_cover_high,is_day"

@Serializable
private data class OmResp(val hourly: OmHourly)

@Serializable
private data class OmHourly(
    val time: List<String>,
    @SerialName("wind_speed_10m") val ws: List<Double?>,
    @SerialName("wind_direction_10m") val wd: List<Double?>,
    @SerialName("wind_gusts_10m") val wg: List<Double?>,
    @SerialName("precipitation") val pr: List<Double?>,
    @SerialName("cloud_cover") val cc: List<Double?>,
    @SerialName("cape") val cape: List<Double?>,
    @SerialName("temperature_2m") val tt: List<Double?>,
    @SerialName("wind_speed_850hPa") val w8: List<Double?>,
    @SerialName("wind_direction_850hPa") val d8: List<Double?>,
    @SerialName("weather_code") val code: List<Int?> = emptyList(),
    @SerialName("cloud_cover_low") val lo: List<Double?> = emptyList(),
    @SerialName("cloud_cover_mid") val mi: List<Double?> = emptyList(),
    @SerialName("cloud_cover_high") val hi: List<Double?> = emptyList(),
    @SerialName("is_day") val day: List<Int?> = emptyList(),
)

class Repo(private val ctx: Context) {
    private val prefs = ctx.getSharedPreferences("vant", Context.MODE_PRIVATE)
    private val cacheFile = File(ctx.filesDir, "forecast2.json")
    val json = Json { ignoreUnknownKeys = true }

    val roSites: List<Site> by lazy { readAsset("ro_sites.json") }
    val defaultSites: List<Site> by lazy { readAsset("initial_sites.json") }
    val holfuy: List<HolfuyStation> by lazy {
        json.decodeFromString(ctx.assets.open("holfuy_ro.json").bufferedReader().use { it.readText() })
    }

    private fun readAsset(name: String): List<Site> =
        json.decodeFromString(ctx.assets.open(name).bufferedReader().use { it.readText() })

    fun loadSites(): List<Site> =
        prefs.getString("sites", null)?.let { runCatching { json.decodeFromString<List<Site>>(it) }.getOrNull() } ?: defaultSites

    fun saveSites(sites: List<Site>) = prefs.edit().putString("sites", json.encodeToString(sites)).apply()

    fun loadThresholds(): Thresholds =
        prefs.getString("thresholds", null)?.let { runCatching { json.decodeFromString<Thresholds>(it) }.getOrNull() } ?: Thresholds()

    fun saveThresholds(t: Thresholds) = prefs.edit().putString("thresholds", json.encodeToString(t)).apply()

    fun loadCache(): ForecastCache? =
        runCatching { json.decodeFromString<ForecastCache>(cacheFile.readText()) }.getOrNull()

    /** Un singur request Open-Meteo pentru toate siturile. */
    suspend fun fetch(sites: List<Site>): ForecastCache = withContext(Dispatchers.IO) {
        fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
        val q = listOf(
            "latitude" to sites.joinToString(",") { it.lat.toString() },
            "longitude" to sites.joinToString(",") { it.lon.toString() },
            "elevation" to sites.joinToString(",") { it.alt?.toString() ?: "nan" },
            "hourly" to HOURLY,
            "wind_speed_unit" to "ms",
            "timezone" to "Europe/Bucharest",
            "forecast_days" to "7",
        ).joinToString("&") { (k, v) -> "$k=${enc(v)}" }
        val conn = URL("https://api.open-meteo.com/v1/forecast?$q").openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 20000
        try {
            if (conn.responseCode != 200) error("Serverul de prognoză a răspuns ${conn.responseCode}")
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val el = json.parseToJsonElement(body)
            val items: List<JsonElement> = if (el is JsonArray) el else listOf(el)
            val bySite = sites.zip(items).associate { (s, e) ->
                val h = json.decodeFromJsonElement(OmResp.serializer(), e).hourly
                s.key to SiteForecast(h.time, h.ws, h.wd, h.wg, h.pr, h.cc, h.cape, h.tt, h.w8, h.d8, h.code, h.lo, h.mi, h.hi, h.day)
            }
            ForecastCache(System.currentTimeMillis(), bySite).also { cacheFile.writeText(json.encodeToString(it)) }
        } finally {
            conn.disconnect()
        }
    }
}
