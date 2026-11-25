package dev.caraalink.wireless

import dev.caraalink.wireless.CarBluetooth.Paired
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CarBluetoothTest {

    private val car = "AA:BB:CC:DD:EE:FF"

    @Test
    fun `the chosen car's Bluetooth starts a connect`() {
        assertTrue(CarBluetooth.shouldStart(car, car, canConnect = true))
        // Android reports addresses in upper case; a stored one must still match.
        assertTrue(CarBluetooth.shouldStart(car, car.lowercase(), canConnect = true))
    }

    @Test
    fun `any other Bluetooth device does not`() {
        assertFalse(CarBluetooth.shouldStart(car, "11:22:33:44:55:66", canConnect = true))
        assertFalse(CarBluetooth.shouldStart(car, null, canConnect = true))
    }

    @Test
    fun `nothing starts with the option off, or while a link is already up`() {
        assertFalse(CarBluetooth.shouldStart(null, car, canConnect = true))
        assertFalse(CarBluetooth.shouldStart(car, car, canConnect = false))
    }

    @Test
    fun `car kits are listed first when choosing the car`() {
        val earbuds = Paired("Buds", "1", 0x0404)
        val watch = Paired("a watch", "2", null)
        val carKit = Paired("Geely", "3", 0x0420)
        val handsFree = Paired("BT Hands-free", "4", 0x0408)

        assertEquals(listOf(handsFree, carKit, watch, earbuds), CarBluetooth.carsFirst(listOf(earbuds, watch, carKit, handsFree)))
    }

    @Test
    fun `Android 11 to 12L ask for a tap, since a background start can't scan for the car there`() {
        assertTrue(CarBluetooth.canSearchFromBackground(29))
        assertFalse(CarBluetooth.canSearchFromBackground(30))
        assertFalse(CarBluetooth.canSearchFromBackground(32))
        assertTrue(CarBluetooth.canSearchFromBackground(33))
        assertTrue(CarBluetooth.canSearchFromBackground(34))
    }
}
