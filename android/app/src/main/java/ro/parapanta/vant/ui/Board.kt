package ro.parapanta.vant.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.first
import ro.parapanta.vant.model.Status
import ro.parapanta.vant.model.rate
import ro.parapanta.vant.model.sky
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle as JTextStyle
import java.util.Locale
import kotlin.math.roundToInt

val CELL_W = 52.dp
val NAME_W = 118.dp
val ROW_H = 62.dp
private val RO = Locale.forLanguageTag("ro")

fun dayName(date: String, idx: Int): String {
    val d = LocalDate.parse(date)
    val today = LocalDate.now(ZONE)
    return when (d) {
        today -> "Azi"
        today.plusDays(1) -> "Mâine"
        else -> d.dayOfWeek.getDisplayName(JTextStyle.SHORT, RO).trimEnd('.').replaceFirstChar { it.uppercase() }
    }
}

fun dayDate(date: String): String {
    val d = LocalDate.parse(date)
    return "${d.dayOfMonth} ${d.month.getDisplayName(JTextStyle.SHORT, RO).trimEnd('.')}"
}

@Composable
fun WindArrow(deg: Double, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        rotate(((deg + 180) % 360).toFloat()) {
            val w = size.width
            val h = size.height
            val p = Path().apply {
                moveTo(w * .5f, h * .08f); lineTo(w * .79f, h * .875f); lineTo(w * .5f, h * .69f); lineTo(w * .21f, h * .875f); close()
            }
            drawPath(p, color)
        }
    }
}

@Composable
fun Rose(o: List<Int>, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Canvas(modifier) {
        val r = size.minDimension / 2
        for (i in 0 until 8) {
            val c = when (o.getOrElse(i) { 0 }) { 2 -> p.go; 1 -> p.maybe; else -> p.line }
            drawArc(c, startAngle = i * 45f - 22.5f - 90f + 2f, sweepAngle = 41f, useCenter = true,
                topLeft = Offset(center.x - r, center.y - r), size = Size(r * 2, r * 2))
        }
        drawCircle(p.surface, radius = r * .28f)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Board(state: UiState, onCell: (Int, String) -> Unit, onSite: (Int) -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val scroll = rememberScrollState()
    val date = state.dates.getOrNull(state.dayIdx) ?: return
    val hours = state.hours
    val today = date == LocalDate.now(ZONE).toString()
    val nowH = if (today) LocalTime.now(ZONE).hour.toString().padStart(2, '0') else null
    val cellPx = with(LocalDensity.current) { CELL_W.toPx() }

    LaunchedEffect(date, hours) {
        snapshotFlow { scroll.maxValue }.first { it > 0 && it < Int.MAX_VALUE }
        val idx = nowH?.let { hours.indexOf(it) } ?: -1
        scroll.scrollTo(if (idx > 0) ((idx - 1) * cellPx).roundToInt() else 0)
    }

    LazyColumn(
        modifier
            .padding(start = 16.dp)
            .clip(RoundedCornerShape(topStart = 12.dp))
            .border(1.dp, p.line, RoundedCornerShape(topStart = 12.dp))
            .background(p.surface)
    ) {
        stickyHeader {
            Row(Modifier.fillMaxWidth().height(30.dp).background(p.surface), verticalAlignment = Alignment.CenterVertically) {
                Text(dayName(date, state.dayIdx), Modifier.width(NAME_W).padding(start = 10.dp), color = p.muted, fontSize = 12.sp, fontFamily = Condensed)
                Row(Modifier.horizontalScroll(scroll)) {
                    hours.forEach { h ->
                        Text(h, Modifier.width(CELL_W), color = if (h == nowH) p.accent else p.muted,
                            fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFamily = Condensed,
                            style = TextStyle(textAlign = androidx.compose.ui.text.style.TextAlign.Center))
                    }
                }
            }
            HorizontalDivider(color = p.line)
        }
        itemsIndexed(state.sites) { si, site ->
            Row(Modifier.height(ROW_H)) {
                Row(
                    Modifier.width(NAME_W).fillMaxHeight().clickable { onSite(si) }.padding(start = 8.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Rose(site.o, Modifier.size(26.dp))
                    Column {
                        Text(site.n, color = p.ink, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, fontFamily = Condensed,
                            maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 15.sp)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            site.alt?.let { Text("$it m", color = p.muted, fontSize = 11.5.sp, fontFamily = Condensed) }
                            if (state.station(site) != null) LiveBadge()
                        }
                    }
                }
                Box(Modifier.width(1.dp).fillMaxHeight().background(p.line))
                Row(Modifier.horizontalScroll(scroll)) {
                    hours.forEach { h ->
                        val w = state.hour(site, date, h)
                        val r = state.rateAt(site, date, h)
                        HourCell(w?.ws, w?.wd, w?.wg, w?.pr, w?.let { sky(it).icon }, r.status, state.th.rain) { if (r.status != Status.NA) onCell(si, h) }
                    }
                }
            }
            HorizontalDivider(color = p.line)
        }
    }
}

@Composable
fun LiveBadge() {
    val p = LocalPalette.current
    Text("LIVE", Modifier.clip(RoundedCornerShape(4.dp)).background(p.accent).padding(horizontal = 4.dp),
        color = p.accentInk, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, letterSpacing = .5.sp, lineHeight = 13.sp)
}

@Composable
private fun HourCell(ws: Double?, wd: Double?, wg: Double?, pr: Double?, skyIcon: String?, st: Status, rainMax: Double, onClick: () -> Unit) {
    val p = LocalPalette.current
    val fg = p.fg(st)
    Column(
        Modifier.width(CELL_W).fillMaxHeight().padding(start = 1.dp).background(p.bgOf(st)).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        if (ws == null || wd == null) {
            Text("–", color = p.muted)
            return@Column
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            skyIcon?.let { SkyIcon(it, Modifier.size(19.dp)) }
            WindArrow(wd, fg, Modifier.size(17.dp))
        }
        Text(ws.roundToInt().toString(), color = fg, fontSize = 17.sp, fontWeight = FontWeight.Bold, fontFamily = Condensed, lineHeight = 18.sp)
        if (pr != null && pr > rainMax) Text("${"%.1f".format(Locale.US, pr)}mm", color = fg, fontSize = 10.sp, lineHeight = 11.sp, fontFamily = Condensed)
        else Text(wg?.roundToInt()?.toString() ?: "", color = fg.copy(alpha = .85f), fontSize = 11.5.sp, lineHeight = 12.sp, fontFamily = Condensed)
    }
}

@Composable
fun DayStrip(state: UiState, onSelect: (Int) -> Unit) {
    val p = LocalPalette.current
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(state.dates) { i, d ->
            val sel = i == state.dayIdx
            Column(
                Modifier.clip(RoundedCornerShape(12.dp))
                    .background(p.surface)
                    .border(if (sel) 2.dp else 1.dp, if (sel) p.accent else p.line, RoundedCornerShape(12.dp))
                    .clickable { onSelect(i) }
                    .padding(horizontal = 10.dp, vertical = 7.dp)
                    .width(64.dp)
            ) {
                Text(dayName(d, i).uppercase(), color = if (sel) p.accent else p.ink, fontWeight = FontWeight.Bold, fontSize = 15.sp, fontFamily = Display, letterSpacing = .4.sp)
                Text(dayDate(d), color = p.muted, fontSize = 12.sp, fontFamily = Condensed)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    state.sites.take(8).forEach { s ->
                        Box(Modifier.size(7.dp).clip(CircleShape).background(p.dot(state.bestOfDay(s, d))))
                    }
                }
            }
        }
    }
}

@Composable
fun Legend() {
    val p = LocalPalette.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            listOf(Status.GO to "Favorabil", Status.MAYBE to "Marginal", Status.NO to "Nu", Status.CALM to "Calm", Status.NIGHT to "Noapte").forEach { (s, t) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(p.dot(s)))
                    Text(t, color = p.muted, fontSize = 12.5.sp, fontFamily = Condensed)
                }
            }
        }
        Text("Pictograma: cerul și tipul de nori · săgeata: încotro bate vântul · cifra mare: vânt la 10 m, mică: rafale (m/s)",
            color = p.muted, fontSize = 12.5.sp, fontFamily = Condensed, modifier = Modifier.padding(top = 4.dp))
        Disclaimer(Modifier.padding(top = 8.dp))
    }
}

/** SIG-07: verdictul e o prognoză de model, nu o garanție. */
@Composable
fun Disclaimer(modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Text("Prognoză de model, nu o garanție. Decizia îți aparține, la decolare.", color = p.muted, fontSize = 12.5.sp, fontFamily = Condensed,
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(p.sunk).padding(horizontal = 10.dp, vertical = 7.dp))
}

@Composable
fun EmptyBoard(text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    val p = LocalPalette.current
    Column(modifier.fillMaxSize().padding(16.dp)) {
        Text(text, color = p.muted)
        action?.let { Spacer(Modifier.height(10.dp)); it() }
    }
}
