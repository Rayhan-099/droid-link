// SPDX-License-Identifier: Apache-2.0
// Original implementation by Manav Adhikari (https://github.com/adiimanav/moreland)
// Maintained and adapted as part of Droid Link by Rayhan Khan (https://github.com/Rayhan-099)

package io.github.rayhan099.droidlink.display

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.Rational
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView

class DisplayActivity : Activity(), SurfaceHolder.Callback {

    private lateinit var surfaceView: SurfaceView
    private lateinit var status: TextView
    private var stream: VideoStream? = null
    private var touchClient: TouchClient? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.setBackgroundDrawableResource(android.R.color.black)

        val root = FrameLayout(this).apply { setBackgroundColor(Color.parseColor("#0B0F19")) }

        status = TextView(this).apply {
            text = getString(R.string.waiting_status)
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 16f
            val pad = (24 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
        }
        surfaceView = SurfaceView(this)

        root.addView(surfaceView, FrameLayout.LayoutParams(MATCH, MATCH))
        root.addView(status, FrameLayout.LayoutParams(MATCH, WRAP))
        setContentView(root)

        // Initialize touch & stylus client
        touchClient = TouchClient().also { it.start() }

        // Forward touch events from surfaceView to touchClient
        surfaceView.setOnTouchListener { v, event ->
            touchClient?.sendMotionEvent(event, v.width, v.height)
            true
        }

        // Initialize video stream with status updates
        stream = VideoStream { state, msg ->
            runOnUiThread {
                when (state) {
                    StreamState.CONNECTED -> {
                        status.visibility = View.GONE
                    }
                    StreamState.CONNECTING -> {
                        status.visibility = View.VISIBLE
                        status.text = msg ?: getString(R.string.waiting_status)
                    }
                    StreamState.SURFACE_DETACHED -> {
                        status.visibility = View.VISIBLE
                        status.text = msg ?: "Standby mode"
                    }
                    StreamState.ERROR -> {
                        status.visibility = View.VISIBLE
                        status.text = "Error: $msg\nRetrying..."
                    }
                    StreamState.DISCONNECTED -> {
                        status.visibility = View.VISIBLE
                        status.text = getString(R.string.waiting_status)
                    }
                }
            }
        }.also { it.start() }

        surfaceView.holder.addCallback(this)
        goImmersive()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) goImmersive()
    }

    @Suppress("DEPRECATION")
    private fun goImmersive() {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        )
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 10))
                    .build()
                enterPictureInPictureMode(params)
            } catch (e: Exception) {
                Log.w("DisplayActivity", "Could not enter Picture-in-Picture: ${e.message}")
            }
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (isInPictureInPictureMode) {
            status.visibility = View.GONE
        } else {
            goImmersive()
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        stream?.attachSurface(holder.surface)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        stream?.attachSurface(holder.surface)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        stream?.detachSurface()
    }

    override fun onDestroy() {
        super.onDestroy()
        stream?.stop()
        stream = null
        touchClient?.stop()
        touchClient = null
    }

    companion object {
        private const val MATCH = FrameLayout.LayoutParams.MATCH_PARENT
        private const val WRAP = FrameLayout.LayoutParams.WRAP_CONTENT
    }
}
