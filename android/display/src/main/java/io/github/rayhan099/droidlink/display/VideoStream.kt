// SPDX-License-Identifier: Apache-2.0
// Original implementation by Manav Adhikari (https://github.com/adiimanav/moreland)
// Maintained and adapted as part of Droid Link by Rayhan Khan (https://github.com/Rayhan-099)

package io.github.rayhan099.droidlink.display

import android.media.MediaCodec
import android.media.MediaFormat
import android.net.LocalServerSocket
import android.net.LocalSocket
import android.util.Log
import android.view.Surface
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "DroidLinkDisplay"

enum class StreamState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    SURFACE_DETACHED,
    ERROR
}

typealias StreamListener = (StreamState, String?) -> Unit

class VideoStream(private val listener: StreamListener? = null) {

    private val running = AtomicBoolean(true)
    private var worker: Thread? = null
    private var serverSocket: LocalServerSocket? = null

    @Volatile
    private var currentSurface: Surface? = null

    @Volatile
    private var surfaceNeedsRebind = false

    fun start() {
        worker = Thread({ run() }, "droidlink-video-worker").apply { start() }
    }

    fun stop() {
        running.set(false)
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        worker?.interrupt()
        worker = null
    }

    fun attachSurface(surface: Surface) {
        currentSurface = surface
        surfaceNeedsRebind = true
        listener?.invoke(StreamState.CONNECTED, null)
    }

    fun detachSurface() {
        currentSurface = null
        surfaceNeedsRebind = true
        listener?.invoke(StreamState.SURFACE_DETACHED, "Display backgrounded; streaming standby")
    }

    private fun run() {
        while (running.get()) {
            try {
                Log.i(TAG, "listening on localabstract:${Protocol.SOCKET_NAME}")
                LocalServerSocket(Protocol.SOCKET_NAME).use { server ->
                    serverSocket = server
                    listener?.invoke(StreamState.CONNECTING, "Waiting for host connection...")
                    while (running.get()) {
                        val socket = server.accept()
                        Log.i(TAG, "Host connected to localabstract:${Protocol.SOCKET_NAME}")
                        try {
                            handleSession(socket)
                        } catch (e: Exception) {
                            Log.w(TAG, "Session ended: ${e.message}")
                            listener?.invoke(StreamState.ERROR, e.message ?: "Session ended")
                        } finally {
                            try {
                                socket.close()
                            } catch (_: Exception) {}
                        }
                    }
                }
            } catch (e: InterruptedException) {
                break
            } catch (e: Exception) {
                if (!running.get()) break
                Log.w(TAG, "Server socket error: ${e.message}; retrying in 500ms")
                listener?.invoke(StreamState.ERROR, e.message ?: "Socket error")
                try {
                    Thread.sleep(500)
                } catch (_: InterruptedException) {
                    break
                }
            } finally {
                serverSocket = null
            }
        }
        listener?.invoke(StreamState.DISCONNECTED, null)
    }

    private fun handleSession(socket: LocalSocket) {
        socket.soTimeout = 5000
        val input = DataInputStream(BufferedInputStream(socket.inputStream, 1024 * 1024))
        val output = DataOutputStream(socket.outputStream)

        val header = Protocol.readStreamHeader(input)
        Log.i(TAG, "stream header: ${header.width}x${header.height}@${header.framerate} ${header.mime}")

        // Wait until an initial surface is provided
        while (currentSurface == null && running.get()) {
            listener?.invoke(StreamState.CONNECTING, "Waiting for display surface...")
            Thread.sleep(50)
        }
        val surface = currentSurface ?: return

        val format = MediaFormat.createVideoFormat(header.mime, header.width, header.height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_FRAME_RATE, header.framerate)
            setInteger(MediaFormat.KEY_PRIORITY, 0) // Realtime priority
            setInteger(MediaFormat.KEY_LOW_LATENCY, 1)
            try {
                setInteger(MediaFormat.KEY_OPERATING_RATE, Short.MAX_VALUE.toInt())
            } catch (_: Exception) {}
        }
        try {
            format.setInteger("vendor.qti-ext-dec-low-latency.enable", 1)
        } catch (_: Exception) {}

        val codec = MediaCodec.createDecoderByType(header.mime)
        codec.configure(format, surface, null, 0)
        codec.start()
        Log.i(TAG, "MediaCodec started: ${codec.name}")
        listener?.invoke(StreamState.CONNECTED, "${header.width}x${header.height}@${header.framerate}Hz")

        var frameBuf = ByteArray(256 * 1024)
        val drainThread = Thread({ drainCodec(codec, output) }, "droidlink-codec-drain").apply { start() }

        try {
            while (running.get()) {
                if (surfaceNeedsRebind) {
                    surfaceNeedsRebind = false
                    val newSurface = currentSurface
                    if (newSurface != null && newSurface.isValid) {
                        try {
                            codec.setOutputSurface(newSurface)
                            Log.i(TAG, "Dynamic surface re-attached via setOutputSurface")
                            listener?.invoke(StreamState.CONNECTED, null)
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed setOutputSurface, restarting decoder: ${e.message}")
                            break
                        }
                    } else {
                        Log.i(TAG, "Surface detached; continuing decode without rendering to avoid stalling host")
                    }
                }

                val fh = Protocol.readFrameHeader(input)
                if (fh.length > frameBuf.size) {
                    frameBuf = ByteArray(fh.length)
                }
                Protocol.readFully(input, frameBuf, fh.length)

                val inIdx = codec.dequeueInputBuffer(10_000)
                if (inIdx >= 0) {
                    val inBuf = codec.getInputBuffer(inIdx)!!
                    inBuf.clear()
                    inBuf.put(frameBuf, 0, fh.length)
                    val flags = if (fh.keyframe) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                    val ptsUs = fh.ptsNs / 1000
                    codec.queueInputBuffer(inIdx, 0, fh.length, ptsUs, flags)
                }
            }
        } finally {
            drainThread.interrupt()
            drainThread.join(500)
            try {
                codec.stop()
            } catch (_: Exception) {}
            codec.release()
        }
    }

    private fun drainCodec(codec: MediaCodec, output: DataOutputStream) {
        val info = MediaCodec.BufferInfo()
        val ackBuf = ByteArray(Protocol.ACK_LEN)

        while (!Thread.currentThread().isInterrupted && running.get()) {
            try {
                val outIdx = codec.dequeueOutputBuffer(info, 5000)
                if (outIdx >= 0) {
                    val activeSurface = currentSurface
                    val canRender = activeSurface != null && activeSurface.isValid
                    codec.releaseOutputBuffer(outIdx, canRender)

                    val ptsNs = info.presentationTimeUs * 1000
                    java.nio.ByteBuffer.wrap(ackBuf).putLong(ptsNs)
                    try {
                        output.write(ackBuf)
                        output.flush()
                    } catch (_: IOException) {
                        break
                    }
                }
            } catch (_: Exception) {
                break
            }
        }
    }
}
