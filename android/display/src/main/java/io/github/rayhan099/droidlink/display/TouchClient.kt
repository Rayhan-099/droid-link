// SPDX-License-Identifier: Apache-2.0
// Droid Link Touch & Stylus Client for Android
// Author: Rayhan Khan (https://github.com/Rayhan-099)

package io.github.rayhan099.droidlink.display

import android.util.Log
import android.view.MotionEvent
import java.io.BufferedOutputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Streams touch and stylus input events from Android to the Linux host input receiver
 * over ADB reverse port forwarding (default: 127.0.0.1:4820).
 */
class TouchClient(private val port: Int = 4820) {

    companion object {
        const val TAG = "DroidLinkTouch"
        const val MAGIC: Byte = 0xD1.toByte()

        // Action constants
        const val ACTION_DOWN: Byte = 1
        const val ACTION_MOVE: Byte = 2
        const val ACTION_UP: Byte = 3
        const val ACTION_CANCEL: Byte = 4
        const val ACTION_POINTER_DOWN: Byte = 5
        const val ACTION_POINTER_UP: Byte = 6

        // Tool constants
        const val TOOL_FINGER: Byte = 0
        const val TOOL_STYLUS: Byte = 1
        const val TOOL_ERASER: Byte = 2
        const val TOOL_MOUSE: Byte = 3
    }

    private val running = AtomicBoolean(true)
    private var worker: Thread? = null
    private val eventQueue = LinkedBlockingQueue<ByteArray>(256)

    fun start() {
        worker = Thread({ runLoop() }, "droidlink-touch-client").apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        running.set(false)
        worker?.interrupt()
        worker = null
        eventQueue.clear()
    }

    fun sendMotionEvent(event: MotionEvent, viewWidth: Int, viewHeight: Int) {
        if (viewWidth <= 0 || viewHeight <= 0) return

        val action = when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> ACTION_DOWN
            MotionEvent.ACTION_MOVE -> ACTION_MOVE
            MotionEvent.ACTION_UP -> ACTION_UP
            MotionEvent.ACTION_CANCEL -> ACTION_CANCEL
            MotionEvent.ACTION_POINTER_DOWN -> ACTION_POINTER_DOWN
            MotionEvent.ACTION_POINTER_UP -> ACTION_POINTER_UP
            else -> return
        }

        val pointerCount = event.pointerCount
        val pointerIndex = event.actionIndex

        val indices = when (action) {
            ACTION_POINTER_DOWN, ACTION_POINTER_UP -> listOf(pointerIndex)
            else -> (0 until pointerCount).toList()
        }

        for (idx in indices) {
            val pointerId = event.getPointerId(idx).toByte()
            val rawX = event.getX(idx)
            val rawY = event.getY(idx)

            // Clamp and normalize to [0, 65535]
            val normX = ((rawX.coerceIn(0f, viewWidth.toFloat()) / viewWidth) * 65535f).toInt()
            val normY = ((rawY.coerceIn(0f, viewHeight.toFloat()) / viewHeight) * 65535f).toInt()

            val toolType = when (event.getToolType(idx)) {
                MotionEvent.TOOL_TYPE_STYLUS -> TOOL_STYLUS
                MotionEvent.TOOL_TYPE_ERASER -> TOOL_ERASER
                MotionEvent.TOOL_TYPE_MOUSE -> TOOL_MOUSE
                else -> TOOL_FINGER
            }

            val pressure = (event.getPressure(idx).coerceIn(0f, 1f) * 65535f).toInt()
            val tiltAngle = (event.getAxisValue(MotionEvent.AXIS_TILT, idx) * 180f / Math.PI).toInt().toShort()
            val buttons = event.buttonState.toByte()

            val buf = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
            buf.put(MAGIC)
            buf.put(action)
            buf.put(toolType)
            buf.put(pointerId)
            buf.putInt(normX)
            buf.putInt(normY)
            buf.putInt(pressure)
            buf.putShort(tiltAngle)
            buf.putShort(0.toShort()) // tiltY reserved
            buf.put(buttons)
            buf.put(0.toByte()) // reserved 1
            buf.put(0.toByte()) // reserved 2
            buf.put(0.toByte()) // reserved 3

            // Non-blocking offer; drops oldest if congested
            if (!eventQueue.offer(buf.array())) {
                eventQueue.poll()
                eventQueue.offer(buf.array())
            }
        }
    }

    private fun runLoop() {
        while (running.get()) {
            var socket: Socket? = null
            try {
                socket = Socket()
                socket.connect(InetSocketAddress("127.0.0.1", port), 2000)
                socket.tcpNoDelay = true
                val out = DataOutputStream(BufferedOutputStream(socket.getOutputStream(), 1024))
                Log.i(TAG, "Touch client connected to host on port $port")

                while (running.get()) {
                    val packet = eventQueue.take()
                    out.write(packet)
                    out.flush()
                }
            } catch (_: InterruptedException) {
                break
            } catch (_: Exception) {
                // Host input receiver might not be active; retry with backoff
                try {
                    Thread.sleep(1500)
                } catch (_: InterruptedException) {
                    break
                }
            } finally {
                try {
                    socket?.close()
                } catch (_: Exception) {}
            }
        }
    }
}
