package dev.caraalink.link

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SocketTransportTest {
    @Test
    fun acceptsTheWifiDirectAndWifiInterfaces() {
        assertTrue(SocketTransport.acceptsInterface("p2p-wlan0-0"))
        // A SoftAP head unit connects back over the ordinary Wi-Fi interface.
        assertTrue(SocketTransport.acceptsInterface("wlan0"))
        assertFalse(SocketTransport.acceptsInterface("rmnet0"))
        assertFalse(SocketTransport.acceptsInterface("lo"))
        assertFalse(SocketTransport.acceptsInterface(null))
    }
}
