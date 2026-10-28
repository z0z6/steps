package pl.dotsteps

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * bg = tło, fg = główny kolor, sub = napisy drugorzędne, dim = zgaszone kropki,
 * accent = akcent, onAccent = kolor tekstu na akcencie,
 * round = zaokrąglone kafle (styl Nothing), light = jasne tło (ikony pasków systemowych).
 */
data class Pal(
    val bg: Color,
    val fg: Color,
    val sub: Color,
    val dim: Color,
    val accent: Color,
    val onAccent: Color,
    val round: Boolean,
    val light: Boolean,
)

/** Papier + czarny tusz, ostre krawędzie, bez kolorów. */
val EInkPal = Pal(
    bg = Color(0xFFF2F1EC), fg = Color(0xFF000000), sub = Color(0xFF3C3C3C),
    dim = Color(0xFFA8A8A8), accent = Color(0xFF000000), onAccent = Color(0xFFF2F1EC),
    round = false, light = true,
)

/** Odwrócony e-ink: ciemny papier, jasny tusz. */
val EInkNightPal = Pal(
    bg = Color(0xFF0B0B0A), fg = Color(0xFFF2F1EC), sub = Color(0xFFBDBDB5),
    dim = Color(0xFF4A4A46), accent = Color(0xFFF2F1EC), onAccent = Color(0xFF0B0B0A),
    round = false, light = false,
)

/** Czerń OLED, biała matryca, czerwony akcent, zaokrąglone kafle. */
val NothingPal = Pal(
    bg = Color(0xFF000000), fg = Color(0xFFFFFFFF), sub = Color(0xFF9A9A9A),
    dim = Color(0xFF333333), accent = Color(0xFFD71921), onAccent = Color(0xFFFFFFFF),
    round = true, light = false,
)

/** Jasna wersja Nothing: biały korpus, czarne kropki, czerwony akcent. */
val SignalPal = Pal(
    bg = Color(0xFFF4F4F2), fg = Color(0xFF0A0A0A), sub = Color(0xFF6B6B6B),
    dim = Color(0xFFD2D2CE), accent = Color(0xFFD71921), onAccent = Color(0xFFFFFFFF),
    round = true, light = true,
)

/** Bursztynowy wyświetlacz punktowy na czerni. */
val AmberPal = Pal(
    bg = Color(0xFF000000), fg = Color(0xFFFFB000), sub = Color(0xFFA87400),
    dim = Color(0xFF3D2A00), accent = Color(0xFFFF4D00), onAccent = Color(0xFF000000),
    round = true, light = false,
)

/** Zielony fosfor na czerni, styl terminala. */
val TerminalPal = Pal(
    bg = Color(0xFF000000), fg = Color(0xFF33FF66), sub = Color(0xFF1FA043),
    dim = Color(0xFF0F3A1B), accent = Color(0xFFB8FF3C), onAccent = Color(0xFF000000),
    round = false, light = false,
)

/** Granat i cyjan z różowym akcentem. */
val LagunaPal = Pal(
    bg = Color(0xFF050B1A), fg = Color(0xFF7FE7FF), sub = Color(0xFF4A8CA8),
    dim = Color(0xFF14263D), accent = Color(0xFFFF3D81), onAccent = Color(0xFF050B1A),
    round = true, light = false,
)

/** Ciepły piasek, brąz i paląca pomarańcza – jasny, papierowy. */
val SandPal = Pal(
    bg = Color(0xFFE9DFC8), fg = Color(0xFF2B2118), sub = Color(0xFF6B5A44),
    dim = Color(0xFFC9BA9A), accent = Color(0xFFC2410C), onAccent = Color(0xFFFFFFFF),
    round = false, light = true,
)

val THEMES: List<Pair<String, Pal>> = listOf(
    "E-INK" to EInkPal,
    "E-INK NOC" to EInkNightPal,
    "NOTHING" to NothingPal,
    "SIGNAL" to SignalPal,
    "AMBER" to AmberPal,
    "TERMINAL" to TerminalPal,
    "LAGUNA" to LagunaPal,
    "PIASEK" to SandPal,
)

val LocalPal = staticCompositionLocalOf { EInkPal }
