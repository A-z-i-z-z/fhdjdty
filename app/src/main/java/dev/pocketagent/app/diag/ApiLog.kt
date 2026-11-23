package dev.pocketagent.app.diag

import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

/** Ring buffer of recent API calls: method, path, status, timing, and error bodies. Never request bodies. */
object ApiLog {
    private const val MAX = 60
    private val items = ArrayDeque<String>()
    private val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    @Synchronized
    fun add(line: String) {
        items.addLast("${fmt.format(Date())} $line")
        while (items.size > MAX) items.removeFirst()
    }

    @Synchronized
    fun snapshot(): List<String> = items.toList()
}
