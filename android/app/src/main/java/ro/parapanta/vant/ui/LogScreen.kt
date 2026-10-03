package ro.parapanta.vant.ui

import android.app.DatePickerDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ro.parapanta.vant.model.Flight
import ro.parapanta.vant.model.byMonth
import ro.parapanta.vant.model.bySite
import ro.parapanta.vant.model.flightsXlsx
import ro.parapanta.vant.model.fmtDur
import ro.parapanta.vant.model.validateFlight
import ro.parapanta.vant.model.zile
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

private val RO = Locale.forLanguageTag("ro")
private const val XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
private const val BOOK = "M6 3h11a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6z M9 3v18 M12.5 8h3.5 M12.5 12h3.5"
private val bookPath by lazy { PathParser().parsePathString(BOOK).toPath() }

/** Pictograma de jurnal (aceeași ca pe web). */
@Composable
fun BookIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        scale(size.minDimension / 24f, pivot = Offset.Zero) {
            drawPath(bookPath, color, style = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

private fun cap(s: String) = s.replaceFirstChar { it.uppercase() }
fun fmtFlightDay(date: String): String {
    val d = LocalDate.parse(date)
    return "${cap(d.dayOfWeek.getDisplayName(TextStyle.SHORT, RO).trimEnd('.'))} ${d.dayOfMonth} " +
        "${d.month.getDisplayName(TextStyle.SHORT, RO).trimEnd('.')} ${d.year}"
}
private fun monthName(key: String): String {
    val d = LocalDate.parse("$key-15")
    return "${cap(d.month.getDisplayName(TextStyle.FULL_STANDALONE, RO))} ${d.year}"
}
private fun today() = LocalDate.now(ZoneId.of("Europe/Bucharest")).toString()

/**
 * Jurnalul personal: o intrare = o zi de zbor pe o locație, cu timpul total în aer.
 * [draft] deschide direct formularul (de ex. din „Am zburat aici”); id gol = zi nouă.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(vm: MainViewModel, state: UiState, draft: Flight?, onDraftShown: () -> Unit, onClose: () -> Unit) {
    val p = LocalPalette.current
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    fun toast(msg: String) = scope.launch { snack.currentSnackbarData?.dismiss(); snack.showSnackbar(msg) }
    var bySites by rememberSaveable { mutableStateOf(false) }
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    var form by remember { mutableStateOf<Flight?>(null) }
    LaunchedEffect(draft) { if (draft != null) { form = draft; onDraftShown() } }
    val ctx = LocalContext.current
    // Exportul Excel: utilizatorul alege unde salvează fișierul (de ex. Descărcări sau Drive).
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(XLSX_MIME)) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val ok = runCatching { ctx.contentResolver.openOutputStream(uri)!!.use { it.write(flightsXlsx(state.flights)) } }.isSuccess
        toast(if (ok) "Fișierul Excel a fost salvat" else "Nu am putut salva fișierul")
    }

    val flights = state.flights
    val year = LocalDate.now().year.toString()
    val inYear = flights.filter { it.date.startsWith(year) }
    val places = flights.map { it.site }.toSet().size

    Scaffold(
        containerColor = p.bg,
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            TopAppBar(
                title = { Text("JURNAL DE ZBOR", fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 20.sp) },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Închide") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = p.bg),
            )
        },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Stat(fmtDur(flights.sumOf { it.minutes }), "în aer", Modifier.weight(1f))
                    Stat("${flights.size}", if (flights.size == 1) "zi de zbor" else "zile de zbor", Modifier.weight(1f))
                    Stat("$places", if (places == 1) "locație" else "locații", Modifier.weight(1f))
                }
                Text("În $year: ${fmtDur(inYear.sumOf { it.minutes })} în ${zile(inYear.size)}.", color = p.muted, fontSize = 13.sp,
                    modifier = Modifier.padding(top = 8.dp))
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { form = Flight("", today(), filter ?: state.sites.firstOrNull()?.n ?: "", 0) }) { Text("+ Adaugă zi de zbor") }
                    if (flights.isNotEmpty()) OutlinedButton(onClick = { exportLauncher.launch("jurnal-zbor-${today()}.xlsx") }) { Text("Export Excel") }
                }
                Row(
                    Modifier.padding(top = 16.dp).fillMaxWidth().clip(RoundedCornerShape(11.dp)).border(1.dp, p.line, RoundedCornerShape(11.dp))
                ) {
                    listOf(false to "Pe zile", true to "Pe locații").forEach { (v, label) ->
                        val sel = bySites == v
                        Box(
                            Modifier.weight(1f).height(44.dp).background(if (sel) p.accent else Color.Transparent)
                                .clickable { bySites = v; filter = null },
                            contentAlignment = Alignment.Center
                        ) { Text(label, color = if (sel) p.accentInk else p.muted, fontWeight = FontWeight.SemiBold) }
                    }
                }
            }
            if (flights.isEmpty()) {
                item {
                    Text("Încă nu ai nimic notat. Apasă „Adaugă zi de zbor”, sau „Am zburat aici” din pagina unei locații. Poți trece și zborurile mai vechi.",
                        color = p.muted, modifier = Modifier.padding(top = 16.dp))
                }
            } else if (!bySites) {
                filter?.let { f ->
                    item {
                        Row(
                            Modifier.padding(top = 12.dp).clip(RoundedCornerShape(50)).background(p.sunk).padding(start = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(f, color = p.ink, fontWeight = FontWeight.SemiBold)
                            IconButton(onClick = { filter = null }) { Icon(Icons.Filled.Close, "Arată toate locațiile", tint = p.muted) }
                        }
                    }
                }
                byMonth(flights, filter).forEach { g ->
                    item(key = "m" + g.key) { MonthHeader(monthName(g.key), "${fmtDur(g.minutes)} în ${zile(g.flights.size)}") }
                    items(g.flights, key = { it.id }) { f ->
                        LogRow(f.site, fmtFlightDay(f.date), fmtDur(f.minutes)) { form = f }
                    }
                }
            } else {
                item {
                    MonthHeader("Locații", null)
                    Text("Atinge o locație ca să vezi zilele de acolo.", color = p.muted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 4.dp))
                }
                items(bySite(flights), key = { "s" + it.site }) { s ->
                    LogRow(s.site, "${zile(s.days)} · ultima: ${fmtFlightDay(s.last)}", fmtDur(s.minutes)) { filter = s.site; bySites = false }
                }
            }
            item { Spacer(Modifier.height(40.dp)) }
        }
    }

    form?.let { f ->
        FlightSheet(
            initial = f,
            names = (listOfNotNull(f.site.takeIf { it.isNotBlank() }) + state.sites.map { it.n } + flights.map { it.site }).distinct(),
            onSave = { saved -> vm.saveFlight(saved); form = null; toast(if (f.id.isEmpty()) "Salvat: ${saved.site}, ${fmtDur(saved.minutes)}" else "Modificat") },
            onDelete = { vm.deleteFlight(f.id); form = null; toast("Șters") },
            onDismiss = { form = null },
        )
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    val p = LocalPalette.current
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(p.sunk).padding(horizontal = 12.dp, vertical = 10.dp)) {
        Text(value, color = p.ink, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 22.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, color = p.muted, fontSize = 12.5.sp)
    }
}

@Composable
private fun MonthHeader(title: String, detail: String?) {
    val p = LocalPalette.current
    Row(Modifier.padding(top = 20.dp, bottom = 6.dp), verticalAlignment = Alignment.Bottom) {
        Text(title.uppercase(), color = p.muted, fontFamily = Display, fontWeight = FontWeight.Bold, letterSpacing = .8.sp, fontSize = 14.sp)
        detail?.let { Text(" · $it", color = p.muted, fontSize = 13.sp) }
    }
}

@Composable
private fun LogRow(title: String, sub: String, dur: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    Column {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, color = p.ink, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(sub, color = p.muted, fontSize = 12.5.sp)
            }
            Text(dur, color = p.ink, fontWeight = FontWeight.Bold)
        }
        HorizontalDivider(color = p.line)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FlightSheet(initial: Flight, names: List<String>, onSave: (Flight) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val other = "__other"
    var site by remember { mutableStateOf(if (names.isEmpty()) other else initial.site.ifBlank { names.first() }) }
    var otherName by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(initial.date) }
    var h by remember { mutableStateOf(if (initial.minutes > 0) (initial.minutes / 60).toString() else "") }
    var m by remember { mutableStateOf(if (initial.minutes > 0) (initial.minutes % 60).toString() else "") }
    var err by remember { mutableStateOf<String?>(null) }
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(armed) { if (armed) { delay(4000); armed = false } }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = p.surface) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding().imePadding().padding(bottom = 16.dp)) {
            Text(if (initial.id.isEmpty()) "ZI DE ZBOR" else "EDITEAZĂ ZIUA DE ZBOR", color = p.ink, fontFamily = Display,
                fontWeight = FontWeight.Bold, fontSize = 20.sp, letterSpacing = .4.sp)
            Text("Locație", color = p.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                names.forEach { n -> FilterChip(selected = site == n, onClick = { site = n }, label = { Text(n) }) }
                FilterChip(selected = site == other, onClick = { site = other }, label = { Text("Altă locație…") })
            }
            if (site == other) {
                OutlinedTextField(otherName, { otherName = it }, Modifier.fillMaxWidth().padding(top = 4.dp), singleLine = true,
                    label = { Text("Numele locației") })
            }
            Text("Data", color = p.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
            OutlinedButton(onClick = {
                val d = LocalDate.parse(date)
                DatePickerDialog(ctx, { _, y, mo, day -> date = LocalDate.of(y, mo + 1, day).toString() }, d.year, d.monthValue - 1, d.dayOfMonth).apply {
                    datePicker.maxDate = System.currentTimeMillis()
                }.show()
            }, Modifier.fillMaxWidth()) { Text(fmtFlightDay(date), color = p.ink) }
            Text("Poți alege și o zi din trecut, pentru zborurile mai vechi.", color = p.muted, fontSize = 12.5.sp, modifier = Modifier.padding(top = 4.dp))
            Text("Timp în aer în ziua respectivă", color = p.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(h, { h = it.filter(Char::isDigit).take(2) }, Modifier.weight(1f), singleLine = true, label = { Text("ore") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(m, { m = it.filter(Char::isDigit).take(3) }, Modifier.weight(1f), singleLine = true, label = { Text("minute") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
            val totalMin = (h.toIntOrNull() ?: 0) * 60 + (m.toIntOrNull() ?: 0)
            if (totalMin > 0) Text("Total: ${fmtDur(totalMin)}", color = p.ink, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
            err?.let { Text(it, color = p.no, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)) }
            Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = {
                    val name = (if (site == other) otherName else site).trim()
                    val hh = h.toIntOrNull() ?: 0
                    val mm = m.toIntOrNull() ?: 0
                    err = validateFlight(name, date, hh, mm, today())
                    if (err == null) onSave(initial.copy(site = name, date = date, minutes = hh * 60 + mm))
                }) { Text("Salvează") }
                if (initial.id.isNotEmpty()) {
                    OutlinedButton(onClick = { if (armed) onDelete() else armed = true }) {
                        Text(if (armed) "Apasă din nou pentru ștergere" else "Șterge", color = p.no)
                    }
                }
            }
            Spacer(Modifier.width(1.dp))
        }
    }
}
