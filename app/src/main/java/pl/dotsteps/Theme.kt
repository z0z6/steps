package pl.dotsteps

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** bg = tło, fg = główny kolor, sub = napisy drugorzędne, dim = zgaszone kropki, accent = akcent */
data class Pal(
    val bg: Color,
    val fg: Color,
    val sub: Color,
    val dim: Color,
    val accent: Color,
    val round: Boolean,
)

/** Papier + czarny tusz, zero kolorów, zero animacji, ostre krawędzie. */
val EInkPal = Pal(
    bg = Color(0xFFF2F1EC),
    fg = Color(0xFF000000),
    sub = Color(0xFF3C3C3C),
    dim = Color(0xFFA8A8A8),
    accent = Color(0xFF000000),
    round = false,
)

/** Czerń OLED, biała matryca kropek, czerwony akcent, zaokrąglone kafle. */
val NothingPal = Pal(
    bg = Color(0xFF000000),
    fg = Color(0xFFFFFFFF),
    sub = Color(0xFF9A9A9A),
    dim = Color(0xFF333333),
    accent = Color(0xFFD71921),
    round = true,
)

val LocalPal = staticCompositionLocalOf { EInkPal }
