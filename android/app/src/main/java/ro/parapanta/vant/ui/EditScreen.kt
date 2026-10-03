package ro.parapanta.vant.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ro.parapanta.vant.model.DIRS
import ro.parapanta.vant.model.HolfuyLink
import ro.parapanta.vant.model.parseHolfuyId
import ro.parapanta.vant.model.distKm
import kotlin.math.roundToInt
import ro.parapanta.vant.model.Site
import ro.parapanta.vant.model.Thresholds
import ro.parapanta.vant.model.key
import ro.parapanta.vant.model.parseCoords
import ro.parapanta.vant.model.trim
import java.text.Normalizer
import java.util.Locale

private const val PREVIEW = 10

private fun norm(s: String) = Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScreen(vm: MainViewModel, state: UiState, onClose: () -> Unit) {
    val p = LocalPalette.current
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    fun toast(msg: String) = scope.launch { snack.currentSnackbarData?.dismiss(); snack.showSnackbar(msg) }
    var query by remember { mutableStateOf("") }
    val have = state.sites.map { it.key }.toSet()
    val q = norm(query.trim())
    var showAll by rememberSaveable { mutableStateOf(false) }
    // Toate siturile din România, ordonate după distanța până la cel mai apropiat sit din lista ta.
    val all = vm.repo.roSites.filter { it.key !in have }.map { s ->
        s to state.sites.map { m -> m to distKm(s.lat, s.lon, m.lat, m.lon) }.minByOrNull { it.second }
    }.let { l -> if (state.sites.isEmpty()) l else l.sortedBy { it.second!!.second } }
    val matches = if (q.isEmpty()) all else all.filter { norm(it.first.n).contains(q) }
    val results = if (q.isNotEmpty() || showAll) matches else matches.take(PREVIEW)

    Scaffold(
        containerColor = p.bg,
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            TopAppBar(
                title = { Text("SITURI ȘI SETĂRI", fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 20.sp) },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Închide") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = p.bg),
            )
        },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad).imePadding().padding(horizontal = 16.dp)) {
            item { Section("Lista ta") }
            if (state.sites.isEmpty()) item { Text("Niciun sit în listă.", color = p.muted) }
            itemsIndexed(state.sites, key = { _, s -> "my" + s.key + s.n }) { i, s ->
                SiteRow(s, state.station(s)) {
                    IconButton(onClick = { vm.move(i, -1) }, enabled = i > 0) { Icon(Icons.Filled.KeyboardArrowUp, "Mută ${s.n} mai sus") }
                    IconButton(onClick = { vm.move(i, 1) }, enabled = i < state.sites.size - 1) { Icon(Icons.Filled.KeyboardArrowDown, "Mută ${s.n} mai jos") }
                    IconButton(onClick = { vm.remove(i); toast("${s.n} a fost șters") }) { Icon(Icons.Filled.Close, "Șterge ${s.n}", tint = p.no) }
                }
            }
            item { ResetButton(onReset = { vm.resetSites(); toast("Lista inițială a fost refăcută") }) }

            item {
                Section("Toate siturile din România")
                Text("De pe ParaglidingEarth, ordonate după distanța față de siturile tale. Apasă + ca să adaugi.",
                    color = p.muted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true,
                    placeholder = { Text("Caută după nume (ex: Bunloc)") })
                Spacer(Modifier.height(6.dp))
            }
            if (results.isEmpty()) item { Text("Niciun rezultat. Îl poți adăuga manual mai jos.", color = p.muted, modifier = Modifier.padding(vertical = 8.dp)) }
            itemsIndexed(results, key = { _, r -> "ro" + r.first.key + r.first.n }) { _, (s, near) ->
                val meta = listOfNotNull(s.alt?.let { "$it m" }, near?.let { (m, km) -> "${km.roundToInt()} km de ${m.n}" }).joinToString(" · ")
                SiteRow(s, meta = meta) {
                    IconButton(onClick = { vm.add(s); toast("${s.n} a fost adăugat") }) { Icon(Icons.Filled.Add, "Adaugă ${s.n}", tint = p.accent) }
                }
            }
            if (q.isEmpty() && matches.size > PREVIEW) item {
                TextButton(onClick = { showAll = !showAll }, Modifier.fillMaxWidth().height(48.dp)) {
                    Text(if (showAll) "Arată mai puține" else "Arată toate cele ${matches.size} situri", fontWeight = FontWeight.Bold)
                }
            }

            item { ManualForm(onAdd = { vm.add(it); toast("${it.n} a fost adăugat") }) }
            item { ThresholdsForm(state.th, vm::setThresholds) }
            item { Spacer(Modifier.height(40.dp)) }
        }
    }
}

@Composable
private fun Section(t: String) {
    Text(t.uppercase(), color = LocalPalette.current.muted, fontWeight = FontWeight.Bold, fontFamily = Display,
        letterSpacing = .8.sp, fontSize = 14.sp, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
}

@Composable
private fun SiteRow(s: Site, station: HolfuyLink? = null, meta: String? = null, actions: @Composable () -> Unit) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Rose(s.o, Modifier.size(24.dp))
        Column(Modifier.weight(1f)) {
            Text(s.n, color = p.ink, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(meta ?: ((s.alt?.let { "$it m · " } ?: "") + String.format(Locale.US, "%.3f, %.3f", s.lat, s.lon) +
                (station?.let { " · Holfuy ${it.station.n}" } ?: "")), color = p.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        actions()
    }
}

@Composable
private fun ManualForm(onAdd: (Site) -> Unit) {
    val p = LocalPalette.current
    var name by remember { mutableStateOf("") }
    var coord by remember { mutableStateOf("") }
    var alt by remember { mutableStateOf("") }
    var hf by remember { mutableStateOf("") }
    val rose = remember { mutableStateListOf(0, 0, 0, 0, 0, 0, 0, 0) }
    var err by remember { mutableStateOf<String?>(null) }
    Column {
        Section("Adaugă manual")
        OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Nume") }, singleLine = true)
        OutlinedTextField(coord, { coord = it }, Modifier.fillMaxWidth().padding(top = 8.dp), singleLine = true,
            label = { Text("Coordonate sau link Google Maps") }, placeholder = { Text("46.6129, 23.4350") })
        Text("În Google Maps ține apăsat pe decolare, apoi copiază coordonatele afișate sus.", color = p.muted, fontSize = 12.5.sp, modifier = Modifier.padding(top = 4.dp))
        OutlinedTextField(alt, { alt = it.filter(Char::isDigit) }, Modifier.fillMaxWidth().padding(top = 8.dp), singleLine = true,
            label = { Text("Altitudine decolare (m)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        OutlinedTextField(hf, { hf = it }, Modifier.fillMaxWidth().padding(top = 8.dp), singleLine = true,
            label = { Text("Stație Holfuy (opțional)") }, placeholder = { Text("774 sau holfuy.com/en/weather/774") })
        Text("Dacă lași gol, se alege automat stația cea mai apropiată, la cel mult 5 km.", color = p.muted, fontSize = 12.5.sp, modifier = Modifier.padding(top = 4.dp))
        Text("Direcții bune de vânt (apasă: gri = nu, galben = marginal, verde = bun)", color = p.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
        val order = listOf(7, 0, 1, 6, -1, 2, 5, 4, 3)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            order.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { i ->
                        if (i < 0) Box(Modifier.size(64.dp, 48.dp), contentAlignment = Alignment.Center) {
                            Text("vântul\nbate din", color = p.muted, fontSize = 11.sp, lineHeight = 12.sp)
                        } else {
                            val v = rose[i]
                            val bg = when (v) { 2 -> p.goBg; 1 -> p.maybeBg; else -> p.surface }
                            val fg = when (v) { 2 -> p.go; 1 -> p.maybe; else -> p.ink }
                            Box(
                                Modifier.size(64.dp, 48.dp).clip(RoundedCornerShape(10.dp)).background(bg)
                                    .border(1.dp, if (v == 0) p.line else bg, RoundedCornerShape(10.dp))
                                    .clickable { rose[i] = (v + 1) % 3 },
                                contentAlignment = Alignment.Center
                            ) { Text(DIRS[i], color = fg, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
        }
        err?.let { Text(it, color = p.no, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)) }
        Button(onClick = {
            val c = parseCoords(coord)
            err = when {
                name.isBlank() -> "Scrie un nume pentru sit."
                c == null && Regex("goo\\.gl|maps\\.app").containsMatchIn(coord) ->
                    "Linkurile scurte Google Maps nu conțin coordonatele. Deschide linkul, ține apăsat pe decolare și copiază coordonatele."
                c == null -> "Coordonatele nu sunt valide. Exemplu: 46.6129, 23.4350"
                rose.none { it > 0 } -> "Alege cel puțin o direcție bună de vânt."
                else -> null
            }
            if (err == null && c != null) {
                onAdd(Site(name.trim(), Math.round(c.first * 1e5) / 1e5, Math.round(c.second * 1e5) / 1e5, alt.toIntOrNull(), rose.toList(), parseHolfuyId(hf) ?: -1))
                name = ""; coord = ""; alt = ""; hf = ""; for (i in 0 until 8) rose[i] = 0
            }
        }, Modifier.padding(top = 12.dp)) { Text("Adaugă situl") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThresholdsForm(th: Thresholds, onChange: (Thresholds) -> Unit) {
    Column {
        Section("Praguri")
        val fields: List<Triple<String, Double, (Double) -> Thresholds>> = listOf(
            Triple("Bun până la (m/s)", th.good) { v -> th.copy(good = v) },
            Triple("Marginal până la", th.marg) { v -> th.copy(marg = v) },
            Triple("Calm sub", th.calm) { v -> th.copy(calm = v) },
            Triple("Rafale maxime", th.gust) { v -> th.copy(gust = v) },
            Triple("Rafală − vânt peste", th.spread) { v -> th.copy(spread = v) },
            Triple("Ploaie peste (mm/h)", th.rain) { v -> th.copy(rain = v) },
            Triple("De la ora", th.from.toDouble()) { v -> th.copy(from = v.toInt().coerceIn(0, 23)) },
            Triple("Până la ora", th.to.toDouble()) { v -> th.copy(to = v.toInt().coerceIn(0, 23)) },
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), maxItemsInEachRow = 2) {
            fields.forEach { (label, value, make) ->
                var txt by remember(value) { mutableStateOf(value.trim()) }
                OutlinedTextField(txt, {
                    txt = it
                    it.replace(',', '.').toDoubleOrNull()?.let { v -> onChange(make(v)) }
                }, Modifier.weight(1f).padding(bottom = 8.dp), label = { Text(label, maxLines = 1) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            }
        }
        OutlinedButton(onClick = { onChange(Thresholds()) }) { Text("Praguri implicite") }
    }
}

@Composable
private fun ResetButton(onReset: () -> Unit) {
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(armed) { if (armed) { delay(4000); armed = false } }
    OutlinedButton(onClick = { if (armed) { onReset(); armed = false } else armed = true }, Modifier.padding(top = 8.dp)) {
        Text(if (armed) "Apasă din nou pentru confirmare" else "Revino la lista inițială", color = LocalPalette.current.no)
    }
}
