package pl.dotsteps

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * Filtr rytmu: krok liczy się dopiero, gdy po sobie następuje [k] kroków w równych odstępach
 * (chód ma regularny rytm, potrząsanie telefonem, wstrząsy w aucie czy pojedyncze ruchy – nie).
 * Po potwierdzeniu marszu kolejne kroki liczą się od razu, dopóki odstępy mieszczą się w zakresie.
 * Czas bierzemy ze znaczników czasu zdarzeń (ns), więc batching czujnika nie psuje wyniku.
 */
class RhythmFilter(
    k: Int,
    private val minMs: Double = 250.0,
    private val maxMs: Double = 1500.0,
    private val tol: Double = 0.45,
) {
    private val k = maxOf(2, k)
    private var lastT = 0L
    private var lastDt = 0.0
    private var run = 0
    private var confirmed = false

    /** Zwraca, ile kroków doliczyć TERAZ (0, 1 albo k po potwierdzeniu). */
    fun onStep(tNs: Long): Int {
        if (lastT == 0L) return restart(tNs)
        val dt = (tNs - lastT) / 1_000_000.0
        if (dt < 0.0) return 0
        if (dt < minMs) return 0                 // dubel / szum – ignorujemy
        if (dt > maxMs) return restart(tNs)      // przerwa – nowy ciąg
        if (!confirmed && lastDt > 0.0 && abs(dt - lastDt) > tol * lastDt) {
            return restart(tNs)                  // nieregularny rytm przed potwierdzeniem
        }
        run++
        lastDt = dt
        lastT = tNs
        if (confirmed) return 1
        if (run >= k) {
            confirmed = true
            return run
        }
        return 0
    }

    private fun restart(tNs: Long): Int {
        lastT = tNs
        lastDt = 0.0
        run = 1
        confirmed = false
        return 0
    }
}

/**
 * Własny detektor kroków z akcelerometru: wartość dynamiczna = |a| − grawitacja (wolna średnia),
 * wygładzona filtrem 1. rzędu (~4 Hz), potem szukanie szczytów. Parametry kalibrowane:
 * [threshold] (m/s²) i [minIntervalMs]. Dodatkowo szczyt musi mieć ≥45% średniej wysokości
 * ostatnich kroków (odrzuca drobne wstrząsy między prawdziwymi krokami).
 */
class StepDetector(private val threshold: Float, minIntervalMs: Int) {
    private val minNs = minIntervalMs * 1_000_000L
    private var lastSampleT = 0L
    private var gravity = 9.81f
    private var s = 0f
    private var prev = 0f
    private var prev2 = 0f
    private var prevT = 0L
    private var lastStepT = 0L
    private var avgPeak = 0f

    /** Zwraca znacznik czasu wykrytego kroku (ns) albo -1. */
    fun onSample(tNs: Long, x: Float, y: Float, z: Float): Long {
        val m = sqrt(x * x + y * y + z * z)
        if (lastSampleT == 0L) {
            lastSampleT = tNs
            gravity = m
            prevT = tNs
            return -1L
        }
        val dt = ((tNs - lastSampleT) / 1e9f).coerceIn(0.001f, 0.2f)
        lastSampleT = tNs

        gravity += (m - gravity) * (1f - exp(-dt / 1.2f))
        val d = m - gravity
        s += (d - s) * (1f - exp(-dt / 0.04f))

        var result = -1L
        val peak = prev
        if (peak > prev2 && peak >= s && peak > threshold) {
            val gap = prevT - lastStepT
            if (gap >= minNs) {
                val fresh = lastStepT == 0L || gap > 2_000_000_000L
                if (fresh || peak >= 0.45f * avgPeak) {
                    avgPeak = if (fresh) peak else 0.8f * avgPeak + 0.2f * peak
                    lastStepT = prevT
                    result = prevT
                }
            }
        }
        prev2 = prev
        prev = s
        prevT = tNs
        return result
    }
}
