package com.example.export

import android.graphics.Bitmap
import java.io.BufferedOutputStream
import java.io.OutputStream
import kotlin.math.roundToInt

/**
 * Self-contained pure-Kotlin GIF89a animated encoder with NETSCAPE2.0 looping
 * and standard variable-width LZW compression. Zero third-party dependencies.
 */
internal class AnimatedGifEncoder(
    rawOut: OutputStream,
    private val width: Int,
    private val height: Int,
    private val loopCount: Int = 0, // 0 = infinite loop
) {
    private val out = BufferedOutputStream(rawOut, 32 * 1024)
    private var headerWritten = false
    private val pixelBuffer = IntArray(width * height)
    private val indexBuffer = ByteArray(width * height)

    // 256-color global palette: 6x6x6 uniform RGB cube (216 colors) + 40 studio grayscale/accent steps
    private val paletteR = IntArray(256)
    private val paletteG = IntArray(256)
    private val paletteB = IntArray(256)

    init {
        var idx = 0
        for (r in 0..5) {
            val rv = (r * 255) / 5
            for (g in 0..5) {
                val gv = (g * 255) / 5
                for (b in 0..5) {
                    val bv = (b * 255) / 5
                    paletteR[idx] = rv
                    paletteG[idx] = gv
                    paletteB[idx] = bv
                    idx++
                }
            }
        }
        // Remaining 40 slots: smooth neutral grayscale steps for crisp dark UI & soft shadows
        for (i in 0 until 40) {
            val gray = ((i + 1) * 255) / 41
            paletteR[idx] = gray
            paletteG[idx] = gray
            paletteB[idx] = gray
            idx++
        }
    }

    fun addFrame(bitmap: Bitmap, delayMs: Int) {
        if (!headerWritten) {
            writeHeader()
            headerWritten = true
        }
        bitmap.getPixels(pixelBuffer, 0, width, 0, 0, width, height)
        quantizePixels()
        val delayCs = (delayMs / 10.0).roundToInt().coerceIn(2, 65535)
        writeGraphicControlExt(delayCs)
        writeImageDescriptor()
        writeLzwImageData()
    }

    fun finish() {
        if (!headerWritten) {
            writeHeader()
            headerWritten = true
        }
        out.write(0x3B) // GIF Trailer
        out.flush()
    }

    private fun writeHeader() {
        // Signature + Version: GIF89a
        out.write("GIF89a".toByteArray(Charsets.US_ASCII))
        // Logical Screen Descriptor
        writeShortLE(width)
        writeShortLE(height)
        // Packed field: Global Color Table = 1, Color Res = 7 (8-bit), Sort = 0, Size = 7 (2^(7+1) = 256)
        out.write(0xF7)
        out.write(0) // Background color index
        out.write(0) // Pixel aspect ratio

        // Global Color Table (256 * 3 bytes)
        for (i in 0 until 256) {
            out.write(paletteR[i])
            out.write(paletteG[i])
            out.write(paletteB[i])
        }

        // NETSCAPE2.0 Application Extension for looping
        out.write(0x21)
        out.write(0xFF)
        out.write(11)
        out.write("NETSCAPE2.0".toByteArray(Charsets.US_ASCII))
        out.write(3)
        out.write(1)
        writeShortLE(loopCount)
        out.write(0)
    }

    private fun writeGraphicControlExt(delayCentiseconds: Int) {
        out.write(0x21) // Extension Introducer
        out.write(0xF9) // Graphic Control Label
        out.write(4)    // Block Size
        out.write(0x04) // Disposal: do not dispose (overwrite full frame), no transparency
        writeShortLE(delayCentiseconds)
        out.write(0)    // Transparent color index (unused)
        out.write(0)    // Block Terminator
    }

    private fun writeImageDescriptor() {
        out.write(0x2C) // Image Separator
        writeShortLE(0) // Left
        writeShortLE(0) // Top
        writeShortLE(width)
        writeShortLE(height)
        out.write(0x00) // No local color table, non-interlaced
    }

    /**
     * Fast ordered-dithered RGB quantization into the 216-color cube + 40-step grayscale ramp.
     */
    private fun quantizePixels() {
        var offset = 0
        for (y in 0 until height) {
            val rowMod = (y and 1) shl 1
            for (x in 0 until width) {
                val argb = pixelBuffer[offset]
                // Tiny 2x2 Bayer bias reduces banding in sky gradients & shadows
                val bayer = when (rowMod or (x and 1)) {
                    0 -> -10
                    1 -> 8
                    2 -> 4
                    else -> -4
                }
                val r = ((argb ushr 16) and 0xFF)
                val g = ((argb ushr 8) and 0xFF)
                val b = (argb and 0xFF)

                // Near-neutral colors snap cleanly to the 40-step grayscale ramp if closer
                val maxC = maxOf(r, g, b)
                val minC = minOf(r, g, b)
                val chroma = maxC - minC

                val dr = (r + bayer).coerceIn(0, 255)
                val dg = (g + bayer).coerceIn(0, 255)
                val db = (b + bayer).coerceIn(0, 255)

                val ri = (dr * 5 + 127) / 255
                val gi = (dg * 5 + 127) / 255
                val bi = (db * 5 + 127) / 255
                val cubeIdx = ri * 36 + gi * 6 + bi

                if (chroma <= 14) {
                    val luma = ((r * 77 + g * 150 + b * 29) shr 8)
                    val grayStep = ((luma * 41 + 127) / 255 - 1).coerceIn(0, 39)
                    val grayIdx = 216 + grayStep

                    val cErr = sqDist(r, g, b, paletteR[cubeIdx], paletteG[cubeIdx], paletteB[cubeIdx])
                    val gErr = sqDist(r, g, b, paletteR[grayIdx], paletteG[grayIdx], paletteB[grayIdx])
                    indexBuffer[offset] = (if (gErr < cErr) grayIdx else cubeIdx).toByte()
                } else {
                    indexBuffer[offset] = cubeIdx.toByte()
                }
                offset++
            }
        }
    }

    private fun sqDist(r1: Int, g1: Int, b1: Int, r2: Int, g2: Int, b2: Int): Int {
        val dr = r1 - r2
        val dg = g1 - g2
        val db = b1 - b2
        return dr * dr + dg * dg + db * db
    }

    private fun writeLzwImageData() {
        val minCodeSize = 8
        out.write(minCodeSize)

        val clearCode = 1 shl minCodeSize // 256
        val eoiCode = clearCode + 1       // 257
        val maxTableSize = 4096
        val hashSize = 5003

        val hashKeys = IntArray(hashSize) { -1 }
        val hashVals = IntArray(hashSize)

        var codeSize = minCodeSize + 1
        var nextCode = eoiCode + 1

        val subBlock = ByteArray(256)
        var subBlockLen = 0
        var bitBuf = 0
        var bitCount = 0

        fun flushSubBlock() {
            if (subBlockLen > 0) {
                out.write(subBlockLen)
                out.write(subBlock, 0, subBlockLen)
                subBlockLen = 0
            }
        }

        fun emitCode(code: Int) {
            bitBuf = bitBuf or (code shl bitCount)
            bitCount += codeSize
            while (bitCount >= 8) {
                subBlock[subBlockLen++] = (bitBuf and 0xFF).toByte()
                if (subBlockLen == 255) flushSubBlock()
                bitBuf = bitBuf ushr 8
                bitCount -= 8
            }
        }

        fun resetTable() {
            hashKeys.fill(-1)
            nextCode = eoiCode + 1
            codeSize = minCodeSize + 1
        }

        emitCode(clearCode)
        val totalPixels = indexBuffer.size
        if (totalPixels > 0) {
            var prefix = indexBuffer[0].toInt() and 0xFF
            for (i in 1 until totalPixels) {
                val k = indexBuffer[i].toInt() and 0xFF
                val key = (prefix shl 8) or k
                var slot = (key * 31) % hashSize
                if (slot < 0) slot += hashSize

                var found = false
                while (hashKeys[slot] != -1) {
                    if (hashKeys[slot] == key) {
                        prefix = hashVals[slot]
                        found = true
                        break
                    }
                    slot++
                    if (slot >= hashSize) slot = 0
                }

                if (!found) {
                    emitCode(prefix)
                    if (nextCode < maxTableSize) {
                        hashKeys[slot] = key
                        hashVals[slot] = nextCode
                        nextCode++
                        if (nextCode > (1 shl codeSize) && codeSize < 12) {
                            codeSize++
                        }
                    } else {
                        emitCode(clearCode)
                        resetTable()
                    }
                    prefix = k
                }
            }
            emitCode(prefix)
        }

        emitCode(eoiCode)
        if (bitCount > 0) {
            subBlock[subBlockLen++] = (bitBuf and 0xFF).toByte()
            if (subBlockLen == 255) flushSubBlock()
        }
        flushSubBlock()
        out.write(0) // Block Terminator
    }

    private fun writeShortLE(value: Int) {
        out.write(value and 0xFF)
        out.write((value ushr 8) and 0xFF)
    }
}
