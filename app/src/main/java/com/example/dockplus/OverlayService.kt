package com.example.dockplus

import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class OverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var overlayButton: Button
    private lateinit var expandedView: View
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var expandedParams: WindowManager.LayoutParams
    private var isExpanded = false

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        setupOverlayButton()
        setupExpandedView()
    }

    private fun setupOverlayButton() {
        overlayButton = Button(this)
        overlayButton.text = "Dock+"

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP or Gravity.START
        params.x = 0
        params.y = 100

        setupDraggableButton()
        windowManager.addView(overlayButton, params)
    }

    private fun setupExpandedView() {
        expandedView = LayoutInflater.from(this).inflate(R.layout.expanded_overlay, null)
        expandedParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        expandedParams.gravity = Gravity.TOP or Gravity.START

        val appContainer = expandedView.findViewById<LinearLayout>(R.id.appContainer)
        loadSelectedApps(appContainer)

        expandedView.findViewById<Button>(R.id.closeButton).setOnClickListener {
            collapseOverlay()
        }
    }

    private fun setupDraggableButton() {
        overlayButton.setOnTouchListener(object : View.OnTouchListener {
            private var initialX: Int = 0
            private var initialY: Int = 0
            private var initialTouchX: Float = 0f
            private var initialTouchY: Float = 0f

            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                when (event?.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(overlayButton, params)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (event.eventTime - event.downTime < 200) {
                            toggleOverlay()
                        }
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun toggleOverlay() {
        if (isExpanded) {
            collapseOverlay()
        } else {
            expandOverlay()
        }
    }

    private fun expandOverlay() {
        if (!isExpanded) {
            expandedParams.x = params.x
            expandedParams.y = params.y
            windowManager.addView(expandedView, expandedParams)
            isExpanded = true
        }
    }

    private fun collapseOverlay() {
        if (isExpanded) {
            windowManager.removeView(expandedView)
            isExpanded = false
        }
    }

    private fun loadSelectedApps(container: LinearLayout) {
        val sharedPreferences = getSharedPreferences("DockPlusPrefs", MODE_PRIVATE)
        val gson = Gson()
        val json = sharedPreferences.getString("selected_apps", "[]")
        val type = object : TypeToken<List<String>>() {}.type
        val selectedApps: List<String> = gson.fromJson(json, type)

        val packageManager = packageManager

        selectedApps.forEach { packageName ->
            try {
                val appInfo = packageManager.getApplicationInfo(packageName, 0)
                val icon = packageManager.getApplicationIcon(appInfo)
                val appIcon = ImageView(this)
                appIcon.setImageDrawable(icon)
                appIcon.layoutParams = LinearLayout.LayoutParams(100, 100)
                appIcon.setOnClickListener {
                    launchApp(packageName)
                }
                container.addView(appIcon)
            } catch (e: PackageManager.NameNotFoundException) {
                e.printStackTrace()
            }
        }
    }

    private fun launchApp(packageName: String) {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ContextCompat.startActivity(this, launchIntent, null)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::overlayButton.isInitialized) {
            windowManager.removeView(overlayButton)
        }
        if (isExpanded && ::expandedView.isInitialized) {
            windowManager.removeView(expandedView)
        }
    }
}