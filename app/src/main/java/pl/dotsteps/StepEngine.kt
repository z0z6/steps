package pl.dotsteps

/** Zamienia zdarzenia z czujników na dodane kroki, zależnie od wybranego źródła. */
object StepEngine {
    private var rhythm = RhythmFilter(4)
    private var detector = StepDetector(1.2f, 300)
    private var kUsed = -1
    private var thrUsed = -1f
    private var minUsed = -1
    private val rem = DoubleArray(3)

    fun reset() {
        kUsed = -1; thrUsed = -1f; minUsed = -1
    }

    private fun sync(s: Settings) {
        if (s.rhythmK != kUsed) {
            rhythm = RhythmFilter(s.rhythmK)
            kUsed = s.rhythmK
        }
        if (s.threshold != thrUsed || s.minIntervalMs != minUsed) {
            detector = StepDetector(s.threshold, s.minIntervalMs)
            thrUsed = s.threshold
            minUsed = s.minIntervalMs
        }
    }

    /** Mnożnik kalibracji z zachowaniem części ułamkowej. */
    private fun scaled(mode: Int, n: Int, s: Settings): Int {
        if (n <= 0) return 0
        rem[mode] += n * s.scales[mode].toDouble()
        val whole = rem[mode].toInt()
        rem[mode] -= whole
        return whole
    }

    /** Źródło 0: surowy licznik sprzętowy. */
    fun onCounter(value: Long, tsNs: Long) {
        val s = StepStore.settings.value
        val d = StepStore.counterDelta(value, tsNs)
        StepStore.addSteps(scaled(0, d, s))
    }

    /** Źródło 1: sprzętowy detektor kroków + filtr rytmu. */
    fun onDetectorStep(tsNs: Long) {
        val s = StepStore.settings.value
        sync(s)
        StepStore.addSteps(scaled(1, rhythm.onStep(tsNs), s))
    }

    /** Źródło 2: własny detektor na akcelerometrze + filtr rytmu. */
    fun onAccel(tsNs: Long, x: Float, y: Float, z: Float) {
        val s = StepStore.settings.value
        sync(s)
        val t = detector.onSample(tsNs, x, y, z)
        if (t >= 0L) StepStore.addSteps(scaled(2, rhythm.onStep(t), s))
    }
}
