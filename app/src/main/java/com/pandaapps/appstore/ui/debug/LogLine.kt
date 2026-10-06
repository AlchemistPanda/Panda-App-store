package com.pandaapps.appstore.ui.debug

/**
 * One [com.pandaapps.appstore.util.AppLog] line split for display:
 * `14:03:22.481 I/Install: Downloading Panda Garage` → time, level 'I', tag "Install", message.
 * Lines that do not match the format come back with only [message] set.
 */
data class LogLine(
    val time: String?,
    val level: Char?,
    val tag: String?,
    val message: String,
) {
    companion object {
        private val PATTERN = Regex("""^(\d{2}:\d{2}:\d{2}\.\d{3}) ([VDIWEA])/([^:]*): ?(.*)$""", RegexOption.DOT_MATCHES_ALL)

        /** Pure and allocation-light; the console caches the styled result per line. */
        fun parse(line: String): LogLine {
            val match = PATTERN.matchEntire(line) ?: return LogLine(null, null, null, line)
            val (time, level, tag, message) = match.destructured
            return LogLine(time, level.single(), tag, message)
        }
    }
}
