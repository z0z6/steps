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
)

/** Magazyn: kroki per dzień (cała historia) + ustawienia, w SharedPreferences. */
object StepStore {
    private lateinit var prefs: SharedPreferences
    private val map = HashMap<LocalDate, Int>()

    private val _all = MutableStateFlow<Map<LocalDate, Int>>(emptyMap())
    /** Cała historia: data -> kroki. */
    val all: StateFlow<Map<LocalDate, Int>> = _all.asStateFlow()

    private val _days = MutableStateFlow<List<Pair<LocalDate, Int>>>(emptyList())
    /** 7 ostatnich dni, od najstarszego do dzisiaj. */
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
            // migracja ze starej flagi "eink": true -> motyw 0, false -> motyw 2 (NOTHING)
            theme = prefs.getInt("theme", if (prefs.getBoolean("eink", true)) 0 else 2),
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

    /** Wywoływane z czujnika TYPE_STEP_COUNTER (suma kroków od restartu telefonu). */
    @Synchronized
    fun onCounter(value: Long) {
        val last = prefs.getLong("last_counter", -1L)
        val delta = when {
            last < 0L -> 0L
            value >= last -> value - last
            else -> value
        }
        val ed = prefs.edit().putLong("last_counter", value)
        if (delta > 0L) {
            val today = LocalDate.now()
            val n = (map[today] ?: 0) + delta.toInt()
            map[today] = n
            ed.putInt("d_$today", n)
        }
        ed.commit() // synchronicznie – proces może zostać zabity zaraz po zapisie
        refresh()
    }

    /** Scala historię z importu (dla każdej daty zostaje większa wartość). Zwraca liczbę zmienionych dni. */
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
            .apply()
    }
}
