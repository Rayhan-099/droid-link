// SPDX-License-Identifier: Apache-2.0
// Original implementation by Manav Adhikari (https://github.com/adiimanav/moreland)
// Maintained and adapted as part of Droid Link by Rayhan Khan (https://github.com/Rayhan-099)

package io.github.rayhan099.droidlink.display

import java.io.DataInputStream
import java.io.EOFException
import java.io.IOException

/**
 * DroidLink Display Streaming Protocol.
 *
 * Multi-byte wire fields are big-endian (matching DataInputStream).
 * Connects over the abstract UNIX domain socket "moreland" established via ADB forward.
 */
object Protocol {
    const val SOCKET_NAME = "moreland"

    private const val MAGIC = 0x4D524C44 // "MRLD"
    const val VERSION = 1

    const val STREAM_HEADER_LEN = 16
    const val FRAME_HEADER_LEN = 16
    const val ACK_LEN = 8

    const val FLAG_KEYFRAME = 0x01

    /** Limit maximum frame allocation to prevent memory exhaustion */
    const val MAX_FRAME_BYTES = 8 * 1024 * 1024

    data class StreamHeader(
        val width: Int,
        val height: Int,
        val framerate: Int,
        val mime: String,
    )

    data class FrameHeader(
        val length: Int,
        val ptsNs: Long,
        val keyframe: Boolean,
    )

    @Throws(IOException::class)
    fun readStreamHeader(input: DataInputStream): StreamHeader {
        val magic = input.readInt()
        if (magic != MAGIC) {
            throw IOException("bad magic 0x%08x".format(magic))
        }
        val version = input.readUnsignedShort()
        if (version != VERSION) {
            throw IOException("unsupported protocol version $version (expected $VERSION)")
        }
        val width = input.readUnsignedShort()
        val height = input.readUnsignedShort()
        val framerate = input.readUnsignedShort()
        val codec = input.readUnsignedByte()
        input.skipBytes(3) // reserved

        val mime = when (codec) {
            0 -> "video/avc"
            1 -> "video/hevc"
            else -> throw IOException("unknown codec byte $codec")
        }
        if (width == 0 || height == 0 || framerate == 0) {
            throw IOException("bogus dimensions: ${width}x${height}@${framerate}")
        }
        return StreamHeader(width, height, framerate, mime)
    }

    @Throws(IOException::class)
    fun readFrameHeader(input: DataInputStream): FrameHeader {
        val length = input.readInt()
        if (length < 0 || length > MAX_FRAME_BYTES) {
            throw IOException("bogus frame length $length")
        }
        val ptsNs = input.readLong()
        val flags = input.readByte().toInt()
        input.skipBytes(3) // reserved
        return FrameHeader(length, ptsNs, (flags and FLAG_KEYFRAME) != 0)
    }

    @Throws(IOException::class)
    fun readFully(input: DataInputStream, buf: ByteArray, length: Int) {
        var off = 0
        while (off < length) {
            val n = input.read(buf, off, length - off)
            if (n < 0) throw EOFException("stream closed mid-frame at $off/$length")
            off += n
        }
    }
}
