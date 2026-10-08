package com.pandagallery.app.data.compression

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import com.pandagallery.app.data.local.PreferencesDataSource
import com.pandagallery.app.domain.model.ThermalThrottleMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

enum class DeviceThermalLevel {
    NOMINAL,   // Full speed, no throttling
    MODERATE,  // Warm: throttle concurrency to max 2, 250ms cooldown
    SEVERE,    // Hot: throttle concurrency to 1, 1000ms cooldown
    CRITICAL,  // Overheating: pause execution until cooled down
}

data class ThermalState(
    val level: DeviceThermalLevel = DeviceThermalLevel.NOMINAL,
    val batteryTempC: Float? = null,
    val thermalStatus: Int = 0,
    val isThrottled: Boolean = false,
    val statusDescription: String = "Optimal",
)

@Singleton
class ThermalThrottlingMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesDataSource: PreferencesDataSource,
) {
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    private val monitorScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var currentMode: ThermalThrottleMode = ThermalThrottleMode.NORMAL

    private val _thermalState = MutableStateFlow(evaluateInitialState())
    val thermalState: StateFlow<ThermalState> = _thermalState.asStateFlow()

    init {
        monitorScope.launch {
            preferencesDataSource.userPreferencesFlow.collect { prefs ->
                currentMode = prefs.thermalThrottleMode
                refreshThermalState()
            }
        }

        // Register PowerManager thermal status listener on Android 10+ (API 29+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                powerManager?.addThermalStatusListener { status ->
                    updateThermalStatus(status)
                }
            }
        }
    }

    /**
     * Reads current battery temperature from sticky battery broadcast (in Celsius).
     */
    fun getBatteryTemperatureC(): Float? {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val rawTemp = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
        return if (rawTemp > 0) rawTemp / 10f else null
    }

    private fun evaluateInitialState(): ThermalState {
        val currentStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            powerManager?.currentThermalStatus ?: 0
        } else {
            0
        }
        val batteryTemp = getBatteryTemperatureC()
        return calculateThermalState(currentStatus, batteryTemp, currentMode)
    }

    fun updateThermalStatus(status: Int) {
        val batteryTemp = getBatteryTemperatureC()
        _thermalState.value = calculateThermalState(status, batteryTemp, currentMode)
    }

    fun refreshThermalState(): ThermalState {
        val currentStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            powerManager?.currentThermalStatus ?: _thermalState.value.thermalStatus
        } else {
            _thermalState.value.thermalStatus
        }
        val batteryTemp = getBatteryTemperatureC()
        val newState = calculateThermalState(currentStatus, batteryTemp, currentMode)
        _thermalState.value = newState
        return newState
    }

    /**
     * Calculate effective concurrency based on thermal level and requested concurrency.
     */
    fun effectiveConcurrency(requestedConcurrency: Int): Int {
        val state = refreshThermalState()
        return when (state.level) {
            DeviceThermalLevel.NOMINAL -> requestedConcurrency.coerceIn(1, 10)
            DeviceThermalLevel.MODERATE -> requestedConcurrency.coerceIn(1, 2)
            DeviceThermalLevel.SEVERE -> 1
            DeviceThermalLevel.CRITICAL -> 1
        }
    }

    /**
     * Cooldown or suspension hook called before processing a task.
     * Injects adaptive non-blocking delays or loops if in CRITICAL state.
     *
     * @param hasWork polled on every CRITICAL wait; once it returns false the wait ends. Without
     *   it, Pause all / Cancel all on a hot device (STRICT treats 42°C, common while charging, as
     *   critical) left every worker — and the foreground service, its notification and the wake
     *   lock — waiting here with nothing left to do for as long as the device stayed hot.
     */
    suspend fun applyCooldownIfNeeded(hasWork: suspend () -> Boolean = { true }) {
        var state = refreshThermalState()

        // If CRITICAL, suspend and wait until device cools below critical threshold
        while (state.level == DeviceThermalLevel.CRITICAL) {
            if (!hasWork()) return
            delay(3000L)
            state = refreshThermalState()
        }

        when (state.level) {
            DeviceThermalLevel.SEVERE -> delay(1000L)
            DeviceThermalLevel.MODERATE -> delay(250L)
            DeviceThermalLevel.NOMINAL, DeviceThermalLevel.CRITICAL -> Unit
        }
    }

    companion object {
        fun calculateThermalState(
            thermalStatus: Int,
            batteryTempC: Float?,
            mode: ThermalThrottleMode = ThermalThrottleMode.NORMAL,
        ): ThermalState {
            if (mode == ThermalThrottleMode.TURBO) {
                return ThermalState(
                    level = DeviceThermalLevel.NOMINAL,
                    batteryTempC = batteryTempC,
                    thermalStatus = thermalStatus,
                    isThrottled = false,
                    statusDescription = "Turbo (Unthrottled)",
                )
            }

            val level = when (mode) {
                ThermalThrottleMode.STRICT -> when {
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && thermalStatus >= PowerManager.THERMAL_STATUS_CRITICAL) ||
                        (batteryTempC != null && batteryTempC >= 42.0f) -> DeviceThermalLevel.CRITICAL

                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && thermalStatus >= PowerManager.THERMAL_STATUS_SEVERE) ||
                        (batteryTempC != null && batteryTempC >= 39.0f) -> DeviceThermalLevel.SEVERE

                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && thermalStatus >= PowerManager.THERMAL_STATUS_LIGHT) ||
                        (batteryTempC != null && batteryTempC >= 36.0f) -> DeviceThermalLevel.MODERATE

                    else -> DeviceThermalLevel.NOMINAL
                }

                ThermalThrottleMode.NORMAL -> when {
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && thermalStatus >= PowerManager.THERMAL_STATUS_CRITICAL) ||
                        (batteryTempC != null && batteryTempC >= 45.0f) -> DeviceThermalLevel.CRITICAL

                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && thermalStatus == PowerManager.THERMAL_STATUS_SEVERE) ||
                        (batteryTempC != null && batteryTempC >= 42.0f) -> DeviceThermalLevel.SEVERE

                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && thermalStatus in PowerManager.THERMAL_STATUS_LIGHT..PowerManager.THERMAL_STATUS_MODERATE) ||
                        (batteryTempC != null && batteryTempC >= 39.0f) -> DeviceThermalLevel.MODERATE

                    else -> DeviceThermalLevel.NOMINAL
                }

                ThermalThrottleMode.TURBO -> DeviceThermalLevel.NOMINAL
            }

            val isThrottled = level != DeviceThermalLevel.NOMINAL
            val description = when (level) {
                DeviceThermalLevel.NOMINAL -> "Optimal"
                DeviceThermalLevel.MODERATE -> if (mode == ThermalThrottleMode.STRICT) "Strict (Pacing)" else "Warm (Pacing)"
                DeviceThermalLevel.SEVERE -> if (mode == ThermalThrottleMode.STRICT) "Strict (Cooling)" else "Hot (Throttling)"
                DeviceThermalLevel.CRITICAL -> "Cooling Device"
            }

            return ThermalState(
                level = level,
                batteryTempC = batteryTempC,
                thermalStatus = thermalStatus,
                isThrottled = isThrottled,
                statusDescription = description,
            )
        }
    }
}
