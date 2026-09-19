package com.xppen.inputmapper.ui

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import com.xppen.inputmapper.R
import com.xppen.inputmapper.settings.SettingsManager

class CursorOverlayManager(private val context: Context) {
    private var windowManager: WindowManager? = null
    private var cursorView: ImageView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var isShowing = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastInputMode = -1

    init {
        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        mainHandler.post {
            createCursorView()
        }
    }

    private fun createCursorView() {
        cursorView = ImageView(context).apply {
            setImageResource(R.drawable.ic_cursor_pen)
            alpha = 0.8f
        }

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }
    }

    fun show() {
        mainHandler.post {
            if (!isShowing && android.provider.Settings.canDrawOverlays(context)) {
                try {
                    windowManager?.addView(cursorView, layoutParams)
                    isShowing = true
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun hide() {
        mainHandler.post {
            if (isShowing) {
                try {
                    windowManager?.removeView(cursorView)
                    isShowing = false
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun updatePosition(x: Float, y: Float, isDown: Boolean, inputMode: Int) {
        mainHandler.post {
            if (!isShowing) show()
            
            // Adjust offset depending on the cursor type
            if (inputMode == SettingsManager.MODE_MOUSE) {
                // Mouse pointer: top-left corner is the hot spot, so no offset
                layoutParams?.x = x.toInt()
                layoutParams?.y = y.toInt()
            } else {
                // Pen pointer: center of the dot is the hot spot (dot is 16dp)
                val density = context.resources.displayMetrics.density
                val offset = (8 * density).toInt() // 8dp offset for center of 16dp circle
                layoutParams?.x = (x - offset).toInt()
                layoutParams?.y = (y - offset).toInt()
            }
            
            if (inputMode != lastInputMode) {
                lastInputMode = inputMode
                if (inputMode == SettingsManager.MODE_MOUSE) {
                    cursorView?.setImageResource(R.drawable.ic_cursor_arrow)
                    cursorView?.clearColorFilter()
                    cursorView?.imageTintList = null
                } else {
                    cursorView?.setImageResource(R.drawable.ic_cursor_pen)
                }
            }

            // In Pen mode, change color dynamically to indicate touch vs hover
            if (inputMode == SettingsManager.MODE_PEN) {
                if (isDown) {
                    cursorView?.setColorFilter(android.graphics.Color.parseColor("#FF9800")) // Orange on touch
                } else {
                    cursorView?.setColorFilter(android.graphics.Color.parseColor("#BB86FC")) // Purple on hover
                }
            }

            if (isShowing) {
                try {
                    windowManager?.updateViewLayout(cursorView, layoutParams)
                } catch (e: Exception) {
                    // View might have been removed
                }
            }
        }
    }
}
