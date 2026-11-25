package dev.pocketagent.app.ui.settings

import dev.pocketagent.app.data.UsageDay
import java.time.LocalDate
import java.util.Locale

/** Sums of the usage rows the Settings > Usage screen shows. */
internal data class UsageTotals(val turns: Int, val tokensIn: Long, val tokensOut: Long) {
    val total: Long get() = tokensIn + tokensOut

    companion object { val ZERO = UsageTotals(0, 0L, 0L) }
}

internal data class DayTotal(val day: LocalDate, val totals: UsageTotals)

internal data class ModelTotal(val model: String, val totals: UsageTotals)

/** Pure functions over [UsageDay] rows (one row per day and model); no Android classes, tested on the JVM. */
internal object UsageMath {
    /** `yyyy-MM-dd` as the store writes it; null for anything else (a damaged row is ignored, never a crash). */
    fun parseDay(s: String): LocalDate? = try { LocalDate.parse(s) } catch (_: Exception) { null }

    private fun UsageTotals.plus(r: UsageDay) = UsageTotals(turns + r.turns.coerceAtLeast(0), tokensIn + r.tokensIn.coerceAtLeast(0L), tokensOut + r.tokensOut.coerceAtLeast(0L))

    fun sum(rows: List<UsageDay>): UsageTotals = rows.fold(UsageTotals.ZERO) { a, r -> a.plus(r) }

    fun forDay(rows: List<UsageDay>, day: LocalDate): UsageTotals =
        rows.filter { parseDay(it.day) == day }.fold(UsageTotals.ZERO) { a, r -> a.plus(r) }

    /** The last [count] days ending with [today], oldest first; days without usage have zero totals. */
    fun lastDays(rows: List<UsageDay>, today: LocalDate, count: Int): List<DayTotal> {
        val perDay = HashMap<LocalDate, UsageTotals>()
        for (r in rows) {
            val d = parseDay(r.day) ?: continue
            perDay[d] = (perDay[d] ?: UsageTotals.ZERO).plus(r)
        }
        return (count - 1 downTo 0).map { back ->
            val d = today.minusDays(back.toLong())
            DayTotal(d, perDay[d] ?: UsageTotals.ZERO)
        }
    }

    /** Totals of the last [n] days including [today]. */
    fun sumLast(rows: List<UsageDay>, today: LocalDate, n: Int): UsageTotals =
        lastDays(rows, today, n).fold(UsageTotals.ZERO) { a, d -> UsageTotals(a.turns + d.totals.turns, a.tokensIn + d.totals.tokensIn, a.tokensOut + d.totals.tokensOut) }

    /** Per model, busiest first ("" = Hermes's default model). */
    fun byModel(rows: List<UsageDay>): List<ModelTotal> {
        val m = LinkedHashMap<String, UsageTotals>()
        for (r in rows) m[r.model] = (m[r.model] ?: UsageTotals.ZERO).plus(r)
        return m.map { ModelTotal(it.key, it.value) }.sortedWith(compareByDescending<ModelTotal> { it.totals.total }.thenBy { it.model })
    }

    /** 950 -> "950", 1234 -> "1.2k", 12345 -> "12k", 3400000 -> "3.4M". The letters are the same in every language. */
    fun compact(n: Long, locale: Locale = Locale.getDefault()): String {
        val v = n.coerceAtLeast(0L)
        fun one(x: Double, unit: String): String {
            val s = String.format(locale, "%.1f", x)
            val trimmed = if (s.endsWith(".0") || s.endsWith(",0")) s.dropLast(2) else s
            return trimmed + unit
        }
        return when {
            v < 1_000L -> v.toString()
            v < 10_000L -> one(v / 1_000.0, "k")
            v < 1_000_000L -> (v / 1_000L).toString() + "k"
            v < 10_000_000L -> one(v / 1_000_000.0, "M")
            v < 1_000_000_000L -> (v / 1_000_000L).toString() + "M"
            else -> one(v / 1_000_000_000.0, "B")
        }
    }
}
