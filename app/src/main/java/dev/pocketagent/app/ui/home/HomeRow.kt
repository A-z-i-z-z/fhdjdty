package dev.pocketagent.app.ui.home

import java.time.ZoneId

/**
 * Row slots for later waves (Wave 1a, HOME.md 3.8): the pure part of this file, written in the first slice.
 *
 * Still to come in a later slice, in this same file: the interface `HomeRow` (with its two composable members,
 * which need the Compose runtime), the immutable marker on [HomeRowEnv] and `HomeHeading`. [orderRows] is
 * generic on purpose, so that it needs no knowledge of `HomeRow` and can be tested with a plain stand-in.
 */
enum class HomeSlot { AFTER_SUGGESTIONS, AFTER_AVATARS, AFTER_RECENT }

/** What a row may look at to decide whether it exists and what it shows. */
class HomeRowEnv(val nowMs: Long, val zone: ZoneId, val dayPart: DayPart)

/**
 * The rows in the order Home shows them: by slot (the declaration order of [HomeSlot]), inside a slot by
 * ascending [orderOf], ties broken by [idOf]. The sort is stable and the input is not changed.
 */
internal fun <T> orderRows(rows: List<T>, slotOf: (T) -> HomeSlot, orderOf: (T) -> Int, idOf: (T) -> String): List<T> =
    rows.sortedWith(compareBy<T>({ slotOf(it).ordinal }, { orderOf(it) }, { idOf(it) }))
