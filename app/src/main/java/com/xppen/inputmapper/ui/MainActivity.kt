package com.xppen.inputmapper.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.provider.Settings
import android.net.Uri
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.WindowCompat
import com.xppen.inputmapper.R
import com.xppen.inputmapper.usb.UsbInputService
import rikka.shizuku.Shizuku

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var btnUsbScan: Button
    private lateinit var btnBluetoothScan: Button
    private lateinit var btnSettings: Button
    private lateinit var statusDot: View

    private lateinit var tvShizukuStatus: TextView
    private lateinit var shizukuStatusDot: View
    private lateinit var btnShizukuStart: Button
    private lateinit var tvXiaomiWarning: TextView

    private val shizukuPermissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        val granted = grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED
        runOnUiThread {
            updateShizukuUi(isShizukuRunning(), granted)
        }
    }

    // Receiver to listen for real-time status updates from UsbInputService
    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val status = intent.getStringExtra("status") ?: "disconnected"
            updateStatusUi(status)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Force dark mode
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        
        // Setup status bar for dark mode
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false

        // Register Shizuku listener
        try {
            Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)
        } catch (e: Throwable) {
            Log.e("MainActivity", "Failed to add Shizuku listener", e)
        }
        
        // Check for Overlay Permission and initialize UI
        checkOverlayPermission()
    }

    override fun onResume() {
        super.onResume()
        val running = isShizukuRunning()
        val granted = running && (try {
            Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
        } catch (e: Throwable) {
            false
        })
        updateShizukuUi(running, granted)
    }

    private fun checkOverlayPermission() {
        // Overlay permission check for Floating Cursor
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivityForResult(intent, 200)
        }

        tvStatus = findViewById(R.id.tv_status)
        btnUsbScan = findViewById(R.id.btn_usb_scan)
        btnBluetoothScan = findViewById(R.id.btn_bluetooth_scan)
        btnSettings = findViewById(R.id.btn_settings)
        statusDot = findViewById(R.id.status_dot)

        tvShizukuStatus = findViewById(R.id.tv_shizuku_status)
        shizukuStatusDot = findViewById(R.id.shizuku_status_dot)
        btnShizukuStart = findViewById(R.id.btn_shizuku_start)
        tvXiaomiWarning = findViewById(R.id.tv_xiaomi_warning)

        // Show warning only on Xiaomi/Redmi/Poco devices
        val isXiaomi = android.os.Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true) ||
                       android.os.Build.BRAND.equals("Xiaomi", ignoreCase = true) ||
                       android.os.Build.MANUFACTURER.equals("Poco", ignoreCase = true) ||
                       android.os.Build.BRAND.equals("Poco", ignoreCase = true) ||
                       android.os.Build.MANUFACTURER.equals("Redmi", ignoreCase = true) ||
                       android.os.Build.BRAND.equals("Redmi", ignoreCase = true)
        tvXiaomiWarning.visibility = if (isXiaomi) View.VISIBLE else View.GONE

        btnUsbScan.setOnClickListener {
            startUsbService()
        }

        btnBluetoothScan.setOnClickListener {
            // TODO: Implement Bluetooth scanning
            updateStatusUi("disconnected")
        }

        btnSettings.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }

        btnShizukuStart.setOnClickListener {
            checkAndRequestShizuku()
        }

        // Register the status broadcast receiver safely on Android 14+
        val filter = IntentFilter("com.xppen.inputmapper.STATUS_UPDATE")
        androidx.core.content.ContextCompat.registerReceiver(
            this,
            statusReceiver,
            filter,
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )

        // Automatically start service to check for any connected XP-Pen tablet on launch
        startUsbService()
    }

    private fun isShizukuRunning(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (e: Throwable) {
            false
        }
    }

    private fun checkAndRequestShizuku() {
        val running = isShizukuRunning()
        var granted = false
        if (running) {
            try {
                if (Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    granted = true
                    Toast.makeText(this, "Shizuku ya está autorizado y corriendo", Toast.LENGTH_SHORT).show()
                } else {
                    Shizuku.requestPermission(1001)
                }
            } catch (e: Throwable) {
                Log.e("MainActivity", "Failed to check Shizuku permission", e)
            }
        } else {
            // Try to open Shizuku Manager
            try {
                val intent = packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                    ?: packageManager.getLaunchIntentForPackage("moe.shizuku.manager")
                if (intent != null) {
                    startActivity(intent)
                } else {
                    Toast.makeText(this, "Shizuku Manager no está instalado", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this, "No se pudo abrir Shizuku Manager", Toast.LENGTH_SHORT).show()
            }
        }
        updateShizukuUi(running, granted)
    }

    private fun updateShizukuUi(running: Boolean, granted: Boolean) {
        if (!running) {
            tvShizukuStatus.text = "Desconectado (No corre)"
            shizukuStatusDot.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FF5555")) // Red
            btnShizukuStart.text = "Iniciar Shizuku"
            btnShizukuStart.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FF9800")) // Orange
        } else if (!granted) {
            tvShizukuStatus.text = "Requiere Autorización"
            shizukuStatusDot.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FF9800")) // Orange
            btnShizukuStart.text = "Autorizar Shizuku"
            btnShizukuStart.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#BB86FC")) // Purple
        } else {
            tvShizukuStatus.text = "Conectado (Activo)"
            shizukuStatusDot.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#03DAC6")) // Teal/Green
            btnShizukuStart.text = "Shizuku Autorizado"
            btnShizukuStart.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#333333")) // Gray
        }
    }

    private fun startUsbService() {
        val intent = Intent(this, UsbInputService::class.java)
        startService(intent)
        updateStatusUi("connecting")
    }

    private fun updateStatusUi(status: String) {
        when (status) {
            "connected" -> {
                tvStatus.text = getString(R.string.status_connected)
                statusDot.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#03DAC6")) // Teal/Green
            }
            "connecting" -> {
                tvStatus.text = getString(R.string.status_connecting)
                statusDot.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FF9800")) // Orange
            }
            else -> {
                tvStatus.text = getString(R.string.status_not_connected)
                statusDot.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FF5555")) // Red
            }
        }
    }

    override fun onDestroy() {
        try {
            Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
        } catch (e: Exception) {
            // Ignore
        }
        try {
            unregisterReceiver(statusReceiver)
        } catch (e: Exception) {
            // Ignore if not registered
        }
        super.onDestroy()
    }
}