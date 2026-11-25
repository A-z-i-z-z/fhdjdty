package dev.pocketagent.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * The app-wide clock for code that wants "now" as the user sees it (the sky of the home scene, a calendar). In a build
 * with test hooks the debug link can pin it to one instant, so a screenshot of a given date and hour can be repeated.
 * A pinned clock is frozen: it does not advance. Message timestamps, alarms and everything else keep the real time.
 */
object AppClock {
    private val pinnedState = MutableStateFlow<Long?>(null)

    /** Epoch milliseconds of the pinned instant, or null while the clock shows the real time. */
    val pinned: StateFlow<Long?> = pinnedState.asStateFlow()

    /** The pinned instant, else the real time. */
    fun nowMillis(): Long = pinnedState.value ?: System.currentTimeMillis()

    fun now(zone: ZoneId = ZoneId.systemDefault()): ZonedDateTime = Instant.ofEpochMilli(nowMillis()).atZone(zone)

    /** Only the debug link calls this. */
    fun pin(epochMillis: Long) {
        pinnedState.value = epochMillis
    }

    fun unpin() {
        pinnedState.value = null
    }

    /**
     * Reads "2027-03-21T20:30" (also with seconds, or with an offset such as "+03:00" or "Z"). A time without an offset is
     * read in [zone]. Null when the text cannot be read. Pure.
     */
    fun parse(text: String, zone: ZoneId = ZoneId.systemDefault()): Long? {
        val t = text.trim()
        if (t.isEmpty()) return null
        try {
            return OffsetDateTime.parse(t).toInstant().toEpochMilli()
        } catch (_: DateTimeException) {
        }
        return try {
            LocalDateTime.parse(t).atZone(zone).toInstant().toEpochMilli()
        } catch (_: DateTimeException) {
            null
        }
    }
}
