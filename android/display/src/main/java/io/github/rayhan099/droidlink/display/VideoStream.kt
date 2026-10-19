// SPDX-License-Identifier: Apache-2.0
// Original implementation by Manav Adhikari (https://github.com/adiimanav/moreland)
// Maintained and adapted as part of Droid Link by Rayhan Khan (https://github.com/Rayhan-099)

package io.github.rayhan099.droidlink.display

import android.media.MediaCodec
import android.media.MediaFormat
import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.os.SystemClock
import android.util.Log
import android.view.Surface
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "DroidLinkDisplay"

class VideoStream {

    private val running = AtomicBoolean(true)
    private var worker: Thread? = null

    @Volatile
    private var currentSurface: Surface? = null

    @Volatile
    private var surfaceChanged = false

    fun start() {
        worker = Thread({ run() }, "droidlink-video-worker").apply { start() }
    }

    fun stop() {
        running.set(false)
        worker?.interrupt()
        worker = null
    }

    fun attachSurface(surface: Surface) {
        currentSurface = surface
        surfaceChanged = true
    }

    fun detachSurface() {
        currentSurface = null
        surfaceChanged = true
    }

    private fun run() {
        while (running.get()) {
            try {
                connectAndStream()
            } catch (e: InterruptedException) {
                break
            } catch (e: Exception) {
                Log.w(TAG, "stream error: ${e.message}; retrying in 1s")
                try {
                    Thread.sleep(1000)
                } catch (ie: InterruptedException) {
                    break
                }
            }
        }
    }

    private fun connectAndStream() {
        val socket = LocalSocket()
        val addr = LocalSocketAddress(
            Protocol.SOCKET_NAME,
            LocalSocketAddress.Namespace.ABSTRACT,
        )

        Log.i(TAG, "connecting to abstract socket ${Protocol.SOCKET_NAME}…")
        socket.connect(addr)
        socket.soTimeout = 5000
        Log.i(TAG, "connected to host daemon")

        val input = DataInputStream(BufferedInputStream(socket.inputStream, 1024 * 1024))
        val output = DataOutputStream(socket.outputStream)

        val header = Protocol.readStreamHeader(input)
        Log.i(TAG, "stream header: ${header.width}x${header.height}@${header.framerate} ${header.mime}")

        while (currentSurface == null && running.get()) {
            Thread.sleep(50)
        }
        val surface = currentSurface ?: return

        val format = MediaFormat.createVideoFormat(header.mime, header.width, header.height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_FRAME_RATE, header.framerate)
            setInteger(MediaFormat.KEY_LOW_LATENCY, 1)
        }

        val codec = MediaCodec.createDecoderByType(header.mime)
        codec.configure(format, surface, null, 0)
        codec.start()
        Log.i(TAG, "MediaCodec started: ${codec.name}")

        var frameBuf = ByteArray(256 * 1024)
        val info = MediaCodec.BufferInfo()
        val drainThread = Thread({ drainCodec(codec, output) }, "droidlink-codec-drain").apply { start() }

        try {
            while (running.get()) {
                if (surfaceChanged) {
                    surfaceChanged = false
                    Log.i(TAG, "surface changed mid-stream; reconnecting")
                    break
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
            try {
                socket.close()
            } catch (_: Exception) {}
        }
    }

    private fun drainCodec(codec: MediaCodec, output: DataOutputStream) {
        val info = MediaCodec.BufferInfo()
        val ackBuf = ByteArray(Protocol.ACK_LEN)

        while (!Thread.currentThread().isInterrupted && running.get()) {
            try {
                val outIdx = codec.dequeueOutputBuffer(info, 5000)
                if (outIdx >= 0) {
                    codec.releaseOutputBuffer(outIdx, true)

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
