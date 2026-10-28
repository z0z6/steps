package pl.dotsteps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt

private fun pct(c: Int, a: Int): String {
    if (a <= 0) return "-"
    val v = ((c - a) * 100f / a).roundToInt()
    return (if (v > 0) "+" else "") + v + "%"
}

private fun f2(x: Float?) = if (x == null) "-" else String.format(Locale.US, "%.2f", x)

@Composable
private fun Note(text: String) {
    val p = LocalPal.current
    BasicText(
        text,
        style = TextStyle(color = p.sub, fontSize = 12.sp, lineHeight = 18.sp, fontFamily = FontFamily.Monospace),
    )
}

/**
 * Fazy: 0 = start, 1 = nagrywanie spaceru, 2 = oczekiwanie na opóźnione dane czujnika,
 * 3 = wpisanie prawdziwej liczby kroków, 4 = wynik.
 */
@Composable
fun CalibrationScreen(onClose: () -> Unit) {
    val p = LocalPal.current
    val ctx = LocalContext.current
    val s by StepStore.settings.collectAsState()
    val rec = remember { CalibRecorder(ctx, StepStore.settings.value) }
    DisposableEffect(Unit) { onDispose { rec.release() } }
    val scope = rememberCoroutineScope()

    var phase by remember { mutableIntStateOf(0) }
    var liveRaw by remember { mutableIntStateOf(0) }
    var liveFilt by remember { mutableIntStateOf(0) }
    var liveAcc by remember { mutableIntStateOf(0) }
    var elapsed by remember { mutableIntStateOf(0) }
    var settle by remember { mutableIntStateOf(5) }
    var startMs by remember { mutableLongStateOf(0L) }
    var actual by remember { mutableIntStateOf(50) }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<CalibResult?>(null) }

    LaunchedEffect(phase) {
        if (phase == 1) {
            while (true) {
                liveRaw = rec.rawCount(); liveFilt = rec.filtCount(); liveAcc = rec.accCount()
                elapsed = ((System.currentTimeMillis() - startMs) / 1000).toInt()
                if (elapsed >= 180) { rec.stopAcc(); phase = 2; break }
                delay(500)
            }
        } else if (phase == 2) {
            for (i in 5 downTo 1) { settle = i; delay(1000) }
            rec.release()
            liveRaw = rec.rawCount(); liveFilt = rec.filtCount(); liveAcc = rec.accCount()
            actual = maxOf(liveRaw, liveFilt, 1).coerceAtMost(5000)
            phase = 3
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(0.dp))
        DotText("KALIBRACJA", 3f)

        when (phase) {
            0 -> {
                Note(
                    "1. Trzymaj telefon tak, jak go zwykle nosisz (kieszeń, ręka, torba).\n" +
                        "2. Naciśnij START i idź równym tempem, najlepiej 50 kroków – licz je dokładnie.\n" +
                        "3. Naciśnij STOP, poczekaj 5 s i wpisz, ile kroków naprawdę zrobiłeś.\n\n" +
                        "Aplikacja nagra surowe dane trzech metod i dobierze mnożniki oraz progi. " +
                        "Powtórz w 2–3 sytuacjach (tempo, sposób noszenia) – wyniki się uśredniają."
                )
                val missing = buildList {
                    if (!rec.hasCounter) add("licznik")
                    if (!rec.hasDetector) add("detektor kroków")
                    if (!rec.hasAccel) add("akcelerometr")
                }
                if (missing.isNotEmpty()) Note("Brak czujników: ${missing.joinToString(", ")}.")
                ToggleBox("START", true, Modifier.fillMaxWidth().height(60.dp)) {
                    rec.begin()
                    startMs = System.currentTimeMillis()
                    elapsed = 0
                    liveRaw = 0; liveFilt = 0; liveAcc = 0
                    phase = 1
                }
            }
            1 -> {
                DotText("IDZ ${elapsed}S", 2.6f, color = p.accent)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Tile("SPRZET", liveRaw.toString(), Modifier.weight(1f))
                    Tile("FILTR", liveFilt.toString(), Modifier.weight(1f))
                    Tile("WLASNY", liveAcc.toString(), Modifier.weight(1f))
                }
                Note("Liczniki pokazują na żywo, co naliczyłaby każda metoda. Maks. 3 minuty.")
                ToggleBox("STOP", true, Modifier.fillMaxWidth().height(60.dp)) {
                    rec.stopAcc()
                    phase = 2
                }
            }
            2 -> {
                DotText("CZEKAM $settle", 3f, color = p.accent)
                Note("Czujnik kroków potrafi dosłać ostatnie kroki z opóźnieniem. Nie ruszaj telefonem.")
            }
            3 -> {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Tile("SPRZET", liveRaw.toString(), Modifier.weight(1f))
                    Tile("FILTR", liveFilt.toString(), Modifier.weight(1f))
                    Tile("WLASNY", liveAcc.toString(), Modifier.weight(1f))
                }
                DotText("PRAWDZIWE KROKI", 2f, color = p.sub)
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ToggleBox("-10", true, Modifier.size(width = 52.dp, height = 52.dp)) { actual = (actual - 10).coerceAtLeast(1) }
                    ToggleBox("-1", true, Modifier.size(width = 52.dp, height = 52.dp)) { actual = (actual - 1).coerceAtLeast(1) }
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { DotText(actual.toString(), 3.4f) }
                    ToggleBox("+1", true, Modifier.size(width = 52.dp, height = 52.dp)) { actual = (actual + 1).coerceAtMost(5000) }
                    ToggleBox("+10", true, Modifier.size(width = 52.dp, height = 52.dp)) { actual = (actual + 10).coerceAtMost(5000) }
                }
                if (busy) {
                    DotText("LICZE...", 2.6f, color = p.accent)
                } else {
                    ToggleBox("LICZ", true, Modifier.fillMaxWidth().height(60.dp)) {
                        busy = true
                        val a = actual
                        val st = StepStore.settings.value
                        scope.launch {
                            val r = withContext(Dispatchers.Default) { Calibration.analyze(rec, a, st) }
                            result = r
                            busy = false
                            phase = 4
                        }
                    }
                }
            }
            else -> {
                val r = result
                if (r != null) {
                    DotText("PRAWDA ${r.actual}", 2.6f)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Tile("SPRZET", r.raw.toString(), Modifier.weight(1f))
                        Tile("FILTR", r.filt.toString(), Modifier.weight(1f))
                        Tile("WLASNY", r.accDefault.toString(), Modifier.weight(1f))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Tile("BLAD", pct(r.raw, r.actual), Modifier.weight(1f))
                        Tile("BLAD", pct(r.filt, r.actual), Modifier.weight(1f))
                        Tile("BLAD", pct(r.accDefault, r.actual), Modifier.weight(1f))
                    }
                    DotText("PO KALIBRACJI", 2f, color = p.sub)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Tile("SKALA S", f2(r.scale0), Modifier.weight(1f))
                        Tile("SKALA F", f2(r.scale1), Modifier.weight(1f))
                        Tile("SKALA W", f2(r.scale2), Modifier.weight(1f))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Tile("PROG", String.format(Locale.US, "%.1f", r.fitThr), Modifier.weight(1f))
                        Tile("ODSTEP", r.fitMin.toString(), Modifier.weight(1f))
                        Tile("ZALEC.", Calibration.NAMES[r.recommended], Modifier.weight(1f))
                    }
                    Note(
                        "WLASNY w tabeli = domyślne progi, przed dopasowaniem. ZASTOSUJ zapisze skale " +
                            "(uśrednione z poprzednimi kalibracjami), progi akcelerometru i przełączy " +
                            "źródło na zalecane. FILTR odrzuca fałszywe kroki (potrząsanie, wstrząsy), " +
                            "czego spacer kalibracyjny nie mierzy – dlatego zwykle to najlepszy wybór."
                    )
                    ToggleBox("ZASTOSUJ", true, Modifier.fillMaxWidth().height(60.dp)) {
                        StepStore.update(Calibration.apply(s, r))
                        onClose()
                    }
                    ToggleBox("POWTORZ", false, Modifier.fillMaxWidth().height(52.dp)) {
                        result = null
                        phase = 0
                    }
                }
            }
        }

        if (phase != 1 && phase != 2) {
            ToggleBox("WROC", false, Modifier.fillMaxWidth().height(52.dp)) { onClose() }
        }
        Spacer(Modifier.height(16.dp))
    }
}
