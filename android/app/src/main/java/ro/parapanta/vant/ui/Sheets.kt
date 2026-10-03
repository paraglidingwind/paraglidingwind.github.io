package ro.parapanta.vant.ui

import androidx.compose.foundation.background
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
import ro.parapanta.vant.model.Site
import ro.parapanta.vant.model.f1
import ro.parapanta.vant.model.rate
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
    val r = rate(site, w, state.th)
    Sheet("${site.n} · $h:00", onDismiss) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(p.bgOf(r.status)).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            w.wd?.let { WindArrow(it, p.fg(r.status), Modifier.size(22.dp)) }
            Text(r.status.label, color = p.fg(r.status), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }
        Column(Modifier.padding(top = 8.dp, start = 4.dp)) {
            r.reasons.forEach { Text("• $it", color = p.ink, fontSize = 15.sp) }
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
        val good = site.o.mapIndexedNotNull { i, v -> when (v) { 2 -> DIRS[i]; 1 -> DIRS[i].lowercase(); else -> null } }.joinToString(" ")
        listOf(
            "Vânt la 10 m" to "${w.ws?.f1()} m/s din ${w.wd?.let(::dir)}",
            "Rafale" to (w.wg?.let { "${it.f1()} m/s" } ?: "–"),
            "Vânt la ~1500 m" to (w.w8?.let { s -> "${s.f1()} m/s din ${w.d8?.let(::dir) ?: "–"}" } ?: "–"),
            "Nori" to ((w.cc?.let { "${it.roundToInt()} %" } ?: "–") + (w.lo?.let { " (jos ${it.roundToInt()} · mediu ${w.mi?.roundToInt()} · sus ${w.hi?.roundToInt()})" } ?: "")),
            "Ploaie" to (w.pr?.let { "${it.f1()} mm/h" } ?: "–"),
            "CAPE" to (w.cape?.let { "${it.roundToInt()} J/kg" + if (it > 800) " · risc de dezvoltări" else "" } ?: "–"),
            "Temperatură" to (w.tt?.let { "${it.f1()} °C" } ?: "–"),
            "Decolare" to (good.ifEmpty { "–" } + (site.alt?.let { " · $it m" } ?: "")),
        ).forEach { (k, v) ->
            Row(Modifier.padding(vertical = 3.dp)) {
                Text(k, color = p.muted, modifier = Modifier.width(130.dp), fontSize = 15.sp)
                Text(v, color = p.ink, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }
        }
        Links(site)
    }
}

@Composable
fun SiteSheet(state: UiState, si: Int, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    val site = state.sites.getOrNull(si) ?: return onDismiss()
    Sheet(site.n, onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Rose(site.o, Modifier.size(72.dp))
            Column {
                Text(site.alt?.let { "$it m" } ?: "Altitudine necunoscută", color = p.ink, fontWeight = FontWeight.SemiBold)
                Text(String.format(Locale.US, "%.4f, %.4f", site.lat, site.lon), color = p.muted)
            }
        }
        Text("SĂPTĂMÂNA", color = p.muted, fontWeight = FontWeight.Bold, fontFamily = Display, letterSpacing = .8.sp,
            modifier = Modifier.padding(top = 18.dp, bottom = 6.dp))
        state.dates.forEachIndexed { i, d ->
            val st = state.bestOfDay(site, d)
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(p.dot(st)))
                Text("${dayName(d, i)} ${dayDate(d)}", color = p.ink, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(110.dp))
                Text("${st.label} (cea mai bună oră)", color = p.muted)
            }
        }
        Links(site)
    }
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
