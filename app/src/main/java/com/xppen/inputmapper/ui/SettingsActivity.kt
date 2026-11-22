package com.xppen.inputmapper.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SwitchCompat
import com.xppen.inputmapper.R
import com.xppen.inputmapper.settings.SettingsManager
import com.xppen.inputmapper.usb.UsbInputService
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var settingsManager: SettingsManager

    private lateinit var spinnerPressureCurve: Spinner
    private lateinit var seekSensitivity: SeekBar
    private lateinit var tvSensitivityLabel: TextView
    private lateinit var spinnerButton1Action: Spinner
    private lateinit var spinnerButton2Action: Spinner
    private lateinit var spinnerRotation: Spinner
    private lateinit var spinnerInputMode: Spinner
    private lateinit var spinnerAreaMode: Spinner
    private lateinit var switchHover: SwitchCompat
    private lateinit var switchCursor: SwitchCompat
    private lateinit var switchAutoConnect: SwitchCompat
    private lateinit var btnReset: Button
    private lateinit var btnSave: Button

    private lateinit var tvDaemonStatus: TextView
    private lateinit var etWirelessHost: android.widget.EditText
    private lateinit var etPairPort: android.widget.EditText
    private lateinit var etConnectPort: android.widget.EditText
    private lateinit var etPairCode: android.widget.EditText
    private lateinit var btnOpenWireless: Button
    private lateinit var btnPairDaemon: Button
    private lateinit var btnConnectDaemon: Button
    private lateinit var btnAccessibility: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        // Force dark mode
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        settingsManager = SettingsManager(this)

        initViews()
        setupSpinners()
        loadSettings()
        setupListeners()
        checkDaemonStatus()
    }

    override fun onResume() {
        super.onResume()
        // Removed automatic daemon check to prevent forced network alerts
    }

    private fun checkDaemonStatus() {
        val isRunning = com.xppen.inputmapper.daemon.LocalDaemonClient.instance.isAlive()
        if (isRunning) {
            tvDaemonStatus.text = "Estado: 🟢 Motor Autónomo Activo (Sin Shizuku)"
            tvDaemonStatus.setTextColor(android.graphics.Color.parseColor("#4CAF50"))
        } else {
            tvDaemonStatus.text = "Estado: 🟡 Motor Inactivo (Presiona Reconectar o abre Shizuku)"
            tvDaemonStatus.setTextColor(android.graphics.Color.parseColor("#FFC107"))
            
            // Try automatic bootstrap in background
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                val manager = com.xppen.inputmapper.daemon.LocalAdbManager(this@SettingsActivity)
                val success = manager.startDaemonAutomatically()
                if (success) {
                    tvDaemonStatus.text = "Estado: 🟢 Motor Autónomo Activo (Sin Shizuku)"
                    tvDaemonStatus.setTextColor(android.graphics.Color.parseColor("#4CAF50"))
                }
            }
        }
    }

    private fun initViews() {
        tvDaemonStatus = findViewById(R.id.tv_daemon_status)
        etWirelessHost = findViewById(R.id.et_wireless_host)
        etPairPort = findViewById(R.id.et_pair_port)
        etConnectPort = findViewById(R.id.et_connect_port)
        etPairCode = findViewById(R.id.et_pair_code)
        btnOpenWireless = findViewById(R.id.btn_open_wireless)
        btnPairDaemon = findViewById(R.id.btn_pair_daemon)
        btnConnectDaemon = findViewById(R.id.btn_connect_daemon)
        btnAccessibility = findViewById(R.id.btn_accessibility)

        spinnerPressureCurve = findViewById(R.id.spinner_pressure_curve)
        seekSensitivity = findViewById(R.id.seek_sensitivity)
        tvSensitivityLabel = findViewById(R.id.tv_sensitivity_label)
        spinnerButton1Action = findViewById(R.id.spinner_button1_action)
        spinnerButton2Action = findViewById(R.id.spinner_button2_action)
        spinnerRotation = findViewById(R.id.spinner_rotation)
        spinnerInputMode = findViewById(R.id.spinner_input_mode)
        spinnerAreaMode = findViewById(R.id.spinner_area_mode)
        switchHover = findViewById(R.id.switch_hover)
        switchCursor = findViewById(R.id.switch_cursor)
        switchAutoConnect = findViewById(R.id.switch_auto_connect)
        btnReset = findViewById(R.id.btn_reset)
        btnSave = findViewById(R.id.btn_save)
    }

    private fun setupSpinners() {
        // Pressure curves from strings.xml resources
        val curveAdapter = ArrayAdapter(
            this,
            R.layout.spinner_item,
            resources.getStringArray(R.array.pressure_curves)
        )
        curveAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        spinnerPressureCurve.adapter = curveAdapter

        // Button actions from strings.xml resources
        val buttonAdapter = ArrayAdapter(
            this,
            R.layout.spinner_item,
            resources.getStringArray(R.array.button_actions)
        )
        buttonAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        spinnerButton1Action.adapter = buttonAdapter
        spinnerButton2Action.adapter = buttonAdapter

        // Rotations from strings.xml resources
        val rotationAdapter = ArrayAdapter(
            this,
            R.layout.spinner_item,
            resources.getStringArray(R.array.rotation_names)
        )
        rotationAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        spinnerRotation.adapter = rotationAdapter

        // Input Mode from strings.xml resources
        val inputModeAdapter = ArrayAdapter(
            this,
            R.layout.spinner_item,
            resources.getStringArray(R.array.input_modes)
        )
        inputModeAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        spinnerInputMode.adapter = inputModeAdapter

        // Area Mode from strings.xml resources
        val areaModeAdapter = ArrayAdapter(
            this,
            R.layout.spinner_item,
            resources.getStringArray(R.array.area_modes)
        )
        areaModeAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        spinnerAreaMode.adapter = areaModeAdapter
    }

    private fun loadSettings() {
        spinnerPressureCurve.setSelection(settingsManager.penPressureCurve)
        
        val sensitivity = settingsManager.penPressureSensitivity
        seekSensitivity.progress = sensitivity
        tvSensitivityLabel.text = "Pressure Sensitivity: $sensitivity%"

        spinnerButton1Action.setSelection(settingsManager.penButton1Action)
        spinnerButton2Action.setSelection(settingsManager.penButton2Action)
        spinnerRotation.setSelection(settingsManager.tabletRotation)
        spinnerInputMode.setSelection(settingsManager.inputMode)
        spinnerAreaMode.setSelection(settingsManager.tabletAreaMode)

        switchHover.isChecked = settingsManager.enableHover
        switchCursor.isChecked = settingsManager.showCursor
        switchAutoConnect.isChecked = settingsManager.autoConnect
    }

    private fun setupListeners() {
        seekSensitivity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                tvSensitivityLabel.text = "Pressure Sensitivity: $progress%"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        btnConnectDaemon.setOnClickListener {
            tvDaemonStatus.text = "Estado: ⏳ Conectando..."
            tvDaemonStatus.setTextColor(android.graphics.Color.parseColor("#03DAC5"))

            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                val manager = com.xppen.inputmapper.daemon.LocalAdbManager(this@SettingsActivity)
                val success = manager.connectWithPort(0)

                if (success) {
                    tvDaemonStatus.text = "Estado: 🟢 Motor Autónomo Activo (Sin Shizuku)"
                    tvDaemonStatus.setTextColor(android.graphics.Color.parseColor("#4CAF50"))
                    Toast.makeText(this@SettingsActivity, "¡Motor autónomo conectado con éxito!", Toast.LENGTH_SHORT).show()
                } else {
                    tvDaemonStatus.text = "Estado: ❌ No se pudo reconectar"
                    tvDaemonStatus.setTextColor(android.graphics.Color.parseColor("#F44336"))
                    Toast.makeText(this@SettingsActivity, "Error al reconectar. Verifica los datos de Depuración Inalámbrica.", Toast.LENGTH_LONG).show()
                }
            }
        }

        btnOpenWireless.setOnClickListener {
            try {
                startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
            } catch (e: Exception) {
                Toast.makeText(this, "No se pudo abrir Ajustes de desarrollador", Toast.LENGTH_SHORT).show()
            }
        }

        btnPairDaemon.setOnClickListener {
            val host = etWirelessHost.text.toString().trim()
            val pairPort = etPairPort.text.toString().trim().toIntOrNull()
            val connectPort = etConnectPort.text.toString().trim().toIntOrNull()
            val code = etPairCode.text.toString().trim()

            if (host.isEmpty() || pairPort == null || connectPort == null || code.length != 6) {
                Toast.makeText(this, "Rellena IP, puerto de emparejamiento, puerto de conexión y código de 6 dígitos", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            tvDaemonStatus.text = "Estado: ⏳ Emparejando con $host:$pairPort..."
            tvDaemonStatus.setTextColor(android.graphics.Color.parseColor("#03DAC5"))

            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                val adbMgr = com.xppen.inputmapper.daemon.WirelessAdbManager(this@SettingsActivity)
                val paired = try { adbMgr.pairAndSave(host, pairPort, connectPort, code) } catch (_: Throwable) { false }

                if (!paired) {
                    tvDaemonStatus.text = "Estado: ❌ Emparejamiento fallido (verifica código y puertos)"
                    tvDaemonStatus.setTextColor(android.graphics.Color.parseColor("#F44336"))
                    Toast.makeText(this@SettingsActivity, "Emparejamiento fallido. Verifica el código de 6 dígitos y los puertos.", Toast.LENGTH_LONG).show()
                    return@launch
                }

                tvDaemonStatus.text = "Estado: ⏳ Emparejado. Conectando y arrancando daemon..."
                val launched = try { adbMgr.connectAndLaunchDaemon() } catch (_: Throwable) { false }

                if (launched) {
                    tvDaemonStatus.text = "Estado: 🟢 Motor Autónomo Activo (Sin Shizuku)"
                    tvDaemonStatus.setTextColor(android.graphics.Color.parseColor("#4CAF50"))
                    Toast.makeText(this@SettingsActivity, "¡Daemon arrancado con éxito via Depuración Inalámbrica!", Toast.LENGTH_SHORT).show()
                } else {
                    tvDaemonStatus.text = "Estado: ⚠ Emparejado pero no se pudo arrancar el daemon"
                    tvDaemonStatus.setTextColor(android.graphics.Color.parseColor("#FFC107"))
                    Toast.makeText(this@SettingsActivity, "Emparejamiento OK, pero el daemon no arrancó. Verifica puertos.", Toast.LENGTH_LONG).show()
                }
            }
        }

        btnReset.setOnClickListener {
            settingsManager.resetToDefaults()
            loadSettings()
            Toast.makeText(this, "Ajustes restablecidos", Toast.LENGTH_SHORT).show()
        }

        btnAccessibility.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            } catch (e: Exception) {
                Toast.makeText(this, "No se pudo abrir Accesibilidad", Toast.LENGTH_SHORT).show()
            }
        }

        btnSave.setOnClickListener {
            saveSettings()
        }
    }

    private fun saveSettings() {
        settingsManager.penPressureCurve = spinnerPressureCurve.selectedItemPosition
        settingsManager.penPressureSensitivity = seekSensitivity.progress
        settingsManager.penButton1Action = spinnerButton1Action.selectedItemPosition
        settingsManager.penButton2Action = spinnerButton2Action.selectedItemPosition
        settingsManager.tabletRotation = spinnerRotation.selectedItemPosition
        settingsManager.inputMode = spinnerInputMode.selectedItemPosition
        settingsManager.tabletAreaMode = spinnerAreaMode.selectedItemPosition
        settingsManager.enableHover = switchHover.isChecked
        settingsManager.showCursor = switchCursor.isChecked
        settingsManager.autoConnect = switchAutoConnect.isChecked

        // Notify the UsbInputService to reload settings
        val intent = Intent(this, UsbInputService::class.java).apply {
            action = "com.xppen.inputmapper.RELOAD_SETTINGS"
        }
        startService(intent)

        Toast.makeText(this, "Ajustes aplicados correctamente", Toast.LENGTH_SHORT).show()
        finish()
    }
}
