package ro.parapanta.vant.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import ro.parapanta.vant.R
import androidx.compose.ui.text.font.FontWeight
import ro.parapanta.vant.model.Status

/** Aceleași culori ca în pagina web. */
@Immutable
data class Palette(
    val bg: Color, val surface: Color, val sunk: Color, val ink: Color, val muted: Color, val line: Color,
    val accent: Color, val accentInk: Color,
    val go: Color, val goBg: Color, val maybe: Color, val maybeBg: Color,
    val no: Color, val noBg: Color, val calm: Color, val calmBg: Color,
    val sun: Color, val water: Color, val sky: Color,
) {
    fun fg(s: Status) = when (s) { Status.GO -> go; Status.MAYBE -> maybe; Status.NO -> no; Status.CALM -> calm; Status.NA -> muted }
    fun bgOf(s: Status) = when (s) { Status.GO -> goBg; Status.MAYBE -> maybeBg; Status.NO -> noBg; Status.CALM -> calmBg; Status.NA -> surface }
    fun dot(s: Status) = when (s) { Status.NA -> line; else -> fg(s) }
}

private val Light = Palette(
    bg = Color(0xFFF2F5F7), surface = Color(0xFFFFFFFF), sunk = Color(0xFFE8EDF1), ink = Color(0xFF13212B),
    muted = Color(0xFF5A6A76), line = Color(0xFFD5DDE3), accent = Color(0xFFD4570C), accentInk = Color.White,
    go = Color(0xFF17804A), goBg = Color(0xFFD9F0E2), maybe = Color(0xFF9A6A0E), maybeBg = Color(0xFFF8ECC9),
    no = Color(0xFFB8322B), noBg = Color(0xFFF7DCD9), calm = Color(0xFF5F6F7B), calmBg = Color(0xFFE6ECF0),
    sun = Color(0xFFD99A06), water = Color(0xFF2A76CF), sky = Color(0xFF4F5F6B),
)
private val Dark = Palette(
    bg = Color(0xFF0D151B), surface = Color(0xFF152029), sunk = Color(0xFF1B2832), ink = Color(0xFFE4EBF0),
    muted = Color(0xFF93A3AE), line = Color(0xFF26343E), accent = Color(0xFFFF8A3D), accentInk = Color(0xFF1A0D04),
    go = Color(0xFF5FD394), goBg = Color(0xFF153526), maybe = Color(0xFFE8B852), maybeBg = Color(0xFF3A2F12),
    no = Color(0xFFF2776D), noBg = Color(0xFF3F1C1A), calm = Color(0xFF93A3AE), calmBg = Color(0xFF1E2B35),
    sun = Color(0xFFF2C14E), water = Color(0xFF6AA8F0), sky = Color(0xFFB4C2CC),
)

val LocalPalette = staticCompositionLocalOf { Light }

/** Aceleași fonturi ca pagina web: Barlow Semi Condensed pentru text, Barlow Condensed pentru titluri. */
val Condensed = FontFamily(
    Font(R.font.barlow_sc_regular, FontWeight.Normal),
    Font(R.font.barlow_sc_medium, FontWeight.Medium),
    Font(R.font.barlow_sc_semibold, FontWeight.SemiBold),
    Font(R.font.barlow_sc_bold, FontWeight.Bold),
)
val Display = FontFamily(Font(R.font.barlow_c_bold, FontWeight.Bold))

private val AppTypography = Typography().let { t ->
    fun TextStyle.c() = copy(fontFamily = Condensed)
    Typography(
        displayLarge = t.displayLarge.c(), displayMedium = t.displayMedium.c(), displaySmall = t.displaySmall.c(),
        headlineLarge = t.headlineLarge.c(), headlineMedium = t.headlineMedium.c(), headlineSmall = t.headlineSmall.c(),
        titleLarge = t.titleLarge.c(), titleMedium = t.titleMedium.c(), titleSmall = t.titleSmall.c(),
        bodyLarge = t.bodyLarge.c(), bodyMedium = t.bodyMedium.c(), bodySmall = t.bodySmall.c(),
        labelLarge = t.labelLarge.c().copy(fontWeight = FontWeight.SemiBold), labelMedium = t.labelMedium.c(), labelSmall = t.labelSmall.c(),
    )
}

@Composable
fun VantTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val p = if (dark) Dark else Light
    val scheme = if (dark) darkColorScheme(
        primary = p.accent, onPrimary = p.accentInk, background = p.bg, onBackground = p.ink,
        surface = p.surface, onSurface = p.ink, surfaceVariant = p.sunk, onSurfaceVariant = p.muted,
        outline = p.line, outlineVariant = p.line, surfaceContainerLow = p.surface, surfaceContainer = p.surface,
        surfaceContainerHigh = p.surface, error = p.no,
    ) else lightColorScheme(
        primary = p.accent, onPrimary = p.accentInk, background = p.bg, onBackground = p.ink,
        surface = p.surface, onSurface = p.ink, surfaceVariant = p.sunk, onSurfaceVariant = p.muted,
        outline = p.line, outlineVariant = p.line, surfaceContainerLow = p.surface, surfaceContainer = p.surface,
        surfaceContainerHigh = p.surface, error = p.no,
    )
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography, content = content)
    }
}
