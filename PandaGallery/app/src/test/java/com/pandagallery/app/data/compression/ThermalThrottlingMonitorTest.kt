package com.pandagallery.app.data.compression

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThermalThrottlingMonitorTest {

    @Test
    fun calculateThermalState_nominalWhenCool() {
        val state = ThermalThrottlingMonitor.calculateThermalState(
            thermalStatus = 0,
            batteryTempC = 34.5f,
        )
        assertEquals(DeviceThermalLevel.NOMINAL, state.level)
        assertFalse(state.isThrottled)
        assertEquals("Optimal", state.statusDescription)
    }

    @Test
    fun calculateThermalState_moderateWhenWarm() {
        val state = ThermalThrottlingMonitor.calculateThermalState(
            thermalStatus = 0,
            batteryTempC = 40.2f,
        )
        assertEquals(DeviceThermalLevel.MODERATE, state.level)
        assertTrue(state.isThrottled)
        assertEquals("Warm (Pacing)", state.statusDescription)
    }

    @Test
    fun calculateThermalState_severeWhenHot() {
        val state = ThermalThrottlingMonitor.calculateThermalState(
            thermalStatus = 0,
            batteryTempC = 43.5f,
        )
        assertEquals(DeviceThermalLevel.SEVERE, state.level)
        assertTrue(state.isThrottled)
        assertEquals("Hot (Throttling)", state.statusDescription)
    }

    @Test
    fun calculateThermalState_criticalWhenOverheating() {
        val state = ThermalThrottlingMonitor.calculateThermalState(
            thermalStatus = 0,
            batteryTempC = 47.0f,
        )
        assertEquals(DeviceThermalLevel.CRITICAL, state.level)
        assertTrue(state.isThrottled)
        assertEquals("Cooling Device", state.statusDescription)
    }

    @Test
    fun calculateThermalState_handlesNullBatteryTemperature() {
        val state = ThermalThrottlingMonitor.calculateThermalState(
            thermalStatus = 0,
            batteryTempC = null,
        )
        assertEquals(DeviceThermalLevel.NOMINAL, state.level)
        assertFalse(state.isThrottled)
    }

    @Test
    fun calculateThermalState_turboModeNeverThrottles() {
        val state = ThermalThrottlingMonitor.calculateThermalState(
            thermalStatus = 0,
            batteryTempC = 48.0f,
            mode = com.pandagallery.app.domain.model.ThermalThrottleMode.TURBO,
        )
        assertEquals(DeviceThermalLevel.NOMINAL, state.level)
        assertFalse(state.isThrottled)
        assertEquals("Turbo (Unthrottled)", state.statusDescription)
    }

    @Test
    fun calculateThermalState_strictModePacesEarlier() {
        // 37.0°C is NOMINAL in Normal mode, but MODERATE in Strict mode
        val normalState = ThermalThrottlingMonitor.calculateThermalState(
            thermalStatus = 0,
            batteryTempC = 37.0f,
            mode = com.pandagallery.app.domain.model.ThermalThrottleMode.NORMAL,
        )
        assertEquals(DeviceThermalLevel.NOMINAL, normalState.level)

        val strictState = ThermalThrottlingMonitor.calculateThermalState(
            thermalStatus = 0,
            batteryTempC = 37.0f,
            mode = com.pandagallery.app.domain.model.ThermalThrottleMode.STRICT,
        )
        assertEquals(DeviceThermalLevel.MODERATE, strictState.level)
        assertTrue(strictState.isThrottled)
        assertEquals("Strict (Pacing)", strictState.statusDescription)
    }
}
