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
    val eink: Boolean = true,
)

/** Prosty magazyn danych: kroki per dzień + ustawienia (SharedPreferences). */
object StepStore {
    private lateinit var prefs: SharedPreferences

    private val _days = MutableStateFlow<List<Pair<LocalDate, Int>>>(emptyList())
    /** 7 ostatnich dni, od najstarszego do dzisiaj. */
    val days: StateFlow<List<Pair<LocalDate, Int>>> = _days.asStateFlow()

    private val _settings = MutableStateFlow(Settings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    @Synchronized
    fun init(ctx: Context) {
        if (::prefs.isInitialized) return
        prefs = ctx.applicationContext.getSharedPreferences("dotsteps", Context.MODE_PRIVATE)
        _settings.value = Settings(
            goal = prefs.getInt("goal", 8000),
            strideCm = prefs.getInt("stride", 75),
            weightKg = prefs.getInt("weight", 70),
            eink = prefs.getBoolean("eink", true),
        )
        refresh()
    }

    private fun steps(d: LocalDate): Int = prefs.getInt("d_$d", 0)

    fun todaySteps(): Int = steps(LocalDate.now())

    @Synchronized
    fun refresh() {
        val today = LocalDate.now()
        _days.value = (6 downTo 0).map { i ->
            val d = today.minusDays(i.toLong())
            d to steps(d)
        }
    }

    /** Wywoływane z czujnika TYPE_STEP_COUNTER (suma kroków od restartu telefonu). */
    @Synchronized
    fun onCounter(value: Long) {
        val last = prefs.getLong("last_counter", -1L)
        val delta = when {
            last < 0L -> 0L          // pierwszy odczyt – brak punktu odniesienia
            value >= last -> value - last
            else -> value            // telefon był zrestartowany – licznik wyzerowany
        }
        val ed = prefs.edit().putLong("last_counter", value)
        if (delta > 0L) {
            val key = "d_${LocalDate.now()}"
            ed.putInt(key, prefs.getInt(key, 0) + delta.toInt())
        }
        ed.commit() // synchronicznie – proces może zostać zabity zaraz po zapisie
        refresh()
    }

    /** Użytkownik świadomie zakończył aplikację – nie wznawiaj usługi po restarcie telefonu. */
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
            .putBoolean("eink", s.eink)
            .apply()
    }
}
