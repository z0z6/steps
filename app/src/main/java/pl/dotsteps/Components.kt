package pl.dotsteps

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// ---------------------------------------------------------------- font 5x7

private fun g(s: String): List<String> = s.split(" ")

private val GLYPHS: Map<Char, List<String>> = mapOf(
    ' ' to g("00000 00000 00000 00000 00000 00000 00000"),
    '0' to g("01110 10001 10011 10101 11001 10001 01110"),
    '1' to g("00100 01100 00100 00100 00100 00100 01110"),
    '2' to g("01110 10001 00001 00010 00100 01000 11111"),
    '3' to g("11110 00001 00001 01110 00001 00001 11110"),
    '4' to g("00010 00110 01010 10010 11111 00010 00010"),
    '5' to g("11111 10000 11110 00001 00001 10001 01110"),
    '6' to g("00110 01000 10000 11110 10001 10001 01110"),
    '7' to g("11111 00001 00010 00100 01000 01000 01000"),
    '8' to g("01110 10001 10001 01110 10001 10001 01110"),
    '9' to g("01110 10001 10001 01111 00001 00010 01100"),
    'A' to g("01110 10001 10001 11111 10001 10001 10001"),
    'B' to g("11110 10001 10001 11110 10001 10001 11110"),
    'C' to g("01110 10001 10000 10000 10000 10001 01110"),
    'D' to g("11110 10001 10001 10001 10001 10001 11110"),
    'E' to g("11111 10000 10000 11110 10000 10000 11111"),
    'F' to g("11111 10000 10000 11110 10000 10000 10000"),
    'G' to g("01110 10001 10000 10111 10001 10001 01111"),
    'H' to g("10001 10001 10001 11111 10001 10001 10001"),
    'I' to g("01110 00100 00100 00100 00100 00100 01110"),
    'J' to g("00111 00010 00010 00010 00010 10010 01100"),
    'K' to g("10001 10010 10100 11000 10100 10010 10001"),
    'L' to g("10000 10000 10000 10000 10000 10000 11111"),
    'M' to g("10001 11011 10101 10101 10001 10001 10001"),
    'N' to g("10001 11001 10101 10011 10001 10001 10001"),
    'O' to g("01110 10001 10001 10001 10001 10001 01110"),
    'P' to g("11110 10001 10001 11110 10000 10000 10000"),
    'Q' to g("01110 10001 10001 10001 10101 10010 01101"),
    'R' to g("11110 10001 10001 11110 10100 10010 10001"),
    'S' to g("01111 10000 10000 01110 00001 00001 11110"),
    'T' to g("11111 00100 00100 00100 00100 00100 00100"),
    'U' to g("10001 10001 10001 10001 10001 10001 01110"),
    'V' to g("10001 10001 10001 10001 10001 01010 00100"),
    'W' to g("10001 10001 10001 10101 10101 10101 01010"),
    'X' to g("10001 10001 01010 00100 01010 10001 10001"),
    'Y' to g("10001 10001 10001 01010 00100 00100 00100"),
    'Z' to g("11111 00001 00010 00100 01000 10000 11111"),
    '.' to g("00000 00000 00000 00000 00000 01100 01100"),
    ':' to g("00000 01100 01100 00000 01100 01100 00000"),
    '/' to g("00001 00010 00010 00100 01000 01000 10000"),
    '-' to g("00000 00000 00000 11111 00000 00000 00000"),
    '+' to g("00000 00100 00100 11111 00100 00100 00000"),
    '%' to g("11001 11010 00010 00100 01000 01011 10011"),
)

private val BLANK = GLYPHS.getValue(' ')

/** Tekst rysowany matrycą kropek 5x7. [cell] w dp = odstęp między kropkami. */
@Composable
fun DotText(
    text: String,
    cell: Float,
    modifier: Modifier = Modifier,
    color: Color = LocalPal.current.fg,
    off: Color? = null,
) {
    val t = text.uppercase()
    val cols = maxOf(1, t.length * 6 - 1)
    Canvas(modifier.size((cols * cell).dp, (7 * cell).dp)) {
        val c = cell.dp.toPx()
        t.forEachIndexed { i, ch ->
            val rows = GLYPHS[ch] ?: BLANK
            for (r in 0 until 7) {
                for (k in 0 until 5) {
                    val center = Offset((i * 6 + k + 0.5f) * c, (r + 0.5f) * c)
                    if (rows[r][k] == '1') {
                        drawCircle(color, c * 0.4f, center)
                    } else if (off != null) {
                        drawCircle(off, c * 0.1f, center)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- pierścień

@Composable
fun DotRing(progress: Float, modifier: Modifier = Modifier) {
    val p = LocalPal.current
    Canvas(modifier) {
        val n = 72
        val r = size.minDimension / 2f
        val dr = r * 0.036f
        val ringR = r - dr * 2.2f
        val center = Offset(size.width / 2f, size.height / 2f)
        val filled = (progress.coerceIn(0f, 1f) * n).roundToInt()
        for (i in 0 until n) {
            val a = Math.toRadians(-90.0 + i * 360.0 / n)
            val pos = Offset(
                center.x + (ringR * cos(a)).toFloat(),
                center.y + (ringR * sin(a)).toFloat(),
            )
            if (i < filled) {
                val head = p.round && i == filled - 1
                drawCircle(if (head) p.accent else p.fg, dr * 1.5f, pos)
            } else {
                drawCircle(p.dim, dr * 0.75f, pos)
            }
        }
        // wewnętrzny, delikatny pierścień z kropek (wzór Nothing)
        val m = 48
        val innerR = ringR * 0.80f
        for (i in 0 until m) {
            val a = Math.toRadians(-90.0 + i * 360.0 / m)
            val pos = Offset(
                center.x + (innerR * cos(a)).toFloat(),
                center.y + (innerR * sin(a)).toFloat(),
            )
            drawCircle(p.dim, dr * 0.45f, pos)
        }
    }
}

// ---------------------------------------------------------------- słupek z kropek

@Composable
fun DotColumn(frac: Float, highlight: Boolean, modifier: Modifier = Modifier) {
    val p = LocalPal.current
    Canvas(modifier) {
        val rows = 14
        val pitch = size.height / rows
        val r = minOf(pitch * 0.36f, size.width * 0.28f)
        var filled = (frac.coerceIn(0f, 1f) * rows).roundToInt()
        if (frac > 0f && filled == 0) filled = 1
        for (i in 0 until rows) {
            val y = size.height - (i + 0.5f) * pitch
            val on = i < filled
            drawCircle(
                color = if (on) (if (highlight) p.accent else p.fg) else p.dim,
                radius = if (on) r else r * 0.45f,
                center = Offset(size.width / 2f, y),
            )
        }
    }
}

// ---------------------------------------------------------------- klik bez ripple (e-ink)

fun Modifier.tap(onClick: () -> Unit): Modifier = composed {
    clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick,
    )
}

// ---------------------------------------------------------------- kafel

@Composable
fun Tile(label: String, value: String, modifier: Modifier = Modifier) {
    val p = LocalPal.current
    val shape = if (p.round) RoundedCornerShape(22.dp) else RectangleShape
    val cols = maxOf(1, value.length * 6 - 1)
    val cell = minOf(2.6f, 58f / cols)
    Column(
        modifier
            .border(if (p.round) 1.dp else 2.dp, if (p.round) p.dim else p.fg, shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        DotText(label, 2f, color = p.sub)
        DotText(value, cell)
    }
}
