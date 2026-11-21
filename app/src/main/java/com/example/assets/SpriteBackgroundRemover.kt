package com.example.assets

import android.graphics.Bitmap
import android.graphics.Canvas
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Lightweight, offline-first background removal modes designed for 2D sprites,
 * character sheets, hand-drawn art, and VFX overlays on low-end Android tablets.
 */
enum class BackgroundRemovalMode(
    val label: String,
    val subtitle: String,
) {
    SMART_EDGE(
        label = "Smart Edge",
        subtitle = "Flood-fills from borders, keeping interior highlights & eyes intact",
    ),
    COLOR_KEY(
        label = "Color Key",
        subtitle = "Removes matching backdrop color everywhere, including enclosed gaps",
    ),
    WHITE_BG(
        label = "White BG",
        subtitle = "Clears white or paper backgrounds while keeping line art & colors",
    ),
    BLACK_BG(
        label = "Black / VFX",
        subtitle = "Turns dark backdrops into smooth alpha for fire, glow & smoke sprites",
    ),
}

/**
 * Configurable parameters for [SpriteBackgroundRemover].
 */
data class BackgroundRemovalOptions(
    val mode: BackgroundRemovalMode = BackgroundRemovalMode.SMART_EDGE,
    /** Explicit ARGB key color, or null to auto-detect from the sprite perimeter. */
    val targetColor: Int? = null,
    /** Color distance sensitivity in `0..100`. */
    val tolerance: Int = 28,
    /** Soft alpha transition width in `0..40` for crisp anti-aliased edges. */
    val feather: Int = 12,
    /** Color-fringe decontamination strength in `0f..1f` for semi-transparent edge pixels. */
    val despill: Float = 0.35f,
    /** Optional boundary matte choke in `0..3` pixels to trim JPEG halo borders. */
    val edgeShrinkPx: Int = 0,
    /** Preserves already-transparent pixels in PNG assets. */
    val preserveExistingAlpha: Boolean = true,
)

/**
 * Result returned by the pure-Kotlin pixel buffer processor.
 */
data class BackgroundRemovalResult(
    val pixels: IntArray,
    val width: Int,
    val height: Int,
    val detectedKeyColor: Int,
    val removedPixelCount: Int,
    val totalPixelCount: Int,
) {
    val removedPercent: Float
        get() = if (totalPixelCount <= 0) 0f else (removedPixelCount * 100f) / totalPixelCount.toFloat()
}

/**
 * Zero-dependency, low-memory sprite background removal engine.
 *
 * Uses iterative flat-array BFS flood-fill (no recursion, no per-pixel object allocation)
 * and perceptually weighted RGB distance so interactive slider previews run smoothly
 * even on entry-level 3–4 GB Android tablets.
 */
object SpriteBackgroundRemover {

    /**
     * Auto-detects the most representative opaque background color along the 4 edges of the sprite.
     * Uses quantized color-bucket voting along perimeter pixels (favoring corners) so a sprite
     * touching one edge never corrupts the detected backdrop color.
     */
    fun detectBorderColor(pixels: IntArray, width: Int, height: Int): Int {
        if (pixels.isEmpty() || width <= 0 || height <= 0) return 0xFFFFFFFF.toInt()

        val bucketCounts = HashMap<Int, Int>(64)
        val bucketSumR = HashMap<Int, Long>(64)
        val bucketSumG = HashMap<Int, Long>(64)
        val bucketSumB = HashMap<Int, Long>(64)

        fun recordSample(x: Int, y: Int, weight: Int) {
            val argb = pixels[y * width + x]
            val a = (argb ushr 24) and 0xFF
            if (a < 24) return
            val r = (argb ushr 16) and 0xFF
            val g = (argb ushr 8) and 0xFF
            val b = argb and 0xFF
            val key = ((r shr 3) shl 10) or ((g shr 3) shl 5) or (b shr 3)
            bucketCounts[key] = (bucketCounts[key] ?: 0) + weight
            bucketSumR[key] = (bucketSumR[key] ?: 0L) + r.toLong() * weight
            bucketSumG[key] = (bucketSumG[key] ?: 0L) + g.toLong() * weight
            bucketSumB[key] = (bucketSumB[key] ?: 0L) + b.toLong() * weight
        }

        val stepX = max(1, width / 64)
        val stepY = max(1, height / 64)

        var x = 0
        while (x < width) {
            recordSample(x, 0, 1)
            if (height > 1) recordSample(x, height - 1, 1)
            x += stepX
        }
        var y = 0
        while (y < height) {
            recordSample(0, y, 1)
            if (width > 1) recordSample(width - 1, y, 1)
            y += stepY
        }

        // Corners receive extra voting weight because sprites usually sit centered.
        recordSample(0, 0, 4)
        recordSample(width - 1, 0, 4)
        recordSample(0, height - 1, 4)
        recordSample(width - 1, height - 1, 4)

        var bestKey = -1
        var bestCount = 0
        for ((k, count) in bucketCounts) {
            if (count > bestCount) {
                bestCount = count
                bestKey = k
            }
        }
        if (bestKey == -1 || bestCount == 0) return 0xFFFFFFFF.toInt()

        val avgR = (bucketSumR[bestKey]!! / bestCount).toInt().coerceIn(0, 255)
        val avgG = (bucketSumG[bestKey]!! / bestCount).toInt().coerceIn(0, 255)
        val avgB = (bucketSumB[bestKey]!! / bestCount).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (avgR shl 16) or (avgG shl 8) or avgB
    }

    /**
     * Pure-Kotlin ARGB pixel-buffer background remover (JVM-testable without Android runtime).
     */
    fun removeBackgroundPixels(
        sourcePixels: IntArray,
        width: Int,
        height: Int,
        options: BackgroundRemovalOptions = BackgroundRemovalOptions(),
    ): BackgroundRemovalResult {
        val total = width * height
        if (total <= 0 || sourcePixels.size < total) {
            return BackgroundRemovalResult(IntArray(0), width, height, 0xFFFFFFFF.toInt(), 0, 0)
        }

        val out = sourcePixels.copyOf(total)
        val keyColor = when (options.mode) {
            BackgroundRemovalMode.WHITE_BG -> 0xFFFFFFFF.toInt()
            BackgroundRemovalMode.BLACK_BG -> 0xFF000000.toInt()
            BackgroundRemovalMode.SMART_EDGE, BackgroundRemovalMode.COLOR_KEY ->
                options.targetColor ?: detectBorderColor(sourcePixels, width, height)
        }

        val keyR = (keyColor ushr 16) and 0xFF
        val keyG = (keyColor ushr 8) and 0xFF
        val keyB = keyColor and 0xFF

        // Map tolerance (0..100) and feather (0..40) onto perceptual RGB distance [0..765].
        val tolDist = (options.tolerance.coerceIn(0, 100) * 4.4f).coerceAtLeast(1f)
        val featherDist = (options.feather.coerceIn(0, 40) * 3.8f)
        val outerDist = tolDist + featherDist

        // alphaFactor[i] in 0..255 represents how much foreground opacity to retain at pixel i.
        val alphaMask = IntArray(total) { 255 }

        when (options.mode) {
            BackgroundRemovalMode.SMART_EDGE -> {
                computeSmartEdgeMask(
                    pixels = sourcePixels,
                    width = width,
                    height = height,
                    keyR = keyR,
                    keyG = keyG,
                    keyB = keyB,
                    tolDist = tolDist,
                    outerDist = outerDist,
                    alphaMask = alphaMask,
                )
            }
            BackgroundRemovalMode.COLOR_KEY -> {
                for (i in 0 until total) {
                    val p = sourcePixels[i]
                    val origA = (p ushr 24) and 0xFF
                    if (origA == 0) {
                        alphaMask[i] = 0
                        continue
                    }
                    val d = perceptualColorDistance(p, keyR, keyG, keyB)
                    alphaMask[i] = distanceToAlphaFactor(d, tolDist, outerDist)
                }
            }
            BackgroundRemovalMode.WHITE_BG -> {
                // Luminance & chroma check: bright low-saturation pixels become transparent;
                // dark line art and vibrant colors stay fully opaque.
                val lumaThreshold = 255f - options.tolerance.coerceIn(0, 100) * 1.45f
                val featherSpan = max(4f, options.feather.coerceIn(0, 40) * 1.8f)
                val maxChromaForBg = 22f + options.tolerance.coerceIn(0, 100) * 0.75f

                for (i in 0 until total) {
                    val p = sourcePixels[i]
                    val origA = (p ushr 24) and 0xFF
                    if (origA == 0) {
                        alphaMask[i] = 0
                        continue
                    }
                    val r = (p ushr 16) and 0xFF
                    val g = (p ushr 8) and 0xFF
                    val b = p and 0xFF
                    val maxC = max(r, max(g, b))
                    val minC = min(r, min(g, b))
                    val chroma = (maxC - minC).toFloat()
                    val luma = 0.299f * r + 0.587f * g + 0.114f * b

                    if (luma >= lumaThreshold && chroma <= maxChromaForBg) {
                        val lumaFade = ((luma - lumaThreshold) / featherSpan).coerceIn(0f, 1f)
                        val chromaFade = (1f - (chroma / max(1f, maxChromaForBg))).coerceIn(0f, 1f)
                        val removeStrength = (lumaFade * (0.45f + 0.55f * chromaFade)).coerceIn(0f, 1f)
                        alphaMask[i] = ((1f - removeStrength) * 255f).roundToInt().coerceIn(0, 255)
                    }
                }
            }
            BackgroundRemovalMode.BLACK_BG -> {
                // Dark VFX unmultiplication: converts brightness into clean alpha transparency.
                val darkFloor = options.tolerance.coerceIn(0, 100) * 1.25f
                val darkCeil = (darkFloor + max(18f, options.feather.coerceIn(0, 40) * 3.5f + 35f))
                    .coerceAtMost(255f)

                for (i in 0 until total) {
                    val p = sourcePixels[i]
                    val origA = (p ushr 24) and 0xFF
                    if (origA == 0) {
                        alphaMask[i] = 0
                        continue
                    }
                    val r = (p ushr 16) and 0xFF
                    val g = (p ushr 8) and 0xFF
                    val b = p and 0xFF
                    val energy = max(r, max(g, b)).toFloat()
                    alphaMask[i] = when {
                        energy <= darkFloor -> 0
                        energy >= darkCeil -> 255
                        else -> (((energy - darkFloor) / max(1f, darkCeil - darkFloor)) * 255f)
                            .roundToInt()
                            .coerceIn(0, 255)
                    }
                }
            }
        }

        // Optional morphological edge shrink (choke) to remove stubborn compression halos.
        val shrinkPasses = options.edgeShrinkPx.coerceIn(0, 3)
        if (shrinkPasses > 0) {
            erodeAlphaMask(alphaMask, width, height, shrinkPasses)
        }

        // Composite final alpha + optional color despill on semi-transparent fringe pixels.
        val despillStrength = options.despill.coerceIn(0f, 1f)
        var removedCount = 0
        for (i in 0 until total) {
            val orig = sourcePixels[i]
            val origA = (orig ushr 24) and 0xFF
            val maskA = alphaMask[i]
            val finalA = if (options.preserveExistingAlpha) {
                (origA * maskA) / 255
            } else {
                maskA
            }

            if (origA > 12 && finalA <= 12) {
                removedCount++
            }

            if (finalA == 0) {
                out[i] = 0x00000000
                continue
            }

            var r = (orig ushr 16) and 0xFF
            var g = (orig ushr 8) and 0xFF
            var b = orig and 0xFF

            // Decontaminate color fringe on semi-transparent boundary pixels (or bright VFX unmultiply).
            if (despillStrength > 0.01f && finalA in 1..250) {
                if (options.mode == BackgroundRemovalMode.BLACK_BG) {
                    // Unmultiply black backdrop so glows stay vivid instead of muddy gray.
                    val invA = (255f / max(24, finalA).toFloat()).coerceAtMost(3.5f)
                    val blend = despillStrength
                    r = (r + (r * invA - r) * blend).roundToInt().coerceIn(0, 255)
                    g = (g + (g * invA - g) * blend).roundToInt().coerceIn(0, 255)
                    b = (b + (b * invA - b) * blend).roundToInt().coerceIn(0, 255)
                } else {
                    val alphaFrac = finalA / 255f
                    val bgContribution = (1f - alphaFrac) * despillStrength
                    // Subtract estimated key-color bleed from the translucent edge pixel.
                    val scale = 1f / max(0.35f, 1f - bgContribution * 0.7f)
                    r = ((r - keyR * bgContribution) * scale).roundToInt().coerceIn(0, 255)
                    g = ((g - keyG * bgContribution) * scale).roundToInt().coerceIn(0, 255)
                    b = ((b - keyB * bgContribution) * scale).roundToInt().coerceIn(0, 255)
                }
            }

            out[i] = (finalA shl 24) or (r shl 16) or (g shl 8) or b
        }

        return BackgroundRemovalResult(
            pixels = out,
            width = width,
            height = height,
            detectedKeyColor = keyColor,
            removedPixelCount = removedCount,
            totalPixelCount = total,
        )
    }

    /**
     * Produces a transparent ARGB_8888 [Bitmap] with the background removed.
     */
    fun removeBackground(
        source: Bitmap,
        options: BackgroundRemovalOptions = BackgroundRemovalOptions(),
    ): Pair<Bitmap, BackgroundRemovalResult> {
        val w = source.width.coerceAtLeast(1)
        val h = source.height.coerceAtLeast(1)
        val readable = if (source.config == Bitmap.Config.ARGB_8888) {
            source
        } else {
            source.copy(Bitmap.Config.ARGB_8888, false)
        }
        val pixels = IntArray(w * h)
        readable.getPixels(pixels, 0, w, 0, 0, w, h)
        if (readable !== source) readable.recycle()

        val result = removeBackgroundPixels(pixels, w, h, options)
        val outBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        outBitmap.setPixels(result.pixels, 0, w, 0, 0, w, h)
        return outBitmap to result
    }

    /**
     * Creates a lightweight downscaled preview bitmap (default max dimension 512px) so
     * live slider adjustments stay instant and low-memory on entry-level tablets.
     */
    fun createPreviewBitmap(source: Bitmap, maxDimension: Int = 512): Bitmap {
        val w = source.width.coerceAtLeast(1)
        val h = source.height.coerceAtLeast(1)
        val longest = max(w, h)
        if (longest <= maxDimension) {
            return source.copy(Bitmap.Config.ARGB_8888, false)
        }
        val scale = maxDimension.toFloat() / longest.toFloat()
        val dstW = max(1, (w * scale).roundToInt())
        val dstH = max(1, (h * scale).roundToInt())
        val scaled = Bitmap.createBitmap(dstW, dstH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(scaled)
        canvas.drawBitmap(
            source,
            android.graphics.Rect(0, 0, w, h),
            android.graphics.Rect(0, 0, dstW, dstH),
            android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG),
        )
        return scaled
    }

    /** Samples an ARGB color at normalized coordinates `(0..1, 0..1)` from [bitmap]. */
    fun sampleColorAt(bitmap: Bitmap, normX: Float, normY: Float): Int {
        val x = (normX.coerceIn(0f, 1f) * (bitmap.width - 1)).roundToInt().coerceIn(0, bitmap.width - 1)
        val y = (normY.coerceIn(0f, 1f) * (bitmap.height - 1)).roundToInt().coerceIn(0, bitmap.height - 1)
        return bitmap.getPixel(x, y)
    }

    private fun computeSmartEdgeMask(
        pixels: IntArray,
        width: Int,
        height: Int,
        keyR: Int,
        keyG: Int,
        keyB: Int,
        tolDist: Float,
        outerDist: Float,
        alphaMask: IntArray,
    ) {
        val total = width * height
        val visited = BooleanArray(total)
        val isBg = BooleanArray(total)
        val queue = IntArray(total)
        var head = 0
        var tail = 0

        fun trySeed(idx: Int) {
            if (visited[idx]) return
            val p = pixels[idx]
            val a = (p ushr 24) and 0xFF
            if (a < 16 || perceptualColorDistance(p, keyR, keyG, keyB) <= tolDist) {
                visited[idx] = true
                isBg[idx] = true
                alphaMask[idx] = 0
                queue[tail++] = idx
            }
        }

        // Seed flood-fill from all 4 image edges.
        for (x in 0 until width) {
            trySeed(x)
            if (height > 1) trySeed((height - 1) * width + x)
        }
        for (y in 0 until height) {
            trySeed(y * width)
            if (width > 1) trySeed(y * width + (width - 1))
        }

        // 4-connected iterative BFS for exterior background region.
        while (head < tail) {
            val idx = queue[head++]
            val x = idx % width
            val y = idx / width

            if (x > 0) trySeed(idx - 1)
            if (x + 1 < width) trySeed(idx + 1)
            if (y > 0) trySeed(idx - width)
            if (y + 1 < height) trySeed(idx + width)
        }

        // Feather a 2-pixel rim directly adjacent to the exterior background so edges stay smooth.
        if (outerDist > tolDist + 0.5f) {
            for (y in 0 until height) {
                val rowOff = y * width
                for (x in 0 until width) {
                    val idx = rowOff + x
                    if (isBg[idx]) continue
                    val touchesBg =
                        (x > 0 && isBg[idx - 1]) ||
                            (x + 1 < width && isBg[idx + 1]) ||
                            (y > 0 && isBg[idx - width]) ||
                            (y + 1 < height && isBg[idx + width])
                    if (touchesBg) {
                        val d = perceptualColorDistance(pixels[idx], keyR, keyG, keyB)
                        alphaMask[idx] = distanceToAlphaFactor(d, tolDist, outerDist)
                    }
                }
            }
        }
    }

    private fun erodeAlphaMask(alphaMask: IntArray, width: Int, height: Int, passes: Int) {
        val total = width * height
        val scratch = IntArray(total)
        repeat(passes) {
            System.arraycopy(alphaMask, 0, scratch, 0, total)
            for (y in 0 until height) {
                val rowOff = y * width
                for (x in 0 until width) {
                    val idx = rowOff + x
                    var minA = scratch[idx]
                    if (minA == 0) continue
                    if (x > 0) minA = min(minA, scratch[idx - 1])
                    if (x + 1 < width) minA = min(minA, scratch[idx + 1])
                    if (y > 0) minA = min(minA, scratch[idx - width])
                    if (y + 1 < height) minA = min(minA, scratch[idx + width])
                    alphaMask[idx] = minA
                }
            }
        }
    }

    private fun distanceToAlphaFactor(dist: Float, tolDist: Float, outerDist: Float): Int {
        if (dist <= tolDist) return 0
        if (dist >= outerDist || outerDist <= tolDist) return 255
        val t = ((dist - tolDist) / (outerDist - tolDist)).coerceIn(0f, 1f)
        // Smoothstep curve for pleasing anti-aliased falloff
        val smooth = t * t * (3f - 2f * t)
        return (smooth * 255f).roundToInt().coerceIn(0, 255)
    }

    /**
     * Fast perceptually weighted Euclidean RGB distance (max ~765).
     */
    internal fun perceptualColorDistance(argb: Int, keyR: Int, keyG: Int, keyB: Int): Float {
        val r = (argb ushr 16) and 0xFF
        val g = (argb ushr 8) and 0xFF
        val b = argb and 0xFF
        val dr = (r - keyR).toFloat()
        val dg = (g - keyG).toFloat()
        val db = (b - keyB).toFloat()
        val rMean = (r + keyR) * 0.5f
        val wr = 2f + rMean / 256f
        val wg = 4f
        val wb = 2f + (255f - rMean) / 256f
        return sqrt(abs(wr * dr * dr + wg * dg * dg + wb * db * db))
    }
}
