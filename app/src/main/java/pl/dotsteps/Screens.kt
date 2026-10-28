package pl.dotsteps

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val DAY = arrayOf("PN", "WT", "SR", "CZ", "PT", "SO", "ND")
private val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM")

private fun f1(x: Double) = String.format(Locale.US, "%.1f", x)
private fun fmtDate(d: LocalDate) = d.format(DATE_FMT)

@Composable
private fun shapeOf() = if (LocalPal.current.round) RoundedCornerShape(22.dp) else RectangleShape

// ================================================================ ROOT

@Composable
fun MainScreen() {
    var tab by remember { mutableIntStateOf(0) }
    var calib by remember { mutableStateOf(false) }
    BackHandler(enabled = calib) { calib = false }
    LaunchedEffect(Unit) {
        while (true) {
            StepStore.refresh()   // przejście przez północ
            delay(30_000)
        }
    }
    Column(Modifier.fillMaxSize()) {
        Header()
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (calib) {
                CalibrationScreen { calib = false }
            } else {
                when (tab) {
                    0 -> TodayScreen()
                    1 -> WeekScreen()
                    2 -> StatsScreen()
                    else -> SettingsScreen { calib = true }
                }
            }
        }
        NavBar(tab) { tab = it; calib = false }
    }
}

@Composable
private fun Header() {
    val p = LocalPal.current
    val ctx = LocalContext.current
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(p.accent, CircleShape))
            Spacer(Modifier.size(10.dp))
            DotText("DOT STEPS", 2.6f)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            DotText(fmtDate(LocalDate.now()), 2.6f, color = p.sub)
            Spacer(Modifier.size(14.dp))
            // zapisz wynik i ubij wszystko (usługa + proces)
            Box(
                Modifier.size(48.dp)
                    .background(p.accent, if (p.round) CircleShape else RectangleShape)
                    .tap { Shutdown.quit(ctx) },
                contentAlignment = Alignment.Center,
            ) { DotText("X", 2.6f, color = p.onAccent) }
        }
    }
}

@Composable
private fun NavBar(selected: Int, onSelect: (Int) -> Unit) {
    val items = listOf("DZIS", "TYDZ", "STATY", "OPCJE")
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
fun ToggleBox(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
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
        BarChart(
            values = days.map { it.second },
            maxV = maxV,
            labels = days.map { DAY[it.first.dayOfWeek.value - 1] },
            labelCell = 2f,
            gap = 6.dp,
            height = 220.dp,
        )
        Spacer(Modifier.height(28.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile("SUMA", total.toString(), Modifier.weight(1f))
            Tile("SREDNIA", avg.toString(), Modifier.weight(1f))
            Tile("REKORD", best.toString(), Modifier.weight(1f))
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** Wspólny wykres słupkowy z kropek. Ostatni słupek jest wyróżniony akcentem. */
@Composable
private fun BarChart(
    values: List<Int>,
    maxV: Int,
    labels: List<String>?,
    labelCell: Float,
    gap: Dp,
    height: Dp,
) {
    val p = LocalPal.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
        values.forEachIndexed { i, v ->
            val last = i == values.lastIndex
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                DotColumn(v.toFloat() / maxOf(maxV, 1), last, Modifier.fillMaxWidth().height(height))
                if (labels != null) {
                    Spacer(Modifier.height(10.dp))
                    DotText(labels[i], labelCell, color = if (last) p.fg else p.sub)
                }
            }
        }
    }
}

// ================================================================ STATYSTYKI

@Composable
fun StatsScreen() {
    val p = LocalPal.current
    val all by StepStore.all.collectAsState()
    val s by StepStore.settings.collectAsState()
    var range by remember { mutableIntStateOf(0) }

    val n = when (range) { 0 -> 7; 1 -> 30; else -> 365 }
    val list = remember(all, n) { Stats.lastDays(all, n) }
    val st = remember(all, n, s.goal) { Stats.summary(all, list, s.goal) }
    val streak = remember(all, s.goal) { Stats.streak(all, s.goal) }

    val km = st.total * s.strideCm / 100000.0
    val kcal = km * s.weightKg * 0.55
    val allTotal = all.values.sum()
    val allKm = allTotal * s.strideCm / 100000.0

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(0.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ToggleBox("7 DNI", range == 0, Modifier.weight(1f).height(48.dp)) { range = 0 }
            ToggleBox("30 DNI", range == 1, Modifier.weight(1f).height(48.dp)) { range = 1 }
            ToggleBox("ROK", range == 2, Modifier.weight(1f).height(48.dp)) { range = 2 }
        }

        when (range) {
            0 -> BarChart(
                values = list.map { it.second },
                maxV = maxOf(s.goal, list.maxOfOrNull { it.second } ?: 0),
                labels = list.map { DAY[it.first.dayOfWeek.value - 1] },
                labelCell = 2f, gap = 6.dp, height = 180.dp,
            )
            1 -> {
                BarChart(
                    values = list.map { it.second },
                    maxV = maxOf(s.goal, list.maxOfOrNull { it.second } ?: 0),
                    labels = null, labelCell = 2f, gap = 2.dp, height = 180.dp,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    DotText(fmtDate(list.first().first), 1.8f, color = p.sub)
                    DotText(fmtDate(list.last().first), 1.8f, color = p.sub)
                }
            }
            else -> {
                val months = remember(all) { Stats.months(all, 12) }
                BarChart(
                    values = months.map { it.second },
                    maxV = maxOf(1, months.maxOfOrNull { it.second } ?: 0),
                    labels = months.map { String.format(Locale.US, "%02d", it.first.monthValue) },
                    labelCell = 1.6f, gap = 4.dp, height = 180.dp,
                )
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile("SUMA", st.total.toString(), Modifier.weight(1f))
            Tile("SREDNIA", st.avg.toString(), Modifier.weight(1f))
            Tile("REKORD", st.best.toString(), Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile("KM", f1(km), Modifier.weight(1f))
            Tile("KCAL", kcal.roundToInt().toString(), Modifier.weight(1f))
            Tile("DNI CEL", st.goalDays.toString(), Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile("SERIA", streak.toString(), Modifier.weight(1f))
            Tile("AKTYWNE", st.activeDays.toString(), Modifier.weight(1f))
            Tile("REK DZIEN", st.bestDate?.let { fmtDate(it) } ?: "-", Modifier.weight(1f))
        }

        DotText("OD POCZATKU", 2f, color = p.sub)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile("RAZEM", allTotal.toString(), Modifier.weight(1f))
            Tile("DNI", all.size.toString(), Modifier.weight(1f))
            Tile("KM", f1(allKm), Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
    }
}

// ================================================================ OPCJE

@Composable
fun SettingsScreen(onCalibrate: () -> Unit) {
    val p = LocalPal.current
    val ctx = LocalContext.current
    val s by StepStore.settings.collectAsState()
    var msg by remember { mutableStateOf("") }

    val exporter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            msg = try {
                val all = StepStore.all.value
                ctx.contentResolver.openOutputStream(uri)?.use {
                    it.write(StatsIO.toCsv(all, s).toByteArray())
                }
                "Zapisano ${all.size} dni."
            } catch (e: Exception) {
                "Błąd zapisu: ${e.message}"
            }
        }
    }
    val importer = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            msg = try {
                val text = ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
                val parsed = StatsIO.parse(text)
                val changed = StepStore.importDays(parsed)
                "Wczytano ${parsed.size} dni, zaktualizowano $changed."
            } catch (e: Exception) {
                "Błąd odczytu: ${e.message}"
            }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Spacer(Modifier.height(2.dp))

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            DotText("ZRODLO KROKOW", 2f, color = p.sub)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Calibration.NAMES.forEachIndexed { i, name ->
                    ToggleBox(name, s.source == i, Modifier.weight(1f).height(52.dp)) {
                        StepStore.update(s.copy(source = i))
                    }
                }
            }
            BasicText(
                when (s.source) {
                    0 -> "Surowy licznik sprzętowy. Najmniej baterii, ale liczy też fałszywe kroki."
                    1 -> "Czujnik kroków + filtr rytmu: krok liczy się dopiero po K równych krokach z rzędu. " +
                        "Odrzuca potrząsanie i wstrząsy, oszczędza baterię."
                    else -> "Własny algorytm na akcelerometrze + filtr rytmu. Dokładny po kalibracji, " +
                        "ale utrzymuje CPU w gotowości, więc zużywa wyraźnie więcej baterii."
                },
                style = TextStyle(color = p.sub, fontSize = 12.sp, lineHeight = 18.sp, fontFamily = FontFamily.Monospace),
            )
        }

        Stepper("KROKI DO POTWIERDZENIA", s.rhythmK.toString(),
            { StepStore.update(s.copy(rhythmK = (s.rhythmK - 1).coerceAtLeast(2))) },
            { StepStore.update(s.copy(rhythmK = (s.rhythmK + 1).coerceAtMost(10))) })

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ToggleBox("KALIBRACJA", true, Modifier.fillMaxWidth().height(56.dp), onCalibrate)
            BasicText(
                "Skale S/F/W: ${String.format(Locale.US, "%.2f / %.2f / %.2f", s.scales[0], s.scales[1], s.scales[2])}" +
                    "  próg: ${String.format(Locale.US, "%.1f", s.threshold)}  odstęp: ${s.minIntervalMs} ms" +
                    "  kalibracji: ${s.calibRuns}",
                style = TextStyle(color = p.sub, fontSize = 12.sp, lineHeight = 18.sp, fontFamily = FontFamily.Monospace),
            )
            if (s.calibRuns > 0) {
                ToggleBox("RESET KALIBRACJI", false, Modifier.fillMaxWidth().height(48.dp)) {
                    StepStore.update(
                        s.copy(scales = listOf(1f, 1f, 1f), threshold = 1.2f, minIntervalMs = 300, calibRuns = 0)
                    )
                }
            }
        }

        Stepper("CEL DZIENNY", s.goal.toString(),
            { StepStore.update(s.copy(goal = (s.goal - 500).coerceAtLeast(500))) },
            { StepStore.update(s.copy(goal = (s.goal + 500).coerceAtMost(100_000))) })

        Stepper("DLUGOSC KROKU CM", s.strideCm.toString(),
            { StepStore.update(s.copy(strideCm = (s.strideCm - 1).coerceAtLeast(30))) },
            { StepStore.update(s.copy(strideCm = (s.strideCm + 1).coerceAtMost(150))) })

        Stepper("WAGA KG", s.weightKg.toString(),
            { StepStore.update(s.copy(weightKg = (s.weightKg - 1).coerceAtLeast(30))) },
            { StepStore.update(s.copy(weightKg = (s.weightKg + 1).coerceAtMost(250))) })

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            DotText("MOTYW", 2f, color = p.sub)
            THEMES.withIndex().toList().chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { iv ->
                        ThemeBox(iv.value.first, iv.value.second, iv.index == s.theme,
                            Modifier.weight(1f).height(64.dp)) {
                            StepStore.update(s.copy(theme = iv.index))
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            DotText("DANE", 2f, color = p.sub)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ToggleBox("EKSPORT CSV", true, Modifier.weight(1f).height(56.dp)) {
                    exporter.launch("dotsteps-${LocalDate.now()}.csv")
                }
                ToggleBox("IMPORT CSV", true, Modifier.weight(1f).height(56.dp)) {
                    importer.launch(arrayOf("*/*"))
                }
            }
            if (msg.isNotEmpty()) {
                BasicText(msg, style = TextStyle(color = p.fg, fontSize = 12.sp, fontFamily = FontFamily.Monospace))
            }
        }

        BasicText(
            "Historia zapisuje się automatycznie po każdym odczycie czujnika. CSV (data, kroki, km, kcal) " +
                "to kopia zapasowa: przy imporcie dla każdej daty zostaje większa wartość. " +
                "Jeśli system usypia aplikację, wyłącz dla niej optymalizację baterii.",
            style = TextStyle(color = p.sub, fontSize = 12.sp, lineHeight = 18.sp, fontFamily = FontFamily.Monospace),
        )
        Spacer(Modifier.height(16.dp))
    }
}

/** Kafel motywu pokazuje podgląd w kolorach tego motywu. */
@Composable
private fun ThemeBox(name: String, pal: Pal, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val cur = LocalPal.current
    val shape = if (pal.round) RoundedCornerShape(20.dp) else RectangleShape
    Box(
        modifier
            .background(pal.bg, shape)
            .border(if (selected) 3.dp else 1.dp, if (selected) cur.fg else cur.sub, shape)
            .tap(onClick),
        contentAlignment = Alignment.Center,
    ) {
        DotText(name, 2f, color = pal.fg)
        Box(
            Modifier.align(Alignment.TopEnd).padding(8.dp).size(8.dp)
                .background(pal.accent, CircleShape)
        )
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
