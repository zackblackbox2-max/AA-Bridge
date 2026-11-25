package dev.caraalink.service

import dev.caraalink.connection.CarLink
import dev.caraalink.connection.ConnectionPhase
import dev.caraalink.connection.ConnectionSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkPolicyTest {
    private val error = ConnectionSnapshot(phase = ConnectionPhase.ERROR)

    @Test
    fun stopsOnlyOnTheChangeIntoError() {
        assertTrue(UsbLinkForegroundService.shouldStopOnError(ConnectionPhase.STREAMING, error))
        // A leftover ERROR must not stop a new cable session's first update.
        assertFalse(UsbLinkForegroundService.shouldStopOnError(ConnectionPhase.ERROR, error.copy(link = CarLink.CABLE)))
        assertFalse(UsbLinkForegroundService.shouldStopOnError(ConnectionPhase.STREAMING, error.copy(link = CarLink.WIRELESS)))
        assertFalse(UsbLinkForegroundService.shouldStopOnError(ConnectionPhase.ATTACHED, ConnectionSnapshot(phase = ConnectionPhase.STREAMING)))
    }

    @Test
    fun failedAttemptsKeepTryingInsideTheReconnectWindow() {
        assertEquals(null, UsbLinkForegroundService.afterFailedAttempt(reconnectUntil = 120_000, now = 60_000))
        assertEquals(dev.caraalink.connection.WirelessPhase.FAILED, UsbLinkForegroundService.afterFailedAttempt(reconnectUntil = 120_000, now = 120_001))
        assertEquals(dev.caraalink.connection.WirelessPhase.FAILED, UsbLinkForegroundService.afterFailedAttempt(reconnectUntil = 0, now = 5_000))
    }

    @Test
    fun anAutoSearchThatNeverReachedTheCarEndsQuietly() {
        val notFound = dev.caraalink.connection.WirelessPhase.NOT_FOUND
        val failed = dev.caraalink.connection.WirelessPhase.FAILED
        val idle = dev.caraalink.connection.WirelessPhase.IDLE
        assertEquals(idle, UsbLinkForegroundService.phaseToShow(notFound, autoSearch = true, streamed = false))
        assertEquals(idle, UsbLinkForegroundService.phaseToShow(failed, autoSearch = true, streamed = false))
        // The driver's own search, and a drive that was up and then failed, still say so.
        assertEquals(notFound, UsbLinkForegroundService.phaseToShow(notFound, autoSearch = false, streamed = false))
        assertEquals(failed, UsbLinkForegroundService.phaseToShow(failed, autoSearch = true, streamed = true))
        val wifiOff = dev.caraalink.connection.WirelessPhase.WIFI_OFF
        assertEquals(wifiOff, UsbLinkForegroundService.phaseToShow(wifiOff, autoSearch = true, streamed = false))
    }
}
