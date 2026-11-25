package dev.caraalink.wireless

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import dev.caraalink.MainActivity
import dev.caraalink.R
import dev.caraalink.connection.ConnectionStateHolder
import dev.caraalink.diagnostics.DiagnosticLogger
import dev.caraalink.service.UsbLinkForegroundService
import dev.caraalink.settings.AppSettings
import dev.caraalink.ui.NEARBY_PERMISSION
import dev.caraalink.ui.shouldAutoConnect

/**
 * The car's Bluetooth as the cue that the driver just got in. The phone joins
 * it by itself on every drive, long before anyone opens an app, so that is
 * when the wireless link to the car screen is started.
 */
object CarBluetooth {

    data class Paired(val name: String, val address: String, val deviceClass: Int?)

    // BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO and AUDIO_VIDEO_HANDSFREE.
    private const val CLASS_CAR_AUDIO = 0x0420
    private const val CLASS_HANDSFREE = 0x0408

    internal fun isCar(deviceClass: Int?): Boolean = deviceClass == CLASS_CAR_AUDIO || deviceClass == CLASS_HANDSFREE

    /** Car kits first, then everything else, each by name. */
    internal fun carsFirst(devices: List<Paired>): List<Paired> =
        devices.sortedWith(compareBy<Paired> { !isCar(it.deviceClass) }.thenBy { it.name.lowercase() })

    /** Only the chosen car's Bluetooth starts a connect, and only when one could be started by hand. */
    internal fun shouldStart(carAddress: String?, connectedAddress: String?, canConnect: Boolean): Boolean =
        carAddress != null && carAddress.equals(connectedAddress, ignoreCase = true) && canConnect

    /**
     * Whether a search started from the background can find the car. Up to
     * Android 12L a Wi-Fi Direct scan counts as location access, and from
     * Android 11 a service started from the background gets none, so on 11
     * to 12L the scan would run for nothing: there the driver is asked for a
     * tap instead.
     */
    internal fun canSearchFromBackground(sdk: Int): Boolean = sdk < Build.VERSION_CODES.R || sdk >= Build.VERSION_CODES.TIRAMISU

    /** Paired devices can be read without asking up to Android 11; from 12 it takes Nearby devices. */
    fun hasPermission(context: Context): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    /** The phone's paired Bluetooth devices, for choosing which one is the car. */
    @SuppressLint("MissingPermission")
    fun paired(context: Context): List<RememberedCar> {
        if (!hasPermission(context)) return emptyList()
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return emptyList()
        val devices = runCatching {
            adapter.bondedDevices.orEmpty().map { Paired(it.name ?: it.address, it.address, it.bluetoothClass?.deviceClass) }
        }.getOrDefault(emptyList())
        return carsFirst(devices).map { RememberedCar(it.name, it.address) }
    }
}

/** Starts the wireless search when the phone connects to the Bluetooth device chosen in Settings. */
class CarBluetoothReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // A protected broadcast: only the system can send it, so it can't be faked by another app.
        if (intent.action != BluetoothDevice.ACTION_ACL_CONNECTED) return
        val car = AppSettings.carBluetooth(context) ?: return
        @Suppress("DEPRECATION")
        val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
        val wirelessCar = AppSettings.wirelessCar(context)
        val canScan = context.checkSelfPermission(NEARBY_PERMISSION) == PackageManager.PERMISSION_GRANTED
        val canConnect = canScan && shouldAutoConnect(ConnectionStateHolder.snapshot, wirelessCar != null)
        if (!CarBluetooth.shouldStart(car.address, device?.address, canConnect)) return

        val log = DiagnosticLogger(context, "wireless")
        if (!CarBluetooth.canSearchFromBackground(Build.VERSION.SDK_INT)) {
            log.log("car Bluetooth connected -- this Android version can't scan from the background, asking for a tap")
            notifyTapToConnect(context, wirelessCar?.name ?: car.name)
            return
        }
        log.log("car Bluetooth connected -- starting the wireless search")
        val start = Intent(context, UsbLinkForegroundService::class.java)
            .setAction(UsbLinkForegroundService.ACTION_START_WIRELESS)
            .putExtra(UsbLinkForegroundService.EXTRA_AUTO, true)
        try {
            context.startForegroundService(start)
        } catch (e: Exception) {
            // Android can refuse a service start from the background; one tap on a notification does it instead.
            log.log("background start refused ($e) -- asking for a tap")
            notifyTapToConnect(context, wirelessCar?.name ?: car.name)
        }
    }

    private fun notifyTapToConnect(context: Context, carName: String) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.fgs_channel), NotificationManager.IMPORTANCE_DEFAULT),
        )
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_bridge)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(context.getString(R.string.wireless_open_to_reconnect, carName))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify(NOTIFICATION_ID, n) }
    }

    private companion object {
        const val CHANNEL_ID = "car_nearby"
        const val NOTIFICATION_ID = 6
    }
}
