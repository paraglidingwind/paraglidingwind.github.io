package ro.parapanta.vant.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ro.parapanta.vant.data.Repo
import ro.parapanta.vant.model.Flight
import ro.parapanta.vant.model.ForecastCache
import ro.parapanta.vant.model.HolfuyLink
import ro.parapanta.vant.model.HolfuyStation
import ro.parapanta.vant.model.holfuyFor
import ro.parapanta.vant.model.importMessage
import ro.parapanta.vant.model.mergeFlights
import ro.parapanta.vant.model.mergeSites
import ro.parapanta.vant.model.readXlsx
import ro.parapanta.vant.model.Hour
import ro.parapanta.vant.model.Rating
import ro.parapanta.vant.model.summarize
import ro.parapanta.vant.model.Site
import ro.parapanta.vant.model.Status
import ro.parapanta.vant.model.Thresholds
import ro.parapanta.vant.model.bestOf
import ro.parapanta.vant.model.key
import ro.parapanta.vant.model.rate
import java.time.LocalDate
import java.time.ZoneId

val ZONE: ZoneId = ZoneId.of("Europe/Bucharest")
private const val STALE_MS = 30 * 60 * 1000L

data class UiState(
    val sites: List<Site>,
    val th: Thresholds,
    val fc: ForecastCache?,
    val loading: Boolean = false,
    val error: String? = null,
    val dayIdx: Int = 0,
    val holfuy: List<HolfuyStation> = emptyList(),
    val flights: List<Flight> = emptyList(),
) {
    fun station(site: Site): HolfuyLink? = holfuyFor(site, holfuy)
    /** Zilele din prognoză, începând cu azi. */
    val dates: List<String> by lazy {
        val f = fc?.bySite?.values?.firstOrNull() ?: return@lazy emptyList()
        val today = LocalDate.now(ZONE).toString()
        f.t.map { it.take(10) }.distinct().filter { it >= today }
    }
    val hours: List<String> by lazy { (th.from..th.to).map { it.toString().padStart(2, '0') } }

    fun hour(site: Site, date: String, h: String): Hour? = fc?.bySite?.get(site.key)?.at("${date}T$h:00")
    private fun plus(h: String, k: Int) = (h.toInt() + k).toString().padStart(2, '0')
    /** Ora [h] din ziua [date], evaluată împreună cu următoarele 2 ore (o furtună care vine schimbă verdictul de acum). */
    fun rateAt(site: Site, date: String, h: String): Rating =
        rate(site, hour(site, date, h), th, listOf(hour(site, date, plus(h, 1)), hour(site, date, plus(h, 2))))
    /** Rezumatul zilei; cele 2 ore de după interval contează pentru furtuni. */
    fun summary(site: Site, date: String) = summarize(site, hours, hours.map { hour(site, date, it) }, th,
        listOf(hour(site, date, plus(hours.last(), 1)), hour(site, date, plus(hours.last(), 2))))
    /** Starea zilei pentru punctul din banda de zile: aceeași ca rezumatul (fereastra minimă, fără noapte). */
    fun bestOfDay(site: Site, date: String): Status = summary(site, date).status
    fun sun(site: Site, date: String): Pair<String, String>? = fc?.bySite?.get(site.key)?.sun(date)
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    val repo = Repo(app)
    private val _state = MutableStateFlow(UiState(repo.loadSites(), repo.loadThresholds(), repo.loadCache(), holfuy = repo.holfuy, flights = repo.loadFlights()))
    val state: StateFlow<UiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh(force: Boolean = false) {
        val s = _state.value
        if (s.loading || s.sites.isEmpty()) return
        val missing = s.sites.any { s.fc?.bySite?.containsKey(it.key) != true }
        if (!force && !missing && s.fc != null && System.currentTimeMillis() - s.fc.at < STALE_MS) return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val sites = _state.value.sites
            try {
                val fc = repo.fetch(sites)
                _state.update { it.copy(fc = fc, loading = false) }
            } catch (e: Exception) {
                val msg = if (e is java.net.UnknownHostException) "Ești offline." else (e.message ?: "Eroare de rețea.")
                _state.update { it.copy(loading = false, error = msg) }
            }
        }
    }

    fun selectDay(i: Int) = _state.update { it.copy(dayIdx = i) }

    private fun setSites(list: List<Site>, fetch: Boolean = false) {
        repo.saveSites(list)
        _state.update { it.copy(sites = list) }
        if (fetch) refresh()
    }

    fun add(site: Site) = setSites(_state.value.sites + site, fetch = true)
    fun remove(i: Int) = setSites(_state.value.sites.toMutableList().apply { removeAt(i) })
    fun move(i: Int, d: Int) {
        val l = _state.value.sites.toMutableList()
        val j = i + d
        if (j !in l.indices) return
        l[i] = l[j].also { l[j] = l[i] }
        setSites(l)
    }
    fun resetSites() = setSites(repo.defaultSites, fetch = true)

    /** Adaugă o zi de zbor nouă (id gol) sau o înlocuiește pe cea cu același id. */
    fun saveFlight(f: Flight) {
        val withId = if (f.id.isEmpty()) f.copy(id = System.currentTimeMillis().toString(36) + (1000..9999).random()) else f
        val list = _state.value.flights.filter { it.id != withId.id } + withId
        repo.saveFlights(list)
        _state.update { it.copy(flights = list) }
    }

    /** Importă un export Excel: adaugă doar zilele de zbor și siturile care lipsesc. Întoarce mesajul pentru utilizator. */
    fun importXlsx(bytes: ByteArray): String {
        val data = readXlsx(bytes) ?: return "Nu am putut citi fișierul. Alege un fișier exportat din jurnal."
        if (data.flights.isEmpty() && data.sites.isEmpty()) return "Fișierul nu conține zile de zbor."
        val (fl, nf) = mergeFlights(_state.value.flights, data.flights) { java.util.UUID.randomUUID().toString() }
        val (st, ns) = mergeSites(_state.value.sites, data.sites)
        repo.saveFlights(fl)
        if (ns > 0) repo.saveSites(st)
        _state.update { it.copy(flights = fl, sites = st) }
        if (ns > 0) refresh()
        return importMessage(nf, ns)
    }

    fun deleteFlight(id: String) {
        val list = _state.value.flights.filter { it.id != id }
        repo.saveFlights(list)
        _state.update { it.copy(flights = list) }
    }

    fun setThresholds(t: Thresholds) {
        val fixed = if (t.from > t.to) t.copy(from = t.to, to = t.from) else t
        repo.saveThresholds(fixed)
        _state.update { it.copy(th = fixed) }
    }
}
