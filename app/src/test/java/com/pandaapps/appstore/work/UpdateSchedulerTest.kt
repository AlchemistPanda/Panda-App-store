package com.pandaapps.appstore.work

import androidx.work.NetworkType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class UpdateSchedulerTest {

    @Test
    fun intervalZero_meansNoWork() {
        assertNull(UpdateScheduler.buildRequest(intervalHours = 0, autoUpdate = true, wifiOnly = true))
        assertNull(UpdateScheduler.buildRequest(intervalHours = -1, autoUpdate = false, wifiOnly = false))
    }

    @Test
    fun everyIntervalOption_mapsToThatPeriod() {
        for (hours in listOf(1, 3, 6, 12, 24)) {
            val request = UpdateScheduler.buildRequest(hours, autoUpdate = false, wifiOnly = true)
            assertNotNull(request)
            assertEquals(TimeUnit.HOURS.toMillis(hours.toLong()), request!!.workSpec.intervalDuration)
            assertTrue(request.workSpec.isPeriodic)
            assertTrue(UpdateScheduler.WORK_NAME in request.tags)
        }
    }

    @Test
    fun networkType_unmeteredOnlyWhenAutoUpdatingOnWifiOnly() {
        assertEquals(NetworkType.UNMETERED, UpdateScheduler.networkTypeFor(autoUpdate = true, wifiOnly = true))
        assertEquals(NetworkType.CONNECTED, UpdateScheduler.networkTypeFor(autoUpdate = true, wifiOnly = false))
        assertEquals(NetworkType.CONNECTED, UpdateScheduler.networkTypeFor(autoUpdate = false, wifiOnly = true))
        assertEquals(NetworkType.CONNECTED, UpdateScheduler.networkTypeFor(autoUpdate = false, wifiOnly = false))
    }

    @Test
    fun request_carriesTheNetworkConstraint() {
        val wifi = UpdateScheduler.buildRequest(6, autoUpdate = true, wifiOnly = true)!!
        assertEquals(NetworkType.UNMETERED, wifi.workSpec.constraints.requiredNetworkType)
        val any = UpdateScheduler.buildRequest(6, autoUpdate = false, wifiOnly = true)!!
        assertEquals(NetworkType.CONNECTED, any.workSpec.constraints.requiredNetworkType)
    }
}
