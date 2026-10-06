package com.pandaapps.appstore.util

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * In-memory ring buffer of the most recent [capacity] log lines, mirrored to Logcat.
 * Backs the Debug console. Thread-safe.
 *
 * Lines look like `14:03:22.481 I/Install: Downloading Panda Garage v1.0.6`.
 */
class AppLog(private val capacity: Int = DEFAULT_CAPACITY) {

    private val buffer = ArrayDeque<String>(capacity)
    private val _lines = MutableStateFlow<List<String>>(emptyList())

    /** Snapshot of the buffer, oldest first. */
    val lines: StateFlow<List<String>> = _lines.asStateFlow()

    fun d(tag: String, message: String) = log(Log.DEBUG, tag, message, null)
    fun i(tag: String, message: String) = log(Log.INFO, tag, message, null)
    fun w(tag: String, message: String, throwable: Throwable? = null) = log(Log.WARN, tag, message, throwable)
    fun e(tag: String, message: String, throwable: Throwable? = null) = log(Log.ERROR, tag, message, throwable)

    fun clear() {
        synchronized(buffer) {
            buffer.clear()
            _lines.value = emptyList()
        }
    }

    /** Whole buffer as one string, for copy/share. */
    fun dump(): String = lines.value.joinToString(separator = "\n")

    private fun log(priority: Int, tag: String, message: String, throwable: Throwable?) {
        val logcatTag = "$LOGCAT_PREFIX$tag"
        when (priority) {
            Log.DEBUG -> Log.d(logcatTag, message, throwable)
            Log.INFO -> Log.i(logcatTag, message, throwable)
            Log.WARN -> Log.w(logcatTag, message, throwable)
            else -> Log.e(logcatTag, message, throwable)
        }

        val detail = throwable?.let { " (${it::class.java.simpleName}: ${it.message})" }.orEmpty()
        val line = "${LocalTime.now().format(TIME_FORMAT)} ${levelChar(priority)}/$tag: $message$detail"
        synchronized(buffer) {
            if (buffer.size >= capacity) buffer.removeFirst()
            buffer.addLast(line)
            _lines.value = buffer.toList()
        }
    }

    private fun levelChar(priority: Int): Char = when (priority) {
        Log.DEBUG -> 'D'
        Log.INFO -> 'I'
        Log.WARN -> 'W'
        else -> 'E'
    }

    companion object {
        const val DEFAULT_CAPACITY = 1000
        private const val LOGCAT_PREFIX = "Panda."
        private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
    }
}
