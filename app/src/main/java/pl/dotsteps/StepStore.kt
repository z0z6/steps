package pl.dotsteps

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate

data class Settings(
    val goal: Int = 8000,
    val strideCm: Int = 75,
    val weightKg: Int = 70,
    val theme: Int = 0,
    /** 0 = surowy licznik, 1 = detektor + filtr rytmu, 2 = własny akcelerometr + filtr rytmu */
    val source: Int = 1,
    /** ile kroków z rzędu w równym rytmie potwierdza marsz */
    val rhythmK: Int = 4,
    val threshold: Float = 1.2f,
    val minIntervalMs: Int = 300,
    /** mnożniki kalibracji dla źródeł 0, 1, 2 */
    val scales: List<Float> = listOf(1f, 1f, 1f),
    val calibRuns: Int = 0,
)

/** Magazyn: kroki per dzień (cała historia) + ustawienia, w SharedPreferences. */
object StepStore {
    private lateinit var prefs: SharedPreferences
    private val map = HashMap<LocalDate, Int>()

    private val _all = MutableStateFlow<Map<LocalDate, Int>>(emptyMap())
    val all: StateFlow<Map<LocalDate, Int>> = _all.asStateFlow()

    private val _days = MutableStateFlow<List<Pair<LocalDate, Int>>>(emptyList())
    val days: StateFlow<List<Pair<LocalDate, Int>>> = _days.asStateFlow()

    private val _settings = MutableStateFlow(Settings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    @Synchronized
    fun init(ctx: Context) {
        if (::prefs.isInitialized) return
        prefs = ctx.applicationContext.getSharedPreferences("dotsteps", Context.MODE_PRIVATE)
        for ((k, v) in prefs.all) {
            if (k.startsWith("d_") && v is Int) {
                try { map[LocalDate.parse(k.substring(2))] = v } catch (_: Exception) { }
            }
        }
        _settings.value = Settings(
            goal = prefs.getInt("goal", 8000),
            strideCm = prefs.getInt("stride", 75),
            weightKg = prefs.getInt("weight", 70),
            theme = prefs.getInt("theme", if (prefs.getBoolean("eink", true)) 0 else 2),
            source = prefs.getInt("source", 1),
            rhythmK = prefs.getInt("rhythm_k", 4),
            threshold = prefs.getFloat("threshold", 1.2f),
            minIntervalMs = prefs.getInt("min_interval", 300),
            scales = listOf(
                prefs.getFloat("scale_0", 1f),
                prefs.getFloat("scale_1", 1f),
                prefs.getFloat("scale_2", 1f),
            ),
            calibRuns = prefs.getInt("calib_runs", 0),
        )
        refresh()
    }

    fun todaySteps(): Int = synchronized(this) { map[LocalDate.now()] ?: 0 }

    @Synchronized
    fun refresh() {
        val today = LocalDate.now()
        _days.value = (6 downTo 0).map { i ->
            val d = today.minusDays(i.toLong())
            d to (map[d] ?: 0)
        }
        _all.value = HashMap(map)
    }

    /** Dodaje kroki do dzisiejszego dnia. */
    @Synchronized
    fun addSteps(n: Int) {
        if (n <= 0) return
        val today = LocalDate.now()
        val v = (map[today] ?: 0) + n
        map[today] = v
        prefs.edit().putInt("d_$today", v).apply()
        refresh()
    }

    /**
     * Różnica surowego licznika od ostatniego odczytu. Przyrost jest ograniczony do realnego tempa
     * (3,5 kroku/s od poprzedniego odczytu), co ucina serie fałszywych kroków.
     */
    @Synchronized
    fun counterDelta(value: Long, tsNs: Long): Int {
        val last = prefs.getLong("last_counter", -1L)
        val lastTs = prefs.getLong("last_ts", 0L)
        var delta = when {
            last < 0L -> 0L
            value >= last -> value - last
            else -> value // restart telefonu
        }
        if (last >= 0L && value >= last && lastTs > 0L && tsNs > lastTs) {
            val cap = ((tsNs - lastTs) / 1e9 * 3.5).toLong() + 3L
            if (delta > cap) delta = cap
        }
        prefs.edit().putLong("last_counter", value).putLong("last_ts", tsNs).apply()
        return delta.toInt()
    }

    /** Przy zmianie źródła licznik sprzętowy nie ma punktu odniesienia – zerujemy bazę. */
    @Synchronized
    fun noteSource(src: Int) {
        if (prefs.getInt("src_active", 0) != src) {
            prefs.edit().putInt("src_active", src).putLong("last_counter", -1L).apply()
        }
    }

    /** Wymusza synchroniczny zapis wszystkiego na dysk (przed zabiciem procesu). */
    @Synchronized
    fun sync() {
        prefs.edit().putLong("synced", System.currentTimeMillis()).commit()
    }

    @Synchronized
    fun importDays(incoming: Map<LocalDate, Int>): Int {
        val ed = prefs.edit()
        var changed = 0
        for ((d, v) in incoming) {
            if (v > (map[d] ?: 0)) {
                map[d] = v
                ed.putInt("d_$d", v)
                changed++
            }
        }
        ed.commit()
        refresh()
        return changed
    }

    fun isStopped(): Boolean = prefs.getBoolean("stopped", false)

    fun setStopped(v: Boolean) {
        prefs.edit().putBoolean("stopped", v).commit()
    }

    @Synchronized
    fun update(s: Settings) {
        _settings.value = s
        prefs.edit()
            .putInt("goal", s.goal)
            .putInt("stride", s.strideCm)
            .putInt("weight", s.weightKg)
            .putInt("theme", s.theme)
            .putInt("source", s.source)
            .putInt("rhythm_k", s.rhythmK)
            .putFloat("threshold", s.threshold)
            .putInt("min_interval", s.minIntervalMs)
            .putFloat("scale_0", s.scales[0])
            .putFloat("scale_1", s.scales[1])
            .putFloat("scale_2", s.scales[2])
            .putInt("calib_runs", s.calibRuns)
            .apply()
    }
}
