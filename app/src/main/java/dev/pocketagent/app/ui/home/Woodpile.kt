package dev.pocketagent.app.ui.home

import dev.pocketagent.app.data.BudgetMode
import dev.pocketagent.app.data.UsageBudget

/**
 * The woodpile beside the hearth shows how much of today's token budget is left. Its level is the frame index of the sprite
 * `scene_woodpile`: 0 is ash and a stump (used up), 5 is a full stack. Both WARN and BLOCK budgets show it; only the number
 * the note tells differs.
 */
object Woodpile {
    /** Counts above this are clamped, so that the exact integer arithmetic below can never overflow. */
    private const val MAX_TOKENS = 1_000_000_000_000_000L

    /** The highest level (a full stack). */
    const val FULL = 5

    /**
     * Null when there is no budget (mode OFF or a limit of 0 or less); else 5 for more than 80 percent left, 4 for more than 60,
     * 3 for more than 40, 2 for more than 20, 1 for anything left, 0 for nothing left. The comparisons use whole tokens, so
     * a boundary such as exactly 60 percent left is exact (and belongs to the lower level).
     */
    fun level(usedToday: Long, budget: UsageBudget): Int? {
        val limit = limitOf(budget) ?: return null
        val left = leftTokens(usedToday, limit)
        return when {
            left * 5 > limit * 4 -> 5
            left * 5 > limit * 3 -> 4
            left * 5 > limit * 2 -> 3
            left * 5 > limit -> 2
            left > 0L -> 1
            else -> 0
        }
    }

    /** The fraction left, 0..1, or null as in [level]. */
    fun left(usedToday: Long, budget: UsageBudget): Float? {
        val limit = limitOf(budget) ?: return null
        return (leftTokens(usedToday, limit).toDouble() / limit.toDouble()).toFloat()
    }

    private fun limitOf(budget: UsageBudget): Long? {
        if (budget.mode == BudgetMode.OFF) return null
        if (budget.dailyTokens <= 0L) return null
        return budget.dailyTokens.coerceAtMost(MAX_TOKENS)
    }

    private fun leftTokens(usedToday: Long, limit: Long): Long = limit - usedToday.coerceIn(0L, limit)
}
