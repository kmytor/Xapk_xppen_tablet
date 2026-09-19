package com.xppen.inputmapper.input

import android.content.Context
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.MotionEvent
import android.view.WindowManager
import com.xppen.inputmapper.settings.SettingsManager
import com.xppen.inputmapper.usb.XPPenAccessibilityService
import android.os.IBinder
import android.view.KeyEvent
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import android.widget.Toast
import android.os.Handler
import android.os.Looper
import com.xppen.inputmapper.daemon.LocalDaemonClient

/**
 * Injects motion events into the system.
 * Uses Shizuku to inject genuine stylus events (with pressure, buttons, and eraser support)
 * via IInputManager. Falls back to Accessibility Service.
 */
class MotionEventInjector {

    companion object {
        // Use UsbInputService TAG so it shows up in the user's filtered logcat
        private const val TAG = "UsbInputService"
    }

    // Cache ranges to calibrate dynamically
    private var maxPressureSeen = HidReportParser.MAX_PRESSURE
    private var wasPenDown = false
    private var lastDownTime = 0L
    private var tabletDeviceId = -1

    private var lastButton1State = false
    private var lastButton2State = false
    private var lastWarningTime = 0L

    // For relative mouse mode and coordinate filtering
    private var lastValidX = 0f
    private var lastValidY = 0f
    private var currentCursorX = -1f
    private var currentCursorY = -1f
    private var lastScaledX = 0f
    private var lastScaledY = 0f
    private var isFirstReportInProximity = true

    // Watchdog and hover tracking
    private val handler = Handler(Looper.getMainLooper())
    private val watchdogRunnable = Runnable {
        synchronized(this) {
            onWatchdogTimeout()
        }
    }
    private var wasHovering = false
    private var appContext: Context? = null
    private var lastSettings: SettingsManager? = null
    private var lastOverlay: com.xppen.inputmapper.ui.CursorOverlayManager? = null
    private var wasPenInProximity = false

    private fun handleInjectionException(context: Context, e: Exception) {
        val cause = e.cause
        if (e is SecurityException || (e is java.lang.reflect.InvocationTargetException && cause is SecurityException)) {
            val now = SystemClock.uptimeMillis()
            if (now - lastWarningTime > 15000) {
                lastWarningTime = now
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(
                        context,
                        "Xiaomi bloqueó la inyección. Activa 'Depuración USB (Ajustes de seguridad)' en Opciones de Desarrollador.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        } else {
            Log.e(TAG, "Injection exception: ${e.message}", e)
        }
    }

    private fun showShizukuStatusWarning(context: Context) {
        val now = SystemClock.uptimeMillis()
        if (now - lastWarningTime > 15000) {
            lastWarningTime = now
            val isRunning = try { Shizuku.pingBinder() } catch (t: Throwable) { false }
            val message = if (!isRunning) {
                "Shizuku no está corriendo. Inicia Shizuku desde su aplicación."
            } else {
                "XP-Pen Input Mapper requiere autorización en Shizuku. Ábrela y concédele el permiso."
            }
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun findTabletDeviceId(context: Context): Int {
        if (tabletDeviceId != -1) {
            val dev = android.view.InputDevice.getDevice(tabletDeviceId)
            if (dev != null) return tabletDeviceId
        }
        val inputManager = context.getSystemService(Context.INPUT_SERVICE) as android.hardware.input.InputManager
        for (id in inputManager.inputDeviceIds) {
            val dev = inputManager.getInputDevice(id)
            if (dev != null && (dev.name.contains("XP-Pen", true) || dev.name.contains("Ugee", true) || dev.name.contains("Deco", true))) {
                tabletDeviceId = id
                return id
            }
        }
        return -1
    }

    fun injectMotionEvent(context: Context, data: HidReportParser.ParsedData, settings: SettingsManager, overlay: com.xppen.inputmapper.ui.CursorOverlayManager?) {
        synchronized(this) {
            appContext = context.applicationContext
            lastSettings = settings
            lastOverlay = overlay

            // Reset watchdog timer on every report received
            handler.removeCallbacks(watchdogRunnable)
            handler.postDelayed(watchdogRunnable, 150)

            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val display = windowManager.defaultDisplay
            val metrics = android.util.DisplayMetrics()
            display.getRealMetrics(metrics)
            val screenWidth = metrics.widthPixels
            val screenHeight = metrics.heightPixels

            val isPenInProximity = data.inProximity && (data.x > 0 || data.y > 0)
            var targetX = 0f
            var targetY = 0f

            // Wake the screen when the pen first enters proximity (hiatus -> active)
            if (isPenInProximity && !wasPenInProximity) {
                wakeScreen(context)
            }
            wasPenInProximity = isPenInProximity

            if (!isPenInProximity) {
                isFirstReportInProximity = true
                if (wasPenDown || wasHovering) {
                    targetX = lastValidX
                    targetY = lastValidY
                } else {
                    // If pen is not in proximity and wasn't active, do nothing
                    return
                }
            } else {
                // Compute screen absolute coordinates
                val currentMaxX = HidReportParser.MAX_X
                val currentMaxY = HidReportParser.MAX_Y
                
                var normX = data.x.toFloat() / currentMaxX
                var normY = data.y.toFloat() / currentMaxY

                var rotX = normX
                var rotY = normY
                var isRotated90 = false
                when (settings.tabletRotation) {
                    SettingsManager.ROTATION_0 -> {
                        rotX = normX
                        rotY = normY
                    }
                    SettingsManager.ROTATION_90 -> {
                        rotX = normY
                        rotY = 1.0f - normX
                        isRotated90 = true
                    }
                    SettingsManager.ROTATION_180 -> {
                        rotX = 1.0f - normX
                        rotY = 1.0f - normY
                    }
                    SettingsManager.ROTATION_270 -> {
                        rotX = 1.0f - normY
                        rotY = normX
                        isRotated90 = true
                    }
                }

                if (settings.tabletAreaMode == SettingsManager.AREA_PROPORTIONAL) {
                    val screenAspect = screenWidth.toFloat() / screenHeight
                    val tabletAspect = if (isRotated90) {
                        currentMaxY.toFloat() / currentMaxX
                    } else {
                        currentMaxX.toFloat() / currentMaxY
                    }
                    
                    if (tabletAspect > screenAspect) {
                        val scaleX = screenAspect / tabletAspect
                        val border = (1f - scaleX) / 2f
                        rotX = ((rotX - border) / scaleX).coerceIn(0f, 1f)
                    } else {
                        val scaleY = tabletAspect / screenAspect
                        val border = (1f - scaleY) / 2f
                        rotY = ((rotY - border) / scaleY).coerceIn(0f, 1f)
                    }
                }

                val scaledX = (rotX * screenWidth).coerceIn(0f, screenWidth.toFloat())
                val scaledY = (rotY * screenHeight).coerceIn(0f, screenHeight.toFloat())

                if (settings.inputMode == SettingsManager.MODE_MOUSE) {
                    if (isFirstReportInProximity) {
                        lastScaledX = scaledX
                        lastScaledY = scaledY
                        isFirstReportInProximity = false
                    }
                    val dx = scaledX - lastScaledX
                    val dy = scaledY - lastScaledY
                    lastScaledX = scaledX
                    lastScaledY = scaledY

                    if (currentCursorX == -1f) {
                        currentCursorX = screenWidth / 2f
                        currentCursorY = screenHeight / 2f
                    }

                    val sensitivity = 1.5f
                    currentCursorX = (currentCursorX + dx * sensitivity).coerceIn(0f, screenWidth.toFloat())
                    currentCursorY = (currentCursorY + dy * sensitivity).coerceIn(0f, screenHeight.toFloat())

                    targetX = currentCursorX
                    targetY = currentCursorY
                } else {
                    targetX = scaledX
                    targetY = scaledY
                }

                lastValidX = targetX
                lastValidY = targetY
            }

            val isDown = isPenInProximity && (data.tipSwitch == 1 || data.pressure > 0)

            // Always update the visual cursor overlay
            try {
                overlay?.updatePosition(targetX, targetY, isDown, settings.inputMode)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update cursor overlay position: ${e.message}", e)
            }

            var action = -1
            var pressure = 0f
            var buttonState = 0

            if (isPenInProximity) {
                if ((data.buttons and 0x02) != 0) {
                    buttonState = buttonState or getButtonStateForAction(settings.penButton1Action)
                }
                if ((data.buttons and 0x04) != 0) {
                    buttonState = buttonState or getButtonStateForAction(settings.penButton2Action)
                }

                if (isDown) {
                    if (wasHovering) {
                        injectStylusEvent(context, MotionEvent.ACTION_HOVER_EXIT, targetX, targetY, 0f, buttonState)
                        wasHovering = false
                    }
                    if (!wasPenDown) {
                        lastDownTime = SystemClock.uptimeMillis()
                        wasPenDown = true
                        action = MotionEvent.ACTION_DOWN
                    } else {
                        action = MotionEvent.ACTION_MOVE
                    }
                    if (data.pressure > maxPressureSeen) {
                        maxPressureSeen = data.pressure
                    }
                    val rawPressure = if (maxPressureSeen > 0) data.pressure.toFloat() / maxPressureSeen else 0f
                    pressure = settings.applyPressureCurve(rawPressure)
                } else {
                    if (wasPenDown) {
                        action = MotionEvent.ACTION_UP
                        wasPenDown = false
                    } else if (settings.enableHover) {
                        action = MotionEvent.ACTION_HOVER_MOVE
                        wasHovering = true
                    }
                }
            } else {
                if (wasPenDown) {
                    action = MotionEvent.ACTION_UP
                    wasPenDown = false
                } else if (wasHovering) {
                    action = MotionEvent.ACTION_HOVER_EXIT
                    wasHovering = false
                }
            }

            if (action != -1) {
                injectStylusEvent(context, action, targetX, targetY, pressure, buttonState)
            }

            // Pen buttons shortcuts / click injection
            val btn1Pressed = (data.buttons and 0x02) != 0
            val btn2Pressed = (data.buttons and 0x04) != 0
            if (btn1Pressed != lastButton1State || btn2Pressed != lastButton2State) {
                if (btn1Pressed != lastButton1State) {
                    val act = settings.penButton1Action
                    if (act >= SettingsManager.ACTION_ERASER) {
                        executeAction(context, act, btn1Pressed)
                    } else if (act != SettingsManager.ACTION_NONE) {
                        executeMouseClick(context, targetX, targetY, act, btn1Pressed, findTabletDeviceId(context))
                    }
                    lastButton1State = btn1Pressed
                }
                if (btn2Pressed != lastButton2State) {
                    val act = settings.penButton2Action
                    if (act >= SettingsManager.ACTION_ERASER) {
                        executeAction(context, act, btn2Pressed)
                    } else if (act != SettingsManager.ACTION_NONE) {
                        executeMouseClick(context, targetX, targetY, act, btn2Pressed, findTabletDeviceId(context))
                    }
                    lastButton2State = btn2Pressed
                }
            }
        }
    }

    private fun injectStylusEvent(context: Context, action: Int, x: Float, y: Float, pressure: Float, buttonState: Int) {
        val eventTime = SystemClock.uptimeMillis()
        val downTime = if (action == MotionEvent.ACTION_DOWN) eventTime else if (wasPenDown) lastDownTime else eventTime
        val deviceId = findTabletDeviceId(context)

        // 1. Primary: Autonomous InputDaemon (0.05ms latency, no Shizuku required)
        if (LocalDaemonClient.instance.injectStylus(downTime, eventTime, action, x, y, pressure, buttonState, deviceId)) {
            return
        }

        // 2. Secondary fallback: Shizuku
        val inject = shizukuInjector()
        if (inject != null) {
            try {
                val properties = arrayOf(MotionEvent.PointerProperties().apply {
                    id = 0
                    toolType = MotionEvent.TOOL_TYPE_STYLUS
                })

                val coords = arrayOf(MotionEvent.PointerCoords().apply {
                    this.x = x
                    this.y = y
                    this.pressure = pressure
                    size = 1f
                })

                val motionEvent = MotionEvent.obtain(
                    downTime,
                    eventTime,
                    action,
                    1,
                    properties,
                    coords,
                    0, // metaState
                    buttonState,
                    1.0f, // xPrecision
                    1.0f, // yPrecision
                    deviceId,
                    0, // edgeFlags
                    InputDevice.SOURCE_STYLUS,
                    0 // flags
                )
                inject(motionEvent, 0)
            } catch (e: Exception) {
                handleInjectionException(context, e)
            }
        } else {
            val accessibilityService = XPPenAccessibilityService.instance
            if (accessibilityService != null) {
                // Accessibility service can only inject touch gestures (no
                // stylus pressure or buttons). Ignore hover-only movement to
                // avoid churning empty strokes and log the fallback path.
                val isTouch = action == MotionEvent.ACTION_DOWN ||
                        action == MotionEvent.ACTION_MOVE ||
                        action == MotionEvent.ACTION_UP
                if (isTouch) {
                    Log.d(TAG, "Injecting via AccessibilityService: action=$action x=$x y=$y down=${action != MotionEvent.ACTION_UP}")
                    accessibilityService.injectPointerEvent(x, y, action != MotionEvent.ACTION_UP)
                }
            } else if (action == MotionEvent.ACTION_DOWN) {
                Log.w(TAG, "No inyección disponible: activa el Servicio de Accesibilidad de XP-Pen Input Mapper o Shizuku.")
            }
        }
    }

    private fun onWatchdogTimeout() {
        Log.d(TAG, "Watchdog timeout triggered (150ms inactivity). Releasing stylus state.")
        val context = appContext ?: return
        val settings = lastSettings ?: return
        val overlay = lastOverlay
        releaseStylusState(context, settings, overlay)
    }

    private fun releaseStylusState(context: Context, settings: SettingsManager, overlay: com.xppen.inputmapper.ui.CursorOverlayManager?) {
        if (wasPenDown) {
            Log.d(TAG, "Watchdog: Releasing pen down -> ACTION_UP")
            injectStylusEvent(context, MotionEvent.ACTION_UP, lastValidX, lastValidY, 0f, 0)
            wasPenDown = false
        }
        if (wasHovering) {
            Log.d(TAG, "Watchdog: Releasing hover -> ACTION_HOVER_EXIT")
            injectStylusEvent(context, MotionEvent.ACTION_HOVER_EXIT, lastValidX, lastValidY, 0f, 0)
            wasHovering = false
        }
        try {
            overlay?.updatePosition(lastValidX, lastValidY, false, settings.inputMode)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update overlay on watchdog release: ${e.message}", e)
        }
    }

    fun clear() {
        synchronized(this) {
            handler.removeCallbacks(watchdogRunnable)
            val context = appContext
            val settings = lastSettings
            val overlay = lastOverlay
            if (context != null && settings != null) {
                releaseStylusState(context, settings, overlay)
            }
            appContext = null
            lastSettings = null
            lastOverlay = null
        }
    }

    private fun executeMouseClick(context: Context, x: Float, y: Float, action: Int, isDown: Boolean, deviceId: Int) {
        val button = when (action) {
            SettingsManager.ACTION_LEFT_CLICK -> MotionEvent.BUTTON_PRIMARY
            SettingsManager.ACTION_RIGHT_CLICK -> MotionEvent.BUTTON_SECONDARY
            SettingsManager.ACTION_MIDDLE_CLICK -> MotionEvent.BUTTON_TERTIARY
            else -> 0
        }
        if (button != 0) {
            val now = SystemClock.uptimeMillis()
            val actionCode = if (isDown) MotionEvent.ACTION_DOWN else MotionEvent.ACTION_UP

            // 1. Try autonomous daemon
            if (LocalDaemonClient.instance.injectStylus(now, now, actionCode, x, y, if (isDown) 1f else 0f, button, deviceId)) {
                return
            }

            // 2. Fallback to Shizuku reflection
            try {
                val inject = shizukuInjector() ?: return
                val properties = arrayOf(MotionEvent.PointerProperties().apply {
                    id = 0
                    toolType = MotionEvent.TOOL_TYPE_MOUSE
                })
                val coords = arrayOf(MotionEvent.PointerCoords().apply {
                    this.x = x
                    this.y = y
                    pressure = if (isDown) 1f else 0f
                    size = 1f
                })
                val motionEvent = MotionEvent.obtain(
                    now,
                    now,
                    actionCode,
                    1,
                    properties,
                    coords,
                    0,
                    button,
                    1.0f,
                    1.0f,
                    deviceId,
                    0,
                    InputDevice.SOURCE_MOUSE,
                    0
                )
                inject(motionEvent, 0)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to inject mouse click event: ${e.message}", e)
            }
        }
    }

    fun executeAction(context: Context, action: Int, isDown: Boolean) {
        when (action) {
            SettingsManager.ACTION_UNDO -> {
                injectCtrlKey(context, KeyEvent.KEYCODE_Z, isDown)
            }
            SettingsManager.ACTION_REDO -> {
                injectCtrlKey(context, KeyEvent.KEYCODE_Y, isDown)
            }
            SettingsManager.ACTION_ERASER -> {
                injectKey(context, KeyEvent.KEYCODE_E, isDown)
            }
            SettingsManager.ACTION_ZOOM_IN -> {
                injectCtrlKey(context, KeyEvent.KEYCODE_EQUALS, isDown)
            }
            SettingsManager.ACTION_ZOOM_OUT -> {
                injectCtrlKey(context, KeyEvent.KEYCODE_MINUS, isDown)
            }
            SettingsManager.ACTION_PAN -> {
                injectKey(context, KeyEvent.KEYCODE_SPACE, isDown)
            }
            SettingsManager.ACTION_BRUSH_SIZE_UP -> {
                injectKey(context, KeyEvent.KEYCODE_RIGHT_BRACKET, isDown)
            }
            SettingsManager.ACTION_BRUSH_SIZE_DOWN -> {
                injectKey(context, KeyEvent.KEYCODE_LEFT_BRACKET, isDown)
            }
        }
    }

    private fun injectKey(context: Context, keyCode: Int, isDown: Boolean) {
        val now = SystemClock.uptimeMillis()
        val action = if (isDown) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP

        // 1. Try autonomous daemon
        if (LocalDaemonClient.instance.injectKey(now, now, action, keyCode, 0)) {
            return
        }

        // 2. Fallback to Shizuku
        try {
            val inject = shizukuInjector() ?: return
            val keyEvent = KeyEvent(
                now, now, action, keyCode, 0, 0,
                android.view.KeyCharacterMap.VIRTUAL_KEYBOARD, 0, KeyEvent.FLAG_FROM_SYSTEM
            )
            inject(keyEvent, 0)
        } catch (e: Exception) {
            handleInjectionException(context, e)
        }
    }

    private fun injectCtrlKey(context: Context, keyCode: Int, isDown: Boolean) {
        val now = SystemClock.uptimeMillis()
        if (isDown) {
            val sent1 = LocalDaemonClient.instance.injectKey(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_CTRL_LEFT, 0)
            val sent2 = LocalDaemonClient.instance.injectKey(now, now, KeyEvent.ACTION_DOWN, keyCode, KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON)
            if (sent1 && sent2) return

            // Fallback to Shizuku
            try {
                val inject = shizukuInjector() ?: return
                val ctrlDown = KeyEvent(
                    now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_CTRL_LEFT, 0, 0,
                    android.view.KeyCharacterMap.VIRTUAL_KEYBOARD, 0, KeyEvent.FLAG_FROM_SYSTEM
                )
                inject(ctrlDown, 0)

                val keyDown = KeyEvent(
                    now, now, KeyEvent.ACTION_DOWN, keyCode, 0, KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON,
                    android.view.KeyCharacterMap.VIRTUAL_KEYBOARD, 0, KeyEvent.FLAG_FROM_SYSTEM
                )
                inject(keyDown, 0)
            } catch (e: Exception) {
                handleInjectionException(context, e)
            }
        } else {
            val sent1 = LocalDaemonClient.instance.injectKey(now, now, KeyEvent.ACTION_UP, keyCode, KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON)
            val sent2 = LocalDaemonClient.instance.injectKey(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_CTRL_LEFT, 0)
            if (sent1 && sent2) return

            // Fallback to Shizuku
            try {
                val inject = shizukuInjector() ?: return
                val keyUp = KeyEvent(
                    now, now, KeyEvent.ACTION_UP, keyCode, 0, KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON,
                    android.view.KeyCharacterMap.VIRTUAL_KEYBOARD, 0, KeyEvent.FLAG_FROM_SYSTEM
                )
                inject(keyUp, 0)

                val ctrlUp = KeyEvent(
                    now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_CTRL_LEFT, 0, 0,
                    android.view.KeyCharacterMap.VIRTUAL_KEYBOARD, 0, KeyEvent.FLAG_FROM_SYSTEM
                )
                inject(ctrlUp, 0)
            } catch (e: Exception) {
                handleInjectionException(context, e)
            }
        }
    }

    private fun getButtonStateForAction(action: Int): Int {
        return when (action) {
            SettingsManager.ACTION_LEFT_CLICK -> MotionEvent.BUTTON_PRIMARY
            SettingsManager.ACTION_RIGHT_CLICK -> MotionEvent.BUTTON_SECONDARY
            SettingsManager.ACTION_MIDDLE_CLICK -> MotionEvent.BUTTON_TERTIARY
            else -> 0
        }
    }

    /**
     * Resolves the IInputManager.injectInputEvent method via Shizuku, or
     * returns null if Shizuku is not available/permitted. This centralizes the
     * reflection boilerplate that was previously duplicated in every injector.
     */
    private fun shizukuInjector(): ((android.view.InputEvent, Int) -> Unit)? {
        try {
            val binder = if (Shizuku.pingBinder()) SystemServiceHelper.getSystemService("input") else null
            if (binder != null && Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                val wrappedBinder = ShizukuBinderWrapper(binder)
                val iInputManagerClass = Class.forName("android.hardware.input.IInputManager")
                val iInputManagerStubClass = Class.forName("android.hardware.input.IInputManager\$Stub")
                val iInputManager = iInputManagerStubClass.getMethod("asInterface", IBinder::class.java)
                    .invoke(null, wrappedBinder) ?: return null
                val injectMethod = iInputManagerClass.getMethod("injectInputEvent", android.view.InputEvent::class.java, Int::class.javaPrimitiveType)
                return { event, mode ->
                    try {
                        injectMethod.invoke(iInputManager, event, mode)
                    } finally {
                        if (event is MotionEvent) event.recycle()
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set up Shizuku injector: ${e.message}")
        }
        return null
    }

    /**
     * Wakes / keeps the screen on when the stylus comes into proximity.
     * Order: autonomous daemon (shell UID) -> PowerManager WakeLock -> Shizuku KEYCODE_WAKEUP.
     */
    private fun wakeScreen(context: Context) {
        try {
            // 1. Autonomous daemon (has shell UID, can call IPowerManager/wake directly)
            if (LocalDaemonClient.instance.wakeScreen()) {
                return
            }
        } catch (e: Exception) {
            Log.w(TAG, "Daemon wakeScreen failed: ${e.message}")
        }

        // 2. Grab a partial wake lock briefly so the screen stays on while drawing
        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            val wl = pm.newWakeLock(android.os.PowerManager.SCREEN_DIM_WAKE_LOCK or android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP, "xppen:wake")
            wl.setReferenceCounted(false)
            wl.acquire(1500)
            try { wl.release() } catch (ignored: Exception) {}
        } catch (e: Exception) {
            Log.w(TAG, "WakeLock failed: ${e.message}")
        }

        // 3. Shizuku fallback: inject KEYCODE_WAKEUP via IInputManager
        shizukuInjector()?.let { inject ->
            val now = SystemClock.uptimeMillis()
            inject(
                android.view.KeyEvent(
                    now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_WAKEUP, 0, 0,
                    android.view.KeyCharacterMap.VIRTUAL_KEYBOARD, 0, KeyEvent.FLAG_FROM_SYSTEM
                ),
                0
            )
        }
    }
}