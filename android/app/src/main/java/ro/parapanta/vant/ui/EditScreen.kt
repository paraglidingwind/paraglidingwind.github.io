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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ro.parapanta.vant.model.DIRS
import ro.parapanta.vant.model.Site
import ro.parapanta.vant.model.Thresholds
import ro.parapanta.vant.model.key
import ro.parapanta.vant.model.parseCoords
import ro.parapanta.vant.model.trim
import java.text.Normalizer
import java.util.Locale

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
    val results = vm.repo.roSites.filter { it.key !in have && (q.isEmpty() || norm(it.n).contains(q)) }.take(if (q.isEmpty()) 8 else 40)

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
                SiteRow(s) {
                    IconButton(onClick = { vm.move(i, -1) }, enabled = i > 0) { Icon(Icons.Filled.KeyboardArrowUp, "Mută ${s.n} mai sus") }
                    IconButton(onClick = { vm.move(i, 1) }, enabled = i < state.sites.size - 1) { Icon(Icons.Filled.KeyboardArrowDown, "Mută ${s.n} mai jos") }
                    IconButton(onClick = { vm.remove(i); toast("${s.n} a fost șters") }) { Icon(Icons.Filled.Close, "Șterge ${s.n}", tint = p.no) }
                }
            }

            item {
                Section("Adaugă de pe ParaglidingEarth")
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true,
                    placeholder = { Text("Caută un sit din România (ex: Bunloc)") })
                Spacer(Modifier.height(6.dp))
            }
            if (results.isEmpty()) item { Text("Niciun rezultat. Îl poți adăuga manual mai jos.", color = p.muted, modifier = Modifier.padding(vertical = 8.dp)) }
            itemsIndexed(results, key = { _, s -> "ro" + s.key + s.n }) { _, s ->
                SiteRow(s) {
                    IconButton(onClick = { vm.add(s); toast("${s.n} a fost adăugat") }) { Icon(Icons.Filled.Add, "Adaugă ${s.n}", tint = p.accent) }
                }
            }

            item { ManualForm(onAdd = { vm.add(it); toast("${it.n} a fost adăugat") }) }
            item { ThresholdsForm(state.th, vm::setThresholds) }
            item { Backup(vm, ::toast) }
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
private fun SiteRow(s: Site, actions: @Composable () -> Unit) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Rose(s.o, Modifier.size(24.dp))
        Column(Modifier.weight(1f)) {
            Text(s.n, color = p.ink, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text((s.alt?.let { "$it m · " } ?: "") + String.format(Locale.US, "%.3f, %.3f", s.lat, s.lon), color = p.muted, fontSize = 12.sp)
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
                onAdd(Site(name.trim(), Math.round(c.first * 1e5) / 1e5, Math.round(c.second * 1e5) / 1e5, alt.toIntOrNull(), rose.toList()))
                name = ""; coord = ""; alt = ""; for (i in 0 until 8) rose[i] = 0
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
private fun Backup(vm: MainViewModel, toast: (String) -> Unit) {
    val p = LocalPalette.current
    val clip = LocalClipboardManager.current
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(armed) { if (armed) { delay(4000); armed = false } }
    Column {
        Section("Backup")
        Text("Lista e salvată pe acest telefon. Copiaz-o ca text ca s-o muți pe alt dispozitiv sau în pagina web.", color = p.muted, fontSize = 13.sp)
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { clip.setText(AnnotatedString(vm.exportJson())); toast("Lista a fost copiată") }) { Text("Copiază lista") }
            OutlinedButton(onClick = {
                val n = clip.getText()?.text?.let(vm::importJson)
                toast(if (n != null) "Am încărcat $n situri" else "În clipboard nu e o listă validă")
            }) { Text("Lipește lista") }
            OutlinedButton(onClick = {
                if (armed) { vm.resetSites(); armed = false; toast("Lista inițială a fost refăcută") } else armed = true
            }) { Text(if (armed) "Apasă din nou pentru confirmare" else "Revino la lista inițială", color = p.no) }
        }
    }
}
