package com.pandaapps.appstore.work

/** Why the update worker leaves an available update alone on this run. */
enum class AutoUpdateSkip(val description: String) {
    /** An earlier background download of this version could not finish within the worker's time limit. */
    Deferred("its download is too slow to finish in the background; install it from the app"),

    /** It needs a tap and the user was already offered this version; don't prompt again every period. */
    AlreadyOffered("already offered; waiting for you to install it"),

    /** An install is running, or a committed session is waiting for the user / the system. */
    InProgress("an install is already in progress or waiting for confirmation"),

    /** At the throughput measured on this run, the download can't finish before the time limit. */
    NotEnoughTime("not enough time left on this run"),
}

/**
 * Pure decisions of the background auto-update (SPEC #8). The worker runs under WorkManager's
 * 10-minute limit and has no foreground service, so a download has to fit in one run.
 */
object AutoUpdatePolicy {

    /** Downloads/installs must end by then (WorkManager stops a worker at 10 minutes). */
    const val INSTALL_BUDGET_MILLIS = 8 * 60_000L

    /** Extra time to wait for committed silent installs to report, within the 10 minutes. */
    const val RESULT_WAIT_MILLIS = 90_000L

    /** Don't start another download with less than this left. */
    const val MIN_START_MILLIS = 30_000L

    /** Shorter download samples are too noisy (or a cached APK) to estimate throughput from. */
    const val MIN_SAMPLE_MILLIS = 2_000L

    /**
     * Updates the worker must not start on its own, from persisted state alone.
     * Silent-eligible updates are retried every run (no prompt involved); updates that need a tap
     * are offered once per version.
     */
    fun skipReason(key: String, silent: Boolean, notified: Set<String>, deferred: Set<String>): AutoUpdateSkip? =
        when {
            key in deferred -> AutoUpdateSkip.Deferred
            !silent && key in notified -> AutoUpdateSkip.AlreadyOffered
            else -> null
        }

    /** Bytes per second of a download sample, or null when the sample is too short to tell. */
    fun throughput(bytes: Long, elapsedMillis: Long): Double? =
        if (bytes <= 0 || elapsedMillis < MIN_SAMPLE_MILLIS) null else bytes * 1000.0 / elapsedMillis

    /**
     * True when [sizeBytes] can be downloaded in [remainingMillis] at [bytesPerSecond]. Unknown
     * size or throughput (nothing measured yet on this run) gets the benefit of the doubt.
     */
    fun canFinishInTime(sizeBytes: Long?, bytesPerSecond: Double?, remainingMillis: Long): Boolean {
        if (sizeBytes == null || sizeBytes <= 0 || bytesPerSecond == null || bytesPerSecond <= 0) return true
        return sizeBytes / bytesPerSecond * 1000 <= remainingMillis
    }

    /**
     * After a background download was cut off by the time limit: true when it could not finish
     * even with a whole run to itself ([budgetMillis]), so retrying next period would only repeat
     * the same doomed download. False when it merely started too late in this run.
     *
     * @param bytes downloaded when it was cut off. @param total expected size (≤ 0 if unknown).
     * @param elapsedMillis time spent downloading those bytes.
     */
    fun isTooSlowForBackground(bytes: Long, total: Long, elapsedMillis: Long, budgetMillis: Long): Boolean {
        // Can't project: only give up on it once it had a fair share of a run and still wasn't done.
        if (total <= 0 || bytes <= 0) return elapsedMillis >= budgetMillis / 2
        val projectedMillis = elapsedMillis.toDouble() * total / bytes
        return projectedMillis > budgetMillis
    }
}
