package pl.dotsteps

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val DAY = arrayOf("PN", "WT", "SR", "CZ", "PT", "SO", "ND")

private fun f1(x: Double) = String.format(Locale.US, "%.1f", x)

@Composable
private fun shapeOf() = if (LocalPal.current.round) RoundedCornerShape(22.dp) else RectangleShape

// ================================================================ ROOT

@Composable
fun MainScreen() {
    var tab by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            StepStore.refresh()   // przejście przez północ
            delay(30_000)
        }
    }
    Column(Modifier.fillMaxSize()) {
        Header()
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                0 -> TodayScreen()
                1 -> WeekScreen()
                else -> SettingsScreen()
            }
        }
        NavBar(tab) { tab = it }
    }
}

@Composable
private fun Header() {
    val p = LocalPal.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(p.accent, CircleShape))
            Spacer(Modifier.size(10.dp))
            DotText("DOT STEPS", 2.6f)
        }
        DotText(LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM")), 2.6f, color = p.sub)
    }
}

@Composable
private fun NavBar(selected: Int, onSelect: (Int) -> Unit) {
    val items = listOf("DZIS", "TYDZIEN", "OPCJE")
    Row(
        Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEachIndexed { i, label ->
            ToggleBox(label, i == selected, Modifier.weight(1f).height(52.dp)) { onSelect(i) }
        }
    }
}

@Composable
private fun ToggleBox(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val p = LocalPal.current
    val shape = if (p.round) RoundedCornerShape(26.dp) else RectangleShape
    Box(
        modifier
            .background(if (selected) p.fg else p.bg, shape)
            .border(if (p.round) 1.dp else 2.dp, if (selected || !p.round) p.fg else p.dim, shape)
            .tap(onClick),
        contentAlignment = Alignment.Center,
    ) {
        DotText(label, 2f, color = if (selected) p.bg else p.fg)
    }
}

// ================================================================ DZIŚ

@Composable
fun TodayScreen() {
    val p = LocalPal.current
    val days by StepStore.days.collectAsState()
    val s by StepStore.settings.collectAsState()

    val steps = days.lastOrNull()?.second ?: 0
    val progress = if (s.goal > 0) steps.toFloat() / s.goal else 0f
    val km = steps * s.strideCm / 100000.0
    val kcal = km * s.weightKg * 0.55

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
            DotRing(progress, Modifier.fillMaxSize())
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val txt = steps.toString()
                val cols = txt.length * 6 - 1
                val cell = minOf(9f, 170f / cols)
                DotText(txt, cell, off = if (p.round) p.dim else null)
                Spacer(Modifier.height(14.dp))
                DotText("KROKI", 2.4f, color = p.sub)
                Spacer(Modifier.height(6.dp))
                DotText("CEL ${s.goal}", 2f, color = p.sub)
            }
        }
        Spacer(Modifier.height(20.dp))
        if (steps >= s.goal) {
            DotText("CEL OSIAGNIETY", 2.2f, color = p.accent)
            Spacer(Modifier.height(16.dp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile("KM", f1(km), Modifier.weight(1f))
            Tile("KCAL", kcal.roundToInt().toString(), Modifier.weight(1f))
            Tile("CEL", "${(progress * 100).roundToInt()}%", Modifier.weight(1f))
        }
        Spacer(Modifier.height(24.dp))
    }
}

// ================================================================ TYDZIEŃ

@Composable
fun WeekScreen() {
    val p = LocalPal.current
    val days by StepStore.days.collectAsState()
    val s by StepStore.settings.collectAsState()

    val maxV = maxOf(s.goal, days.maxOfOrNull { it.second } ?: 0, 1)
    val total = days.sumOf { it.second }
    val avg = if (days.isNotEmpty()) total / days.size else 0
    val best = days.maxOfOrNull { it.second } ?: 0

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        DotText("OSTATNIE 7 DNI", 2.4f, color = p.sub)
        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            days.forEachIndexed { idx, (d, v) ->
                val today = idx == days.lastIndex
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    DotColumn(v.toFloat() / maxV, today, Modifier.fillMaxWidth().height(220.dp))
                    Spacer(Modifier.height(10.dp))
                    DotText(DAY[d.dayOfWeek.value - 1], 2f, color = if (today) p.fg else p.sub)
                }
            }
        }
        Spacer(Modifier.height(28.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile("SUMA", total.toString(), Modifier.weight(1f))
            Tile("SREDNIA", avg.toString(), Modifier.weight(1f))
            Tile("REKORD", best.toString(), Modifier.weight(1f))
        }
        Spacer(Modifier.height(24.dp))
    }
}

// ================================================================ OPCJE

@Composable
fun SettingsScreen() {
    val p = LocalPal.current
    val s by StepStore.settings.collectAsState()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Spacer(Modifier.height(2.dp))

        Stepper("CEL DZIENNY", s.goal.toString(),
            { StepStore.update(s.copy(goal = (s.goal - 500).coerceAtLeast(500))) },
            { StepStore.update(s.copy(goal = (s.goal + 500).coerceAtMost(100_000))) })

        Stepper("DLUGOSC KROKU CM", s.strideCm.toString(),
            { StepStore.update(s.copy(strideCm = (s.strideCm - 1).coerceAtLeast(30))) },
            { StepStore.update(s.copy(strideCm = (s.strideCm + 1).coerceAtMost(150))) })

        Stepper("WAGA KG", s.weightKg.toString(),
            { StepStore.update(s.copy(weightKg = (s.weightKg - 1).coerceAtLeast(30))) },
            { StepStore.update(s.copy(weightKg = (s.weightKg + 1).coerceAtMost(250))) })

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DotText("MOTYW", 2f, color = p.sub)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ToggleBox("E-INK", s.eink, Modifier.weight(1f).height(56.dp)) {
                    StepStore.update(s.copy(eink = true))
                }
                ToggleBox("NOTHING", !s.eink, Modifier.weight(1f).height(56.dp)) {
                    StepStore.update(s.copy(eink = false))
                }
            }
        }

        BasicText(
            "Licznik korzysta ze sprzętowego czujnika kroków. Usługa w tle z cichym " +
                "powiadomieniem pozwala zapisywać kroki także po zamknięciu aplikacji. " +
                "Jeśli system usypia aplikację, wyłącz dla niej optymalizację baterii.",
            style = TextStyle(color = p.sub, fontSize = 12.sp, lineHeight = 18.sp, fontFamily = FontFamily.Monospace),
        )
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun Stepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    val p = LocalPal.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DotText(label, 2f, color = p.sub)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StepBtn("-", onMinus)
            Box(
                Modifier.weight(1f).height(56.dp)
                    .border(if (p.round) 1.dp else 2.dp, if (p.round) p.dim else p.fg, shapeOf()),
                contentAlignment = Alignment.Center,
            ) { DotText(value, 3.4f) }
            StepBtn("+", onPlus)
        }
    }
}

@Composable
private fun StepBtn(sym: String, onClick: () -> Unit) {
    val p = LocalPal.current
    val shape = if (p.round) CircleShape else RectangleShape
    Box(
        Modifier.size(56.dp).background(p.fg, shape).tap(onClick),
        contentAlignment = Alignment.Center,
    ) { DotText(sym, 3f, color = p.bg) }
}

// ================================================================ UPRAWNIENIA

@Composable
fun PermissionScreen(onRequest: () -> Unit) {
    val p = LocalPal.current
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DotText("KROKI", 6f)
        Spacer(Modifier.height(24.dp))
        BasicText(
            "Aby liczyć kroki, aplikacja potrzebuje uprawnienia do rozpoznawania aktywności fizycznej" +
                " oraz powiadomień (cicha ikona usługi w tle).",
            style = TextStyle(color = p.sub, fontSize = 13.sp, lineHeight = 19.sp, fontFamily = FontFamily.Monospace),
        )
        Spacer(Modifier.height(28.dp))
        ToggleBox("ZEZWOL", true, Modifier.fillMaxWidth().height(60.dp), onRequest)
    }
}
