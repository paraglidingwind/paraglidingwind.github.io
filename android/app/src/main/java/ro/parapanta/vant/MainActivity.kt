package ro.parapanta.vant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import ro.parapanta.vant.model.Flight
import ro.parapanta.vant.ui.Board
import ro.parapanta.vant.ui.BookIcon
import ro.parapanta.vant.ui.LogScreen
import ro.parapanta.vant.ui.ZONE
import java.time.LocalDate
import ro.parapanta.vant.ui.CellSheet
import ro.parapanta.vant.ui.Condensed
import ro.parapanta.vant.ui.Display
import ro.parapanta.vant.ui.DayStrip
import ro.parapanta.vant.ui.EditScreen
import ro.parapanta.vant.ui.EmptyBoard
import ro.parapanta.vant.ui.Legend
import ro.parapanta.vant.ui.LocalPalette
import ro.parapanta.vant.ui.MainViewModel
import ro.parapanta.vant.ui.SiteSheet
import ro.parapanta.vant.ui.VantTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { VantTheme { App() } }
    }
}

private fun agoTxt(at: Long, now: Long): String {
    val m = ((now - at) / 60000).toInt()
    return when {
        m < 1 -> "acum"
        m < 60 -> "acum $m min"
        else -> SimpleDateFormat("EEE HH:mm", Locale.forLanguageTag("ro")).format(Date(at))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(vm: MainViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val p = LocalPalette.current
    var editing by rememberSaveable { mutableStateOf(false) }
    var logOpen by rememberSaveable { mutableStateOf(false) }
    var flightDraft by remember { mutableStateOf<Flight?>(null) }
    var cell by remember { mutableStateOf<Pair<Int, String>?>(null) }
    var siteSheet by remember { mutableStateOf<Int?>(null) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }

    // La revenirea în aplicație: reîmprospătează dacă prognoza e veche.
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner) {
        owner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            vm.refresh()
            while (true) { now = System.currentTimeMillis(); delay(60_000) }
        }
    }

    if (editing) {
        BackHandler { editing = false }
        EditScreen(vm, state) { editing = false }
        return
    }
    if (logOpen) {
        BackHandler { logOpen = false }
        LogScreen(vm, state, flightDraft, onDraftShown = { flightDraft = null }) { logOpen = false }
        return
    }

    Column(Modifier.fillMaxSize().background(p.bg).safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 10.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("VÂNT LA DECOLARE", color = p.ink, fontSize = 22.sp, fontWeight = FontWeight.Bold, fontFamily = Display, letterSpacing = .5.sp)
                Text(
                    when {
                        state.loading -> "Se descarcă prognoza…"
                        state.fc != null -> "Actualizat ${agoTxt(state.fc!!.at, now)} · Open-Meteo"
                        else -> "Fără prognoză încă"
                    },
                    color = p.muted, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = { vm.refresh(force = true) }) { Icon(Icons.Filled.Refresh, "Reîmprospătează prognoza", tint = p.ink) }
            IconButton(onClick = { logOpen = true }) { BookIcon(p.ink, Modifier.size(22.dp)) }
            IconButton(onClick = { editing = true }) { Icon(Icons.Filled.Edit, "Editează lista de situri", tint = p.ink) }
        }
        state.error?.let { e ->
            Text(
                "Nu am putut descărca prognoza. $e" + if (state.fc != null) " Arăt ultimele date salvate." else " Trage în jos pentru a încerca din nou.",
                color = p.no, fontSize = 13.5.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(p.noBg).padding(10.dp)
            )
        }
        PullToRefreshBox(isRefreshing = state.loading, onRefresh = { vm.refresh(force = true) }, modifier = Modifier.weight(1f)) {
            Column(Modifier.fillMaxSize()) {
                when {
                    state.sites.isEmpty() -> EmptyBoard("Lista e goală. Adaugă decolările la care zbori ca să le vezi vântul pe ore.") {
                        Button(onClick = { editing = true }) { Text("Adaugă situri") }
                    }
                    state.dates.isEmpty() -> EmptyBoard(if (state.loading) "Se descarcă prognoza pentru ${state.sites.size} situri…" else "Nu există încă date. Trage în jos pentru reîmprospătare.")
                    else -> {
                        DayStrip(state, vm::selectDay)
                        Board(state, onCell = { si, h -> cell = si to h }, onSite = { siteSheet = it }, modifier = Modifier.weight(1f, fill = false))
                        Legend()
                    }
                }
            }
        }
    }

    cell?.let { (si, h) -> CellSheet(state, si, h) { cell = null } }
    siteSheet?.let {
        SiteSheet(state, it, onDay = { d -> vm.selectDay(d); siteSheet = null },
            onFlew = { name ->
                siteSheet = null
                flightDraft = Flight("", LocalDate.now(ZONE).toString(), name, 0)
                logOpen = true
            }) { siteSheet = null }
    }
}
