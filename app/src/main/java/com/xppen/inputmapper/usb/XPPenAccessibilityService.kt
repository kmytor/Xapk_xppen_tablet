package com.xppen.inputmapper.usb

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class XPPenAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "UsbInputService"
        var instance: XPPenAccessibilityService? = null
            private set
    }

    private var currentStrokePath: Path? = null
    private var lastX = 0f
    private var lastY = 0f
    private var isStrokeActive = false
    private var lastStroke: GestureDescription.StrokeDescription? = null
    private var lastDispatchTime = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "Accessibility Service Connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Not used
    }

    override fun onInterrupt() {
        // Not used
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        Log.d(TAG, "Accessibility Service Destroyed")
    }

    fun injectPointerEvent(x: Float, y: Float, isDown: Boolean) {
        val currentTime = System.currentTimeMillis()

        if (!isDown) {
            if (isStrokeActive) {
                // End the stroke
                val path = Path()
                path.moveTo(lastX, lastY)
                path.lineTo(x, y)
                
                try {
                    val finalStroke = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && lastStroke != null) {
                        lastStroke!!.continueStroke(path, 0, 10, false)
                    } else {
                        GestureDescription.StrokeDescription(path, 0, 10)
                    }
                    val gesture = GestureDescription.Builder().addStroke(finalStroke).build()
                    dispatchGesture(gesture, null, null)
                } catch (e: Exception) {
                    Log.e(TAG, "Error finishing gesture", e)
                }
                
                isStrokeActive = false
                lastStroke = null
            }
            return
        }

        if (!isStrokeActive) {
            // Start a new stroke
            isStrokeActive = true
            lastX = x
            lastY = y
            lastDispatchTime = currentTime
            val path = Path()
            path.moveTo(x, y)
            path.lineTo(x, y) // Minimal movement to register a touch
            
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    lastStroke = GestureDescription.StrokeDescription(path, 0, 20, true)
                    val gesture = GestureDescription.Builder().addStroke(lastStroke!!).build()
                    dispatchGesture(gesture, null, null)
                } else {
                    val stroke = GestureDescription.StrokeDescription(path, 0, 20)
                    val gesture = GestureDescription.Builder().addStroke(stroke).build()
                    dispatchGesture(gesture, null, null)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error starting gesture", e)
            }
        } else {
            // Continue the stroke - Throttle to ~30fps to prevent queue overflow
            if (currentTime - lastDispatchTime < 30) {
                return // Skip intermediate points to reduce lag
            }
            lastDispatchTime = currentTime

            val path = Path()
            path.moveTo(lastX, lastY)
            path.lineTo(x, y)
            
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && lastStroke != null) {
                    val nextStroke = lastStroke!!.continueStroke(path, 0, 20, true)
                    val gesture = GestureDescription.Builder().addStroke(nextStroke).build()
                    dispatchGesture(gesture, null, null)
                    lastStroke = nextStroke
                } else {
                    // Fallback for old Android (will break continuous drawing, but allows dragging somewhat)
                    val stroke = GestureDescription.StrokeDescription(path, 0, 20)
                    val gesture = GestureDescription.Builder().addStroke(stroke).build()
                    dispatchGesture(gesture, null, null)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error continuing gesture", e)
            }
            lastX = x
            lastY = y
        }
    }
}
