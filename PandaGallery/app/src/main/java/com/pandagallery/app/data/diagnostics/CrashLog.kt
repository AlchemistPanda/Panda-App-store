package com.pandagallery.app.data.diagnostics

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.util.Log
import com.pandagallery.app.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * On-device crash and error log, viewable and shareable from Settings › Diagnostics.
 *
 * Three sources feed it:
 * - uncaught exceptions on any thread, written synchronously before the process dies;
 * - errors the app catches but still wants explained (e.g. one file failing in a batch), via [e];
 * - on the next launch, the platform's own exit records ([ApplicationExitInfo]) for deaths an
 *   exception handler can never see: ANRs, native crashes in the media codecs, and low-memory
 *   kills.
 *
 * The enabled flag lives in SharedPreferences rather than DataStore because it has to be read
 * synchronously from inside the crash handler.
 */
object CrashLog {
    private const val TAG = "CrashLog"
    private const val PREFS = "diagnostics"
    private const val KEY_ENABLED = "crash_log_enabled"
    private const val KEY_LAST_EXIT_TIMESTAMP = "last_exit_info_timestamp"
    private const val LOG_FILE = "crash_log.txt"

    /** Oldest entries are dropped past this, so the log can never eat meaningful storage. */
    private const val MAX_BYTES = 512 * 1024

    private lateinit var appContext: Context
    private val lock = Any()
    private val _enabled = MutableStateFlow(true)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    /** Bumped on every write or clear, so an open log view knows to re-read the file. */
    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    private val logFile: File get() = File(appContext.filesDir, "logs/$LOG_FILE")

    fun install(context: Context) {
        appContext = context.applicationContext
        _enabled.value = prefs().getBoolean(KEY_ENABLED, true)

        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { append("CRASH", "Uncaught exception on thread '${thread.name}'", error) }
            // Hand over to the platform so the process still dies (and reports) as it normally would.
            previous?.uncaughtException(thread, error)
        }

        Thread {
            runCatching { recordPreviousExits() }
                .onFailure { Log.w(TAG, "Could not read process exit history", it) }
        }.apply { name = "CrashLog-exit-info" }.start()
    }

    fun setEnabled(enabled: Boolean) {
        prefs().edit().putBoolean(KEY_ENABLED, enabled).apply()
        _enabled.value = enabled
    }

    /** Records a caught error. Also goes to logcat, so it is visible over adb either way. */
    fun e(tag: String, message: String, error: Throwable? = null) {
        Log.e(tag, message, error)
        if (::appContext.isInitialized) runCatching { append("ERROR", "[$tag] $message", error) }
    }

    fun read(): String = synchronized(lock) {
        logFile.takeIf { it.exists() }?.readText().orEmpty()
    }

    fun clear() {
        synchronized(lock) { logFile.delete() }
        _revision.value++
    }

    /**
     * Copies the log to the FileProvider-shared cache folder with a header describing the
     * build and device, and returns that copy.
     */
    fun exportForSharing(): File {
        val dir = File(appContext.cacheDir, "share").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        return File(dir, "PandaGallery-log-$stamp.txt").apply {
            writeText(deviceHeader() + "\n" + read().ifEmpty { "(log is empty)\n" })
        }
    }

    private fun append(level: String, message: String, error: Throwable?) {
        if (!_enabled.value) return
        val entry = buildString {
            append("===== ").append(timestamp(System.currentTimeMillis())).append(" · ").append(level)
            append(" · v").append(BuildConfig.VERSION_NAME).append(" (").append(BuildConfig.VERSION_CODE).append(")\n")
            append(message).append('\n')
            if (error != null) append(stackTrace(error))
            append('\n')
        }
        synchronized(lock) {
            val file = logFile
            file.parentFile?.mkdirs()
            file.appendText(entry)
            if (file.length() > MAX_BYTES) trim(file)
        }
        _revision.value++
    }

    /** Keeps the newest half of the file, cut on an entry boundary. */
    private fun trim(file: File) {
        val text = file.readText()
        val keepFrom = text.indexOf("\n===== ", startIndex = text.length / 2)
        file.writeText(if (keepFrom >= 0) text.substring(keepFrom + 1) else "")
    }

    private fun recordPreviousExits() {
        val activityManager = appContext.getSystemService(ActivityManager::class.java) ?: return
        val prefs = prefs()
        val lastSeen = prefs.getLong(KEY_LAST_EXIT_TIMESTAMP, 0L)
        val exits = activityManager
            .getHistoricalProcessExitReasons(appContext.packageName, 0, 20)
            .filter { it.timestamp > lastSeen }
        exits.maxOfOrNull { it.timestamp }?.let {
            prefs.edit().putLong(KEY_LAST_EXIT_TIMESTAMP, it).apply()
        }
        // The first launch only sets the watermark: old exits predate the log and would mislead.
        if (lastSeen == 0L) return

        exits.sortedBy { it.timestamp }.forEach { exit ->
            val label = when (exit.reason) {
                ApplicationExitInfo.REASON_ANR -> "ANR (app not responding)"
                ApplicationExitInfo.REASON_CRASH_NATIVE -> "Native crash"
                ApplicationExitInfo.REASON_LOW_MEMORY -> "Killed for low memory"
                ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "Killed for excessive resource use"
                ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "Initialization failure"
                // Java crashes are already logged, with a full trace, by the handler above.
                else -> return@forEach
            }
            val details = buildString {
                append("Previous session ended: ").append(label)
                append(" at ").append(timestamp(exit.timestamp))
                append("\nprocess=").append(exit.processName)
                append(" importance=").append(exit.importance)
                append(" pss=").append(exit.pss / 1024).append("MB rss=").append(exit.rss / 1024).append("MB")
                exit.description?.let { append("\ndescription=").append(it) }
                if (exit.reason == ApplicationExitInfo.REASON_ANR || exit.reason == ApplicationExitInfo.REASON_CRASH_NATIVE) {
                    // ANR traces can be megabytes; the main thread's section near the top is what matters.
                    runCatching {
                        exit.traceInputStream?.bufferedReader()?.use { reader ->
                            val trace = reader.readText()
                            append("\n--- trace (first 12 KB) ---\n").append(trace.take(12 * 1024))
                        }
                    }
                }
            }
            append("EXIT", details, null)
        }
    }

    private fun deviceHeader(): String = buildString {
        append("Panda Gallery ").append(BuildConfig.VERSION_NAME).append(" (").append(BuildConfig.VERSION_CODE).append(")\n")
        append("Device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
        append(" · Android ").append(Build.VERSION.RELEASE).append(" (SDK ").append(Build.VERSION.SDK_INT).append(")\n")
        append("Exported: ").append(timestamp(System.currentTimeMillis())).append('\n')
    }

    private fun stackTrace(error: Throwable): String =
        StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()

    private fun timestamp(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date(millis))

    private fun prefs() = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
