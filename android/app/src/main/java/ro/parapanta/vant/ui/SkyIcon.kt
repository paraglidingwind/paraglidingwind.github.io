package ro.parapanta.vant.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser

private val PARSED: Map<String, List<Pair<IconPart, Path>>> by lazy {
    SKY_ICONS.mapValues { (_, parts) -> parts.map { it to PathParser().parsePathString(it.d).toPath() } }
}

/** Pictograma de cer, desenată din aceleași path-uri SVG ca pagina web (data/sky_icons.json). */
@Composable
fun SkyIcon(name: String, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val parts = PARSED[name] ?: return
    Canvas(modifier) {
        scale(size.minDimension / 24f, pivot = Offset.Zero) {
            parts.forEach { (part, path) ->
                val c = when (part.role) { "sun" -> p.sun; "water" -> p.water; else -> p.sky }
                drawPath(path, c, style = if (part.fill) Fill else Stroke(width = 1.7f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}
