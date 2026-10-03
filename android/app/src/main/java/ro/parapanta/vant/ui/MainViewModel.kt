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
import ro.parapanta.vant.model.ForecastCache
import ro.parapanta.vant.model.HolfuyLink
import ro.parapanta.vant.model.HolfuyStation
import ro.parapanta.vant.model.holfuyFor
import ro.parapanta.vant.model.Hour
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
    fun bestOfDay(site: Site, date: String): Status = bestOf(hours.map { rate(site, hour(site, date, it), th).status })
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    val repo = Repo(app)
    private val _state = MutableStateFlow(UiState(repo.loadSites(), repo.loadThresholds(), repo.loadCache(), holfuy = repo.holfuy))
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

    fun setThresholds(t: Thresholds) {
        val fixed = if (t.from > t.to) t.copy(from = t.to, to = t.from) else t
        repo.saveThresholds(fixed)
        _state.update { it.copy(th = fixed) }
    }
}
