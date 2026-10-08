package com.pandagallery.app.util

import java.text.SimpleDateFormat
import java.util.*

/**
 * Date formatting utilities for the gallery timeline.
 */
object DateUtils {
    private val fullDateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
    private val shortDateFormat = SimpleDateFormat("MMM d", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    private val dateTimeFormat = SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault())

    fun formatFullDate(epochMillis: Long): String = fullDateFormat.format(Date(epochMillis))
    fun formatShortDate(epochMillis: Long): String = shortDateFormat.format(Date(epochMillis))
    fun formatTime(epochMillis: Long): String = timeFormat.format(Date(epochMillis))
    fun formatDateTime(epochMillis: Long): String = dateTimeFormat.format(Date(epochMillis))

    fun getRelativeTimeLabel(epochMillis: Long): String {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply { timeInMillis = epochMillis }

        return when {
            isSameDay(now, target) -> "Today"
            isYesterday(now, target) -> "Yesterday"
            isSameWeek(now, target) -> SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(epochMillis))
            isSameYear(now, target) -> shortDateFormat.format(Date(epochMillis))
            else -> fullDateFormat.format(Date(epochMillis))
        }
    }

    private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean =
        cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)

    private fun isYesterday(today: Calendar, target: Calendar): Boolean {
        val yesterday = today.clone() as Calendar
        yesterday.add(Calendar.DAY_OF_YEAR, -1)
        return isSameDay(yesterday, target)
    }

    private fun isSameWeek(cal1: Calendar, cal2: Calendar): Boolean =
        cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.WEEK_OF_YEAR) == cal2.get(Calendar.WEEK_OF_YEAR)

    private fun isSameYear(cal1: Calendar, cal2: Calendar): Boolean =
        cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR)
}

/**
 * File size formatting utility.
 */
object FileUtils {
    fun formatSize(bytes: Long): String {
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> "%.1f GB".format(gb)
            mb >= 1.0 -> "%.1f MB".format(mb)
            kb >= 1.0 -> "%.0f KB".format(kb)
            else -> "$bytes B"
        }
    }

    fun formatDuration(millis: Long): String {
        val totalSeconds = millis / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%d:%02d".format(minutes, seconds)
        }
    }
}
