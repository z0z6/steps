package pl.dotsteps

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.abs

class LongBuf {
    var a = LongArray(1024)
    var n = 0
    fun add(v: Long) { if (n == a.size) a = a.copyOf(n * 2); a[n++] = v }
    fun clear() { n = 0 }
}

class FloatBuf {
    var a = FloatArray(1024)
    var n = 0
    fun add(v: Float) { if (n == a.size) a = a.copyOf(n * 2); a[n++] = v }
    fun clear() { n = 0 }
}

/** Nagrywa surowe dane ze wszystkich trzech czujników na czas kalibracyjnego spaceru. */
class CalibRecorder(ctx: Context, private val s: Settings) : SensorEventListener {
    private val sm = ctx.applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val counter: Sensor? = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
    private val detector: Sensor? = sm.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
    private val accel: Sensor? = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    val hasCounter get() = counter != null
    val hasDetector get() = detector != null
    val hasAccel get() = accel != null

    val detT = LongBuf()
    val accT = LongBuf()
    val ax = FloatBuf()
    val ay = FloatBuf()
    val az = FloatBuf()

    private var cBase = -1L
    private var cLast = -1L
    private var liveFilt = 0
    private var liveAcc = 0
    private var filtR = RhythmFilter(s.rhythmK)
    private var accR = RhythmFilter(s.rhythmK)
    private val liveDet = StepDetector(s.threshold, s.minIntervalMs)
    private var accOn = false

    fun begin() {
        detT.clear(); accT.clear(); ax.clear(); ay.clear(); az.clear()
        cBase = -1L; cLast = -1L; liveFilt = 0; liveAcc = 0
        filtR = RhythmFilter(s.rhythmK)
        accR = RhythmFilter(s.rhythmK)
        accOn = accel != null
        counter?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST) }
        detector?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST) }
        accel?.let { sm.registerListener(this, it, 20_000) }
    }

    fun stopAcc() { accOn = false }

    fun release() { sm.unregisterListener(this) }

    fun rawCount(): Int = if (cBase < 0L || cLast < 0L) 0 else (cLast - cBase).toInt().coerceAtLeast(0)
    fun filtCount(): Int = liveFilt
    fun accCount(): Int = liveAcc

    override fun onSensorChanged(e: SensorEvent) {
        when (e.sensor.type) {
            Sensor.TYPE_STEP_COUNTER -> {
                val v = e.values[0].toLong()
                if (cBase < 0L) cBase = v
                cLast = v
            }
            Sensor.TYPE_STEP_DETECTOR -> {
                detT.add(e.timestamp)
                liveFilt += filtR.onStep(e.timestamp)
            }
            Sensor.TYPE_ACCELEROMETER -> if (accOn && accT.n < MAX_SAMPLES) {
                accT.add(e.timestamp); ax.add(e.values[0]); ay.add(e.values[1]); az.add(e.values[2])
                val st = liveDet.onSample(e.timestamp, e.values[0], e.values[1], e.values[2])
                if (st >= 0L) liveAcc += accR.onStep(st)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    companion object { const val MAX_SAMPLES = 20_000 }
}

class CalibResult(
    val actual: Int,
    val raw: Int,
    val filt: Int,
    val accDefault: Int,
    val accFit: Int,
    val fitThr: Float,
    val fitMin: Int,
    val scale0: Float?,
    val scale1: Float?,
    val scale2: Float?,
    val recommended: Int,
)

object Calibration {
    const val DEF_THR = 1.2f
    const val DEF_MIN = 300
    val NAMES = listOf("SPRZET", "FILTR", "WLASNY")

    fun replayFilter(t: LongArray, n: Int, k: Int): Int {
        val f = RhythmFilter(k)
        var c = 0
        for (i in 0 until n) c += f.onStep(t[i])
        return c
    }

    fun replayAccel(rec: CalibRecorder, thr: Float, minMs: Int, k: Int): Int {
        val d = StepDetector(thr, minMs)
        val f = RhythmFilter(k)
        val t = rec.accT.a; val x = rec.ax.a; val y = rec.ay.a; val z = rec.az.a
        var c = 0
        for (i in 0 until rec.accT.n) {
            val st = d.onSample(t[i], x[i], y[i], z[i])
            if (st >= 0L) c += f.onStep(st)
        }
        return c
    }

    /**
     * Siatka progów × minimalnych odstępów; wybieramy parametry o najmniejszym błędzie,
     * z środka najdłuższego "plateau" (stabilna okolica, nie przypadkowy ostry punkt).
     */
    fun fitAccel(rec: CalibRecorder, actual: Int, k: Int): Triple<Float, Int, Int> {
        val thrs = FloatArray(37) { 0.4f + it * 0.1f }
        val mins = intArrayOf(250, 300, 350, 400, 450)
        val err = Array(mins.size) { j ->
            IntArray(thrs.size) { i -> abs(replayAccel(rec, thrs[i], mins[j], k) - actual) }
        }
        val best = err.minOf { it.min() }
        var bestLen = 0
        var bj = 1
        var bc = 8
        for (j in mins.indices) {
            var i = 0
            while (i < thrs.size) {
                if (err[j][i] == best) {
                    var e = i
                    while (e + 1 < thrs.size && err[j][e + 1] == best) e++
                    val len = e - i + 1
                    if (len > bestLen) { bestLen = len; bj = j; bc = (i + e) / 2 }
                    i = e + 1
                } else i++
            }
        }
        val thr = thrs[bc]
        val mn = mins[bj]
        return Triple(thr, mn, replayAccel(rec, thr, mn, k))
    }

    private fun scale(count: Int, actual: Int, has: Boolean): Float? =
        if (has && count > 0 && actual > 0) (actual.toFloat() / count).coerceIn(0.5f, 2f) else null

    private fun err(c: Int, a: Int) = abs(c - a).toDouble() / maxOf(a, 1)

    fun analyze(rec: CalibRecorder, actual: Int, s: Settings): CalibResult {
        val hasAcc = rec.hasAccel && rec.accT.n > 100
        val hasDet = rec.hasDetector && rec.detT.n > 0
        val hasRaw = rec.hasCounter
        val raw = rec.rawCount()
        val filt = if (hasDet) replayFilter(rec.detT.a, rec.detT.n, s.rhythmK) else 0
        val accDef = if (hasAcc) replayAccel(rec, DEF_THR, DEF_MIN, s.rhythmK) else 0
        val fit = if (hasAcc) fitAccel(rec, actual, s.rhythmK) else Triple(s.threshold, s.minIntervalMs, 0)

        // Zalecenie: filtr rytmu zwykle najlepszy (kalibracja marszu nie widzi fałszywych kroków,
        // a filtr właśnie je odrzuca). Akcelerometr tylko gdy sprzęt jest daleko od prawdy.
        val rec1 = when {
            hasDet && err(filt, actual) <= 0.25 -> 1
            hasAcc -> 2
            hasDet -> 1
            else -> 0
        }
        return CalibResult(
            actual, raw, filt, accDef, fit.third, fit.first, fit.second,
            scale(raw, actual, hasRaw), scale(filt, actual, hasDet), scale(fit.third, actual, hasAcc),
            rec1,
        )
    }

    /** Nowe ustawienia: skale uśredniane z poprzednimi kalibracjami, progi z ostatniego dopasowania. */
    fun apply(s: Settings, r: CalibResult): Settings {
        val runs = s.calibRuns
        fun mix(old: Float, new: Float?): Float =
            if (new == null) old else ((old * runs + new) / (runs + 1)).coerceIn(0.5f, 2f)
        return s.copy(
            scales = listOf(mix(s.scales[0], r.scale0), mix(s.scales[1], r.scale1), mix(s.scales[2], r.scale2)),
            threshold = if (r.accFit > 0) r.fitThr else s.threshold,
            minIntervalMs = if (r.accFit > 0) r.fitMin else s.minIntervalMs,
            calibRuns = runs + 1,
            source = r.recommended,
        )
    }
}
