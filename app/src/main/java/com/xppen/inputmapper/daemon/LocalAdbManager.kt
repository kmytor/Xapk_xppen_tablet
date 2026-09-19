package com.xppen.inputmapper.daemon

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

/**
 * Manages the activation and lifecycle of the autonomous InputDaemon.
 * Primary mechanism: Wireless Debugging (ADB pairing) via [WirelessAdbManager], which grants
 * UID 2000 (Shell) access without root or Shizuku. Shizuku/SU remain as fallbacks.
 */
class LocalAdbManager(private val context: Context) {

    companion object {
        private const val TAG = "UsbInputService"
        const val PREFS_NAME = "LocalAdbPrefs"
        const val KEY_LAST_PORT = "last_adb_port"
    }

    private val wirelessAdb = WirelessAdbManager(context)

    fun isDaemonRunning(): Boolean {
        return LocalDaemonClient.instance.isAlive()
    }

    /**
     * Attempts to start the daemon through all available autonomous mechanisms.
     */
    suspend fun startDaemonAutomatically(): Boolean = withContext(Dispatchers.IO) {
        if (isDaemonRunning()) {
            Log.i(TAG, "Autonomous daemon is already active!")
            return@withContext true
        }

        val apkPath = context.packageCodePath
        val launchCommand = "CLASSPATH=$apkPath /system/bin/app_process /system/bin com.xppen.inputmapper.daemon.InputDaemonServer > /dev/null 2>&1 &"

        // Attempt 1: Wireless Debugging (paired) via libadb-android
        if (wirelessAdb.isPaired && wirelessAdb.savedConnectPort > 0) {
            try {
                Log.i(TAG, "Launching daemon via Wireless Debugging (ADB pairing)...")
                val ok = wirelessAdb.connectAndLaunchDaemon()
                if (ok) {
                    Log.i(TAG, "Daemon started successfully via Wireless Debugging!")
                    return@withContext true
                }
            } catch (e: Exception) {
                Log.w(TAG, "Wireless Debugging launch failed: ${e.message}")
            }
        }

        // Attempt 2: If Shizuku is currently active, bootstrap our daemon through it
        try {
            if (Shizuku.pingBinder() && Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Log.i(TAG, "Bootstrapping autonomous daemon via Shizuku...")
                val method = Shizuku::class.java.getDeclaredMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
                method.isAccessible = true
                val process = method.invoke(null, arrayOf("sh", "-c", launchCommand), null, null) as? Process
                process?.waitFor()
                Thread.sleep(300)
                if (LocalDaemonClient.instance.tryConnect()) {
                    Log.i(TAG, "Autonomous daemon bootstrapped and running!")
                    return@withContext true
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Shizuku bootstrap attempt: ${e.message}")
        }

        // Attempt 3: Root / SU fallback if available
        try {
            val suProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", launchCommand))
            suProcess.waitFor()
            Thread.sleep(300)
            if (LocalDaemonClient.instance.tryConnect()) {
                Log.i(TAG, "Daemon started via SU successfully!")
                return@withContext true
            }
        } catch (ignored: Exception) {}

        return@withContext LocalDaemonClient.instance.isAlive()
    }

    /**
     * Attempt to reconnect and launch the daemon via saved Wireless Debugging pairing.
     */
    suspend fun connectWithPort(port: Int): Boolean = withContext(Dispatchers.IO) {
        if (isDaemonRunning()) return@withContext true
        try {
            val ok = wirelessAdb.connectAndLaunchDaemon()
            if (!ok && port > 0) {
                // Manual reconnect attempt against the connect port (already paired).
                Log.i(TAG, "Retrying daemon launch via connect port $port...")
                prefsWireless().edit().putInt("connect_port", port).apply()
            }
            return@withContext ok || LocalDaemonClient.instance.isAlive()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start daemon: ${e.message}", e)
            return@withContext false
        }
    }

    private fun prefsWireless() =
        context.getSharedPreferences("WirelessAdb", Context.MODE_PRIVATE)
}
