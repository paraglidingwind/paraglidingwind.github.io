package ro.parapanta.vant.ui

import androidx.compose.foundation.background
import ro.parapanta.vant.model.summarize
import ro.parapanta.vant.model.Status
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ro.parapanta.vant.model.CLOUD_NOTES
import ro.parapanta.vant.model.DIRS
import ro.parapanta.vant.model.HolfuyLink
import ro.parapanta.vant.model.Site
import ro.parapanta.vant.model.f1
import ro.parapanta.vant.model.knownDirs
import ro.parapanta.vant.model.trim
import ro.parapanta.vant.model.sectorOf
import ro.parapanta.vant.model.sky
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Sheet(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val p = LocalPalette.current
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = p.surface) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(title.uppercase(), color = p.ink, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = Display, letterSpacing = .4.sp)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

private fun dir(deg: Double) = "${DIRS[sectorOf(deg)]} (${deg.roundToInt()}°)"

@Composable
fun CellSheet(state: UiState, si: Int, h: String, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    val site = state.sites.getOrNull(si) ?: return onDismiss()
    val date = state.dates.getOrNull(state.dayIdx) ?: return onDismiss()
    val w = state.hour(site, date, h) ?: return onDismiss()
    val r = state.rateAt(site, date, h)
    val sun = state.sun(site, date)
    Sheet("${site.n} · $h:00", onDismiss) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(p.bgOf(r.status)).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            w.wd?.let { WindArrow(it, p.fg(r.status), Modifier.size(22.dp)) }
            Text(r.status.label, color = p.fg(r.status), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }
        Column(Modifier.padding(top = 8.dp, start = 4.dp)) {
            r.reasons.forEach {
                // Avertizările (vânt la altitudine, furtună, fără direcții) au marcajul din colțul celulei.
                Row {
                    Text(if (it in r.warnings) "◤ " else "• ", color = if (it in r.warnings) p.maybe else p.ink, fontSize = 15.sp)
                    Text(it, color = p.ink, fontSize = 15.sp)
                }
            }
        }
        val k = sky(w)
        Text("CER", color = p.muted, fontWeight = FontWeight.Bold, fontFamily = Display, letterSpacing = .8.sp,
            modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SkyIcon(k.icon, Modifier.size(30.dp))
            Column {
                Text(k.label, color = p.ink, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                k.type?.let { Text(it, color = p.muted, fontSize = 14.sp) }
            }
        }
        k.type?.let { CLOUD_NOTES[it] }?.let { Text(it, color = p.muted, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp)) }
        Spacer(Modifier.height(12.dp))
        listOf(
            "Vânt la 10 m" to "${w.ws?.f1()} m/s din ${w.wd?.let(::dir)}",
            "Rafale" to (w.wg?.let { "${it.f1()} m/s" } ?: "–"),
            "Vânt la ~1500 m" to (w.w8?.let { s -> "${s.f1()} m/s din ${w.d8?.let(::dir) ?: "–"}" } ?: "–"),
            "Nori" to ((w.cc?.let { "${it.roundToInt()} %" } ?: "–") + (w.lo?.let { " (jos ${it.roundToInt()} · mediu ${w.mi?.roundToInt()} · sus ${w.hi?.roundToInt()})" } ?: "")),
            "Ploaie" to (w.pr?.let { "${it.f1()} mm/h" } ?: "–"),
            "Instabilitate" to ((w.cape?.let { "CAPE ${it.roundToInt()} J/kg" } ?: "–") + (w.li?.let { " · LI ${(Math.round(it * 10) / 10.0).trim().replace('-', '−')}" } ?: "")),
            "Temperatură" to (w.tt?.let { "${it.f1()} °C" } ?: "–"),
            "Soare" to (sun?.let { (r, s) -> "răsărit $r · apus $s" } ?: "–"),
            "Decolare" to (dirsText(site) + (site.alt?.let { " · $it m" } ?: "")),
        ).forEach { (k, v) ->
            Row(Modifier.padding(vertical = 3.dp)) {
                Text(k, color = p.muted, modifier = Modifier.width(130.dp), fontSize = 15.sp)
                Text(v, color = p.ink, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }
        }
        Disclaimer(Modifier.padding(top = 12.dp))
        Links(site)
    }
}

/** Direcțiile unei decolări, în cuvinte (ca dirsTxt() de pe web). */
fun dirsText(site: Site): String {
    fun of(v: Int) = site.o.mapIndexedNotNull { i, x -> if (x == v) DIRS[i] else null }.joinToString(", ")
    if (!site.knownDirs()) return "fără direcții setate"
    return listOfNotNull(of(2).takeIf { it.isNotEmpty() }?.let { "bune: $it" }, of(1).takeIf { it.isNotEmpty() }?.let { "marginale: $it" }).joinToString(" · ")
}

@Composable
fun SiteSheet(state: UiState, si: Int, onDay: (Int) -> Unit, onFlew: (String) -> Unit, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    val site = state.sites.getOrNull(si) ?: return onDismiss()
    val hours = state.hours
    fun dirs(v: Int) = site.o.mapIndexedNotNull { i, x -> if (x == v) DIRS[i] else null }.joinToString(", ")
    Sheet(site.n, onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Rose(site.o, Modifier.size(72.dp))
            Column {
                Text(site.alt?.let { "$it m" } ?: "Altitudine necunoscută", color = p.ink, fontWeight = FontWeight.SemiBold)
                Text("Direcții bune: ${dirs(2).ifEmpty { "–" }}", color = p.muted)
                dirs(1).takeIf { it.isNotEmpty() }?.let { Text("Marginal: $it", color = p.muted) }
            }
        }
        OutlinedButton(onClick = { onFlew(site.n) }, Modifier.padding(top = 14.dp)) { Text("✓ Am zburat aici") }
        state.station(site)?.let { HolfuyBlock(it) }
        Text("URMĂTOARELE ZILE", color = p.muted, fontWeight = FontWeight.Bold, fontFamily = Display, letterSpacing = .8.sp,
            modifier = Modifier.padding(top = 18.dp, bottom = 4.dp))
        Text("Fiecare pătrățel e o oră, de la ${hours.first()} la ${hours.last()}. Atinge o zi ca s-o vezi în tabel.",
            color = p.muted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.dp, p.line, RoundedCornerShape(12.dp))) {
            Row(Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp)) {
                Spacer(Modifier.width(72.dp))
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    hours.forEachIndexed { i, h ->
                        Text(if (i % 3 == 0) h else "", Modifier.weight(1f), color = p.muted, fontSize = 11.sp,
                            softWrap = false, overflow = TextOverflow.Visible, maxLines = 1)
                    }
                }
            }
            state.dates.forEachIndexed { i, d ->
                val sm = state.summary(site, d)
                if (i > 0) HorizontalDivider(color = p.line)
                Row(
                    Modifier.fillMaxWidth().background(if (i == state.dayIdx) p.sunk else p.surface)
                        .clickable { onDay(i) }.padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Column(Modifier.width(72.dp)) {
                        Text(dayName(d, i), color = p.ink, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(dayDate(d), color = p.muted, fontSize = 12.sp)
                    }
                    Column(Modifier.weight(1f)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            sm.statuses.forEach { st ->
                                Box(Modifier.weight(1f).height(16.dp).clip(RoundedCornerShape(3.dp)).background(p.dot(st)))
                            }
                        }
                        Text(sm.text, color = if (sm.status == Status.NA || sm.status == Status.CALM) p.muted else p.fg(sm.status),
                            fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
        }
        Links(site)
    }
}

@Composable
private fun HolfuyBlock(link: HolfuyLink) {
    val p = LocalPalette.current
    val uri = LocalUriHandler.current
    val st = link.station
    val where = link.km?.let { if (it < 0.5) "la decolare" else "la ${String.format(Locale.US, "%.1f", it).replace('.', ',')} km" }
    Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("ACUM · STAȚIA HOLFUY ${st.n.uppercase()}", color = p.muted, fontWeight = FontWeight.Bold, fontFamily = Display,
            letterSpacing = .8.sp, modifier = Modifier.weight(1f))
        Text("Istoric ↗", color = p.accent, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
            modifier = Modifier.clickable { uri.openUri("https://holfuy.com/en/weather/${st.id}") }.padding(4.dp))
    }
    Text(listOfNotNull(where, st.alt?.let { "$it m" }).joinToString(" · ") +
        ". Vânt măsurat în timp real; zonele colorate de pe cadran sunt setate de administratorul stației.",
        color = p.muted, fontSize = 13.sp)
    HolfuyWidget(st.id, Modifier.padding(top = 8.dp).fillMaxWidth().height(258.dp).clip(RoundedCornerShape(12.dp)))
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun Links(site: Site) {
    val uri = LocalUriHandler.current
    val (la, lo) = site.lat to site.lon
    FlowRow(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { uri.openUri("https://www.windy.com/$la/$lo?$la,$lo,12") }) { Text("Windy") }
        OutlinedButton(onClick = { uri.openUri("https://www.meteoblue.com/ro/vreme/s%C4%83pt%C4%83m%C3%A2na/${la}N${lo}E") }) { Text("Meteoblue") }
        OutlinedButton(onClick = { uri.openUri("geo:$la,$lo?q=$la,$lo(${android.net.Uri.encode(site.n)})") }) { Text("Hartă") }
    }
}
