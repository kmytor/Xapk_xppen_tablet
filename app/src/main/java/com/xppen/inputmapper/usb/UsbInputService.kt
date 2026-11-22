package com.xppen.inputmapper.usb

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.xppen.inputmapper.R
import com.xppen.inputmapper.input.HidReportParser
import com.xppen.inputmapper.input.MotionEventInjector
import com.xppen.inputmapper.settings.SettingsManager
import kotlinx.coroutines.launch


/**
 * Service to handle USB communication with XP-Pen tablet.
 * Runs in foreground to maintain connection and process HID reports.
 */
class UsbInputService : Service() {

    companion object {
        private const val TAG = "UsbInputService"
        private const val ACTION_USB_PERMISSION = "com.xppen.inputmapper.USB_PERMISSION"
        private const val ACTION_RELOAD_SETTINGS = "com.xppen.inputmapper.RELOAD_SETTINGS"
        private const val ACTION_STATUS_UPDATE = "com.xppen.inputmapper.STATUS_UPDATE"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "xppen_input_mapper_channel"
    }

    private lateinit var usbManager: UsbManager
    private lateinit var settingsManager: SettingsManager
    
    private var device: UsbDevice? = null
    private var connection: UsbDeviceConnection? = null
    private val claimedInterfaces = mutableListOf<UsbInterface>()
    private val inputEndpoints = mutableListOf<UsbEndpoint>()
    private val readThreads = mutableListOf<Thread>()
    private var running = false

    private val reportParser = HidReportParser()
    private val injector = MotionEventInjector()
    private var cursorOverlay: com.xppen.inputmapper.ui.CursorOverlayManager? = null

    // Dynamic receiver for device attachments/detachments
    private val usbReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            Log.d(TAG, "usbReceiver received action: $action")
            if (UsbManager.ACTION_USB_DEVICE_ATTACHED == action) {
                val attachedDevice = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                if (attachedDevice != null && isXpenDevice(attachedDevice)) {
                    Log.i(TAG, "XP-Pen USB Device Attached: ${attachedDevice.deviceName}")
                    device = attachedDevice
                    requestPermission(attachedDevice)
                }
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED == action) {
                val detachedDevice = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                if (detachedDevice != null && detachedDevice == device) {
                    Log.i(TAG, "XP-Pen USB Device Detached: ${detachedDevice.deviceName}")
                    stopReading()
                    closeConnection()
                    device = null
                    broadcastStatus("disconnected")
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "UsbInputService onCreate")
        usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        settingsManager = SettingsManager(this)
        
        try {
            cursorOverlay = com.xppen.inputmapper.ui.CursorOverlayManager(this)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize CursorOverlayManager", e)
        }
        
        // Register dynamic receiver for attach/detach events safely on Android 14+
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        androidx.core.content.ContextCompat.registerReceiver(
            this,
            usbReceiver,
            filter,
            androidx.core.content.ContextCompat.RECEIVER_EXPORTED
        )
        
        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }

        // Proactively scan for any already-connected tablets
        checkForConnectedDevices()
    }

    private fun buildNotification(): android.app.Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("XP-Pen Input Mapper")
            .setContentText("Listening to USB connection...")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelName = "XP-Pen Input Service"
            val channelDescription = "Service for mapping XP-Pen tablet inputs"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, channelName, importance).apply {
                description = channelDescription
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Log.i(TAG, "UsbInputService onStartCommand with action: $action")
        
        if (action == null) {
            // Scan for already connected devices on simple service start
            checkForConnectedDevices()
        } else {
            when (action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> handleDeviceAttached(intent)
                UsbManager.ACTION_USB_DEVICE_DETACHED -> handleDeviceDetached(intent)
                ACTION_USB_PERMISSION -> handlePermission(intent)
                ACTION_RELOAD_SETTINGS -> reloadSettings()
            }
        }
        return START_STICKY
    }

    private fun broadcastStatus(status: String) {
        Log.d(TAG, "Broadcasting status: $status")
        val intent = Intent(ACTION_STATUS_UPDATE).apply {
            putExtra("status", status)
        }
        sendBroadcast(intent)
    }

    private fun checkForConnectedDevices() {
        Log.i(TAG, "Scanning USB bus for XP-Pen devices...")
        val deviceList = usbManager.deviceList
        for (dev in deviceList.values) {
            if (isXpenDevice(dev)) {
                Log.i(TAG, "Found XP-Pen device in scan: ${dev.deviceName} (Vendor: ${dev.vendorId}, Product: ${dev.productId})")
                device = dev
                requestPermission(dev)
                return
            }
        }
        Log.w(TAG, "No XP-Pen devices found in the USB bus list.")
        broadcastStatus("disconnected")
    }

    private fun reloadSettings() {
        Log.i(TAG, "Reloading settings from Preferences")
        settingsManager = SettingsManager(this)
    }

    private fun handleDeviceAttached(intent: Intent) {
        val attachedDevice = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
        if (attachedDevice != null && isXpenDevice(attachedDevice)) {
            Log.i(TAG, "handleDeviceAttached: XP-Pen device found")
            device = attachedDevice
            requestPermission(attachedDevice)
        }
    }

    private fun handleDeviceDetached(intent: Intent) {
        val detachedDevice = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
        if (detachedDevice != null && detachedDevice == device) {
            Log.i(TAG, "handleDeviceDetached: XP-Pen device disconnected")
            stopReading()
            closeConnection()
            device = null
            broadcastStatus("disconnected")
        }
    }

    private fun handlePermission(intent: Intent) {
        val dev = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
        val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
        Log.i(TAG, "handlePermission: Device=$dev, Granted=$granted")
        
        if (dev != null && granted) {
            device = dev
            if (connection != null) {
                Log.i(TAG, "Connection already open, skipping openConnection")
                return
            }
            if (openConnection()) {
                startReading()
            } else {
                broadcastStatus("disconnected")
            }
        } else {
            Log.w(TAG, "Permission denied for device: $dev")
            broadcastStatus("disconnected")
        }
    }

    private fun isXpenDevice(device: UsbDevice): Boolean {
        // XP-Pen vendor ID is 0x28bd (or Hanvon Ugee vendor ID 10429 which is 0x28BD)
        // Also supporting common USB Hub/Dongle IDs if they appear as proxies
        return device.vendorId == 0x28bd || device.vendorId == 10429 || device.vendorId == 0x04d9 || device.vendorId == 0x05ac
    }

    private fun requestPermission(device: UsbDevice) {
        if (!usbManager.hasPermission(device)) {
            Log.i(TAG, "Requesting USB permission for ${device.deviceName}")
            broadcastStatus("connecting")
            // Use PendingIntent.getService to deliver the permission response back directly to onStartCommand
            val permissionIntent = PendingIntent.getService(
                this,
                0,
                Intent(this, UsbInputService::class.java).apply {
                    action = ACTION_USB_PERMISSION
                    putExtra(UsbManager.EXTRA_DEVICE, device)
                },
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }
            )
            usbManager.requestPermission(device, permissionIntent)
        } else {
            Log.i(TAG, "USB permission already granted for ${device.deviceName}")
            if (connection != null) {
                Log.i(TAG, "Connection already open, skipping openConnection")
                return
            }
            if (openConnection()) {
                startReading()
            } else {
                broadcastStatus("disconnected")
            }
        }
    }

    private fun openConnection(): Boolean {
        val dev = device ?: return false
        Log.i(TAG, "Opening connection to ${dev.deviceName}...")
        val conn = usbManager.openDevice(dev)
        if (conn == null) {
            Log.e(TAG, "Failed to open device connection")
            return false
        }
        connection = conn
        claimedInterfaces.clear()
        inputEndpoints.clear()

        // Find and claim ALL HID interfaces
        // and force the kernel driver to detach.
        for (i in 0 until dev.interfaceCount) {
            val intf = dev.getInterface(i)
            if (intf.interfaceClass == UsbConstants.USB_CLASS_HID) {
                if (conn.claimInterface(intf, true)) {
                    Log.i(TAG, "Claimed HID interface index $i (Protocol ${intf.interfaceProtocol})")
                    claimedInterfaces.add(intf)
                    
                    // Send SET_IDLE
                    try {
                        val requestType = 0x21 // USB_TYPE_CLASS | USB_RECIP_INTERFACE
                        val request = 0x0A // SET_IDLE
                        val value = 0x0000 // duration 0, report ID 0
                        val index = intf.id
                        val result = conn.controlTransfer(requestType, request, value, index, null, 0, 1000)
                        Log.d(TAG, "Sent SET_IDLE to interface ${intf.id}, result: $result")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to send SET_IDLE", e)
                    }

                    findInterruptEndpoints(intf)
                } else {
                    Log.w(TAG, "Failed to claim HID interface index $i")
                }
            }
        }

        if (claimedInterfaces.isEmpty()) {
            Log.e(TAG, "Could not claim any HID interface with an IN endpoint")
            conn.close()
            connection = null
            return false
        }

        // Enable fully-functional tablet mode by reading string descriptor 100
        // (as discovered by DIGImend / Linux hid-uclogic kernel driver)
        try {
            val buffer = ByteArray(255)
            val result = conn.controlTransfer(0x80, 0x06, 0x0364, 0x0409, buffer, buffer.size, 1000)
            Log.i(TAG, "Read String Descriptor 100 to enable tablet mode, result: $result bytes")
            if (result > 0) {
                val hexStr = buffer.take(result).joinToString(" ") { "%02x".format(it) }
                Log.d(TAG, "String 100 data: $hexStr")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read String Descriptor 100", e)
        }

        Log.i(TAG, "USB connection opened and ${claimedInterfaces.size} interfaces claimed successfully")
        broadcastStatus("connected")
        return true
    }

    private fun closeConnection() {
        Log.i(TAG, "Closing USB connection...")
        val conn = connection
        if (conn != null) {
            for (intf in claimedInterfaces) {
                conn.releaseInterface(intf)
            }
            conn.close()
        }
        claimedInterfaces.clear()
        inputEndpoints.clear()
        connection = null
    }

    private fun startReading() {
        if (running) return
        Log.i(TAG, "Starting USB HID report reading via bulkTransfer for ${inputEndpoints.size} endpoints...")
        running = true
        readThreads.clear()
        
        val conn = connection ?: return

        for (endpoint in inputEndpoints) {
            val thread = Thread {
                Log.d(TAG, "HID read loop running using bulkTransfer for endpoint ${endpoint.address}")
                val buffer = ByteArray(endpoint.maxPacketSize)
                
                while (running) {
                    try {
                        val bytesRead = conn.bulkTransfer(endpoint, buffer, buffer.size, 100)
                        if (bytesRead > 0) {
                            val hexString = buffer.take(bytesRead).joinToString(" ") { "%02X".format(it) }
                            Log.d(TAG, "EP ${endpoint.address} received: $hexString")
                            val report = buffer.copyOf(bytesRead)
                            processReport(report)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in bulkTransfer loop for endpoint ${endpoint.address}", e)
                        try { Thread.sleep(500) } catch (ie: Exception) {}
                    }
                }
                Log.d(TAG, "HID read loop exited for endpoint ${endpoint.address}")
            }
            readThreads.add(thread)
            thread.start()
        }
    }

    private fun stopReading() {
        Log.i(TAG, "Stopping USB HID reading threads...")
        running = false
        // Interrupting the thread might not break requestWait, but cancel() will.
        for (thread in readThreads) {
            thread.interrupt()
        }
        readThreads.clear()
    }

    private var lastPressedExpressKey = -1

    private fun processReport(report: ByteArray) {
        if (report.isEmpty()) return
        val reportId = report[0].toInt() and 0xFF
        
        if (reportId == 0x06) {
            handleExpressKeys(report)
            return
        }

        val data = reportParser.parseReport(report, device?.productId ?: 0)
        if (data.isValid) {
            injector.injectMotionEvent(this@UsbInputService, data, settingsManager, cursorOverlay)
        }
    }

    private fun handleExpressKeys(report: ByteArray) {
        if (report.size < 2) return
        val b1 = report[1].toInt() and 0xFF
        val b2 = if (report.size > 2) report[2].toInt() and 0xFF else 0
        
        var pressedKey = -1
        for (i in 0 until 8) {
            if ((b1 and (1 shl i)) != 0) {
                pressedKey = i
                break
            }
        }
        if (pressedKey == -1) {
            for (i in 0 until 8) {
                if ((b2 and (1 shl i)) != 0) {
                    pressedKey = i + 8
                    break
                }
            }
        }
        
        if (pressedKey != lastPressedExpressKey) {
            // Release last key
            if (lastPressedExpressKey != -1) {
                injectExpressKeyAction(lastPressedExpressKey, isDown = false)
            }
            // Press new key
            if (pressedKey != -1) {
                injectExpressKeyAction(pressedKey, isDown = true)
            }
            lastPressedExpressKey = pressedKey
        }
    }

    private fun injectExpressKeyAction(keyIndex: Int, isDown: Boolean) {
        Log.i(TAG, "Express Key event: Index $keyIndex, isDown=$isDown")
        val action = when (keyIndex) {
            0 -> SettingsManager.ACTION_UNDO
            1 -> SettingsManager.ACTION_REDO
            2 -> SettingsManager.ACTION_ERASER
            3 -> SettingsManager.ACTION_BRUSH_SIZE_DOWN
            4 -> SettingsManager.ACTION_BRUSH_SIZE_UP
            5 -> SettingsManager.ACTION_ZOOM_OUT
            6 -> SettingsManager.ACTION_ZOOM_IN
            7 -> SettingsManager.ACTION_PAN
            else -> SettingsManager.ACTION_NONE
        }
        
        if (action != SettingsManager.ACTION_NONE) {
            injector.executeAction(this, action, isDown)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.i(TAG, "UsbInputService onDestroy")
        try {
            unregisterReceiver(usbReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering receiver", e)
        }
        cursorOverlay?.hide()
        stopReading()
        closeConnection()
        injector.clear()
        broadcastStatus("disconnected")
        super.onDestroy()
    }

    private fun findInterruptEndpoints(intf: UsbInterface) {
        for (i in 0 until intf.endpointCount) {
            val endpoint = intf.getEndpoint(i)
            if (endpoint.direction == UsbConstants.USB_DIR_IN && endpoint.type == UsbConstants.USB_ENDPOINT_XFER_INT) {
                inputEndpoints.add(endpoint)
            } else if (endpoint.direction == UsbConstants.USB_DIR_OUT) {
                // Send UGEE v2 magic packet to OUT endpoints to enable absolute tablet mode
                try {
                    val magicPacket = byteArrayOf(0x02, 0xb0.toByte(), 0x04, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)
                    val bytesSent = connection?.bulkTransfer(endpoint, magicPacket, magicPacket.size, 1000)
                    Log.i(TAG, "Sent UGEE v2 magic packet to endpoint ${endpoint.address}, bytes sent: $bytesSent")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to send magic packet", e)
                }
            }
        }
    }
}