package com.xppen.inputmapper.settings

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages all user settings for the XP-Pen Input Mapper.
 * Persists settings using SharedPreferences.
 */
class SettingsManager(context: Context) {

    companion object {
        private const val PREFS_NAME = "xppen_settings"

        // Pen Settings
        const val KEY_PEN_PRESSURE_SENSITIVITY = "pen_pressure_sensitivity"
        const val KEY_PEN_PRESSURE_CURVE = "pen_pressure_curve"
        const val KEY_PEN_BUTTON1_ACTION = "pen_button1_action"
        const val KEY_PEN_BUTTON2_ACTION = "pen_button2_action"
        const val KEY_PEN_TIP_ACTION = "pen_tip_action"

        // Tablet Settings
        const val KEY_TABLET_AREA_MODE = "tablet_area_mode"
        const val KEY_TABLET_ROTATION = "tablet_rotation"
        const val KEY_INPUT_MODE = "input_mode"

        // Mapping Settings
        const val KEY_MAP_TO_SCREEN = "map_to_screen"
        const val KEY_X_OFFSET = "x_offset"
        const val KEY_Y_OFFSET = "y_offset"
        const val KEY_X_SCALE = "x_scale"
        const val KEY_Y_SCALE = "y_scale"

        // General
        const val KEY_AUTO_CONNECT = "auto_connect"
        const val KEY_SHOW_CURSOR = "show_cursor"
        const val KEY_ENABLE_HOVER = "enable_hover"

        // Button Actions
        const val ACTION_NONE = 0
        const val ACTION_LEFT_CLICK = 1
        const val ACTION_RIGHT_CLICK = 2
        const val ACTION_MIDDLE_CLICK = 3
        const val ACTION_ERASER = 4
        const val ACTION_UNDO = 5
        const val ACTION_REDO = 6
        const val ACTION_ZOOM_IN = 7
        const val ACTION_ZOOM_OUT = 8
        const val ACTION_PAN = 9
        const val ACTION_BRUSH_SIZE_UP = 10
        const val ACTION_BRUSH_SIZE_DOWN = 11

        // Pressure Curves
        const val CURVE_LINEAR = 0
        const val CURVE_SOFT = 1
        const val CURVE_FIRM = 2
        const val CURVE_CUSTOM = 3

        // Input Modes
        const val MODE_PEN = 0
        const val MODE_MOUSE = 1

        // Area Modes
        const val AREA_FULL = 0
        const val AREA_PROPORTIONAL = 1
        const val AREA_CUSTOM = 2

        // Rotation
        const val ROTATION_0 = 0
        const val ROTATION_90 = 1
        const val ROTATION_180 = 2
        const val ROTATION_270 = 3

        val BUTTON_ACTION_NAMES = arrayOf(
            "None",
            "Left Click",
            "Right Click",
            "Middle Click",
            "Eraser",
            "Undo (Ctrl+Z)",
            "Redo (Ctrl+Y)",
            "Zoom In",
            "Zoom Out",
            "Pan / Scroll",
            "Brush Size +",
            "Brush Size -"
        )

        val PRESSURE_CURVE_NAMES = arrayOf(
            "Linear",
            "Soft (light touch)",
            "Firm (hard press)",
            "Custom"
        )

        val INPUT_MODE_NAMES = arrayOf(
            "Pen (Absolute)",
            "Mouse (Relative)"
        )

        val AREA_MODE_NAMES = arrayOf(
            "Full Area",
            "Proportional to Screen",
            "Custom Area"
        )

        val ROTATION_NAMES = arrayOf(
            "0° (Normal)",
            "90° (Left-handed)",
            "180° (Inverted)",
            "270° (Right-handed)"
        )
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // --- Pen Settings ---
    var penPressureSensitivity: Int
        get() = prefs.getInt(KEY_PEN_PRESSURE_SENSITIVITY, 50)
        set(value) = prefs.edit().putInt(KEY_PEN_PRESSURE_SENSITIVITY, value).apply()

    var penPressureCurve: Int
        get() = prefs.getInt(KEY_PEN_PRESSURE_CURVE, CURVE_LINEAR)
        set(value) = prefs.edit().putInt(KEY_PEN_PRESSURE_CURVE, value).apply()

    var penButton1Action: Int
        get() = prefs.getInt(KEY_PEN_BUTTON1_ACTION, ACTION_RIGHT_CLICK)
        set(value) = prefs.edit().putInt(KEY_PEN_BUTTON1_ACTION, value).apply()

    var penButton2Action: Int
        get() = prefs.getInt(KEY_PEN_BUTTON2_ACTION, ACTION_ERASER)
        set(value) = prefs.edit().putInt(KEY_PEN_BUTTON2_ACTION, value).apply()

    var penTipAction: Int
        get() = prefs.getInt(KEY_PEN_TIP_ACTION, ACTION_LEFT_CLICK)
        set(value) = prefs.edit().putInt(KEY_PEN_TIP_ACTION, value).apply()

    // --- Tablet Settings ---
    var tabletAreaMode: Int
        get() = prefs.getInt(KEY_TABLET_AREA_MODE, AREA_FULL)
        set(value) = prefs.edit().putInt(KEY_TABLET_AREA_MODE, value).apply()

    var tabletRotation: Int
        get() = prefs.getInt(KEY_TABLET_ROTATION, ROTATION_0)
        set(value) = prefs.edit().putInt(KEY_TABLET_ROTATION, value).apply()

    var inputMode: Int
        get() = prefs.getInt(KEY_INPUT_MODE, MODE_PEN)
        set(value) = prefs.edit().putInt(KEY_INPUT_MODE, value).apply()

    // --- Mapping Settings ---
    var mapToScreen: Boolean
        get() = prefs.getBoolean(KEY_MAP_TO_SCREEN, true)
        set(value) = prefs.edit().putBoolean(KEY_MAP_TO_SCREEN, value).apply()

    var xOffset: Int
        get() = prefs.getInt(KEY_X_OFFSET, 0)
        set(value) = prefs.edit().putInt(KEY_X_OFFSET, value).apply()

    var yOffset: Int
        get() = prefs.getInt(KEY_Y_OFFSET, 0)
        set(value) = prefs.edit().putInt(KEY_Y_OFFSET, value).apply()

    var xScale: Float
        get() = prefs.getFloat(KEY_X_SCALE, 1.0f)
        set(value) = prefs.edit().putFloat(KEY_X_SCALE, value).apply()

    var yScale: Float
        get() = prefs.getFloat(KEY_Y_SCALE, 1.0f)
        set(value) = prefs.edit().putFloat(KEY_Y_SCALE, value).apply()

    // --- General Settings ---
    var autoConnect: Boolean
        get() = prefs.getBoolean(KEY_AUTO_CONNECT, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_CONNECT, value).apply()

    var showCursor: Boolean
        get() = prefs.getBoolean(KEY_SHOW_CURSOR, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_CURSOR, value).apply()

    var enableHover: Boolean
        get() = prefs.getBoolean(KEY_ENABLE_HOVER, true)
        set(value) = prefs.edit().putBoolean(KEY_ENABLE_HOVER, value).apply()

    /**
     * Apply pressure curve to a raw pressure value.
     * @param rawPressure 0.0 to 1.0
     * @return adjusted pressure 0.0 to 1.0
     */
    fun applyPressureCurve(rawPressure: Float): Float {
        val sensitivity = penPressureSensitivity / 100f
        val adjusted = rawPressure * (0.5f + sensitivity)

        return when (penPressureCurve) {
            CURVE_LINEAR -> adjusted.coerceIn(0f, 1f)
            CURVE_SOFT -> {
                // Quadratic curve - more responsive at light pressure
                val result = Math.sqrt(adjusted.toDouble()).toFloat()
                result.coerceIn(0f, 1f)
            }
            CURVE_FIRM -> {
                // Square curve - requires more pressure
                val result = adjusted * adjusted
                result.coerceIn(0f, 1f)
            }
            else -> adjusted.coerceIn(0f, 1f)
        }
    }

    /**
     * Reset all settings to defaults.
     */
    fun resetToDefaults() {
        prefs.edit().clear().apply()
    }
}
