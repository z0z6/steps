package pl.dotsteps

import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import kotlin.math.roundToInt

data class Summary(
    val total: Int,
    val avg: Int,
    val best: Int,
    val bestDate: LocalDate?,
    val goalDays: Int,
    val activeDays: Int,
)

object Stats {
    fun lastDays(all: Map<LocalDate, Int>, n: Int): List<Pair<LocalDate, Int>> {
        val today = LocalDate.now()
        return (n - 1 downTo 0).map { i ->
            val d = today.minusDays(i.toLong())
            d to (all[d] ?: 0)
        }
    }

    fun summary(all: Map<LocalDate, Int>, list: List<Pair<LocalDate, Int>>, goal: Int): Summary {
        val first = all.keys.minOrNull()
        // średnia liczona tylko od pierwszego zapisanego dnia
        val elapsed = if (first == null) 0 else list.count { !it.first.isBefore(first) }
        val total = list.sumOf { it.second }
        val bestE = list.maxByOrNull { it.second }
        val best = bestE?.second ?: 0
        return Summary(
            total = total,
            avg = if (elapsed > 0) total / elapsed else 0,
            best = best,
            bestDate = if (best > 0) bestE?.first else null,
            goalDays = list.count { it.second > 0 && it.second >= goal },
            activeDays = list.count { it.second > 0 },
        )
    }

    /** Seria dni z osiągniętym celem (jeśli dziś cel jeszcze niezrobiony – liczy od wczoraj). */
    fun streak(all: Map<LocalDate, Int>, goal: Int): Int {
        var d = LocalDate.now()
        if ((all[d] ?: 0) < goal) d = d.minusDays(1)
        var c = 0
        while ((all[d] ?: 0) >= goal && goal > 0) {
            c++
            d = d.minusDays(1)
        }
        return c
    }

    fun months(all: Map<LocalDate, Int>, n: Int): List<Pair<YearMonth, Int>> {
        val sums = HashMap<YearMonth, Int>()
        for ((d, v) in all) {
            val ym = YearMonth.from(d)
            sums[ym] = (sums[ym] ?: 0) + v
        }
        val cur = YearMonth.now()
        return (n - 1 downTo 0).map { i ->
            val m = cur.minusMonths(i.toLong())
            m to (sums[m] ?: 0)
        }
    }
}

/** Eksport / import historii w formacie CSV: date,steps,km,kcal */
object StatsIO {
    fun toCsv(all: Map<LocalDate, Int>, s: Settings): String {
        val sb = StringBuilder("date,steps,km,kcal\n")
        for ((d, v) in all.toSortedMap()) {
            val km = v * s.strideCm / 100000.0
            sb.append(d).append(',').append(v).append(',')
                .append(String.format(Locale.US, "%.2f", km)).append(',')
                .append((km * s.weightKg * 0.55).roundToInt()).append('\n')
        }
        return sb.toString()
    }

    /** Czyta pierwsze dwie kolumny (data ISO, kroki); nagłówek i błędne wiersze są pomijane. */
    fun parse(text: String): Map<LocalDate, Int> {
        val out = HashMap<LocalDate, Int>()
        for (line in text.lineSequence()) {
            val parts = line.trim().split(',', ';')
            if (parts.size < 2) continue
            val d = try { LocalDate.parse(parts[0].trim()) } catch (_: Exception) { continue }
            val v = parts[1].trim().toIntOrNull() ?: continue
            if (v in 0..300_000) out[d] = v
        }
        return out
    }
}
