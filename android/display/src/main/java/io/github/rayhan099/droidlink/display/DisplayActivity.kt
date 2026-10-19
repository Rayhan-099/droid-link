// SPDX-License-Identifier: Apache-2.0
// Original implementation by Manav Adhikari (https://github.com/adiimanav/moreland)
// Maintained and adapted as part of Droid Link by Rayhan Khan (https://github.com/Rayhan-099)

package io.github.rayhan099.droidlink.display

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
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

        stream = VideoStream().also { it.start() }
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

    override fun surfaceCreated(holder: SurfaceHolder) {
        status.visibility = View.GONE
        stream?.attachSurface(holder.surface)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        stream?.attachSurface(holder.surface)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        stream?.detachSurface()
        status.visibility = View.VISIBLE
    }

    override fun onDestroy() {
        super.onDestroy()
        stream?.stop()
        stream = null
    }

    companion object {
        private const val MATCH = FrameLayout.LayoutParams.MATCH_PARENT
        private const val WRAP = FrameLayout.LayoutParams.WRAP_CONTENT
    }
}
