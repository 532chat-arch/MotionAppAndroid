package com.example.renderer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.example.engine.evaluateCompositionAtFrame
import com.example.schema.CompositionRecord
import com.example.schema.MotionProject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

/**
 * Picks a visually informative default frame for a project thumbnail so projects that
 * fade in from black at frame 0 still show recognizable animated content.
 */
fun pickRepresentativeFrame(project: MotionProject, preferredFrame: Int? = null): Int {
    val comp = project.compositions.firstOrNull() ?: return 0
    val duration = max(1, comp.settings.durationFrames)
    val maxFrame = duration - 1
    if (preferredFrame != null) {
        return preferredFrame.coerceIn(0, maxFrame)
    }

    // Prefer the midpoint of keyframe activity if keyframes exist
    val allKeyframes = comp.layers.flatMap { layer ->
        if (!layer.visible) emptyList()
        else (layer.keyframes.x + layer.keyframes.y + layer.keyframes.rotation +
            layer.keyframes.scaleX + layer.keyframes.scaleY + layer.keyframes.opacity).map { it.frame }
    }
    if (allKeyframes.isNotEmpty()) {
        val sorted = allKeyframes.sorted()
        val median = sorted[sorted.size / 2]
        return median.coerceIn(0, maxFrame)
    }

    // Otherwise sample roughly 1/3 through the visible layers' span
    val visibleLayers = comp.layers.filter { it.visible }
    if (visibleLayers.isNotEmpty()) {
        val minIn = visibleLayers.minOf { it.inFrame }.coerceIn(0, maxFrame)
        val maxOut = visibleLayers.maxOf { it.outFrame }.coerceIn(minIn, duration)
        val span = max(1, maxOut - minIn)
        return (minIn + span / 3).coerceIn(0, maxFrame)
    }

    return min(24, duration / 3).coerceIn(0, maxFrame)
}

/**
 * Extracts normalized [0f..1f] keyframe positions from a composition for drawing subtle
 * keyframe ticks along a project card's dynamic scrub bar.
 */
fun extractNormalizedKeyframePositions(comp: CompositionRecord?): List<Float> {
    if (comp == null) return emptyList()
    val maxFrame = max(1, comp.settings.durationFrames - 1).toFloat()
    return comp.layers
        .asSequence()
        .filter { it.visible }
        .flatMap { layer ->
            (layer.keyframes.x + layer.keyframes.y + layer.keyframes.rotation +
                layer.keyframes.scaleX + layer.keyframes.scaleY + layer.keyframes.opacity)
                .asSequence()
                .map { it.frame }
        }
        .distinct()
        .sorted()
        .take(24)
        .map { (it / maxFrame).coerceIn(0f, 1f) }
        .toList()
}

/**
 * Thread-safe offscreen thumbnail renderer & LRU cache for Home screen project cards.
 * Keeps scrolling smooth by rendering bitmaps on Dispatchers.Default and reusing cached frames.
 */
object ProjectThumbnailCache {
    private val cache = object : LruCache<String, ImageBitmap>(48) {}

    private fun buildCacheKey(
        project: MotionProject,
        compId: String,
        frame: Int,
        widthPx: Int,
        heightPx: Int,
    ): String = "${project.id}|${project.updatedAt}|${project.hashCode()}|$compId|$frame|${widthPx}x$heightPx"

    fun getCached(
        project: MotionProject,
        compId: String,
        frame: Int,
        widthPx: Int,
        heightPx: Int,
    ): ImageBitmap? = cache.get(buildCacheKey(project, compId, frame, widthPx, heightPx))

    suspend fun renderThumbnail(
        context: Context,
        project: MotionProject,
        compId: String? = null,
        frame: Int,
        widthPx: Int = 440,
        heightPx: Int = 248,
    ): ImageBitmap? {
        val comp = compId?.let { id -> project.compositions.find { it.id == id } }
            ?: project.compositions.firstOrNull()
            ?: return null
        val clampedFrame = frame.coerceIn(0, max(0, comp.settings.durationFrames - 1))
        val key = buildCacheKey(project, comp.id, clampedFrame, widthPx, heightPx)
        cache.get(key)?.let { return it }

        val rendered = withContext(Dispatchers.Default) {
            runCatching {
                val evaluated = evaluateCompositionAtFrame(project, comp.id, clampedFrame) ?: return@runCatching null
                val safeW = widthPx.coerceIn(96, 640)
                val safeH = heightPx.coerceIn(54, 480)
                val bitmap = Bitmap.createBitmap(safeW, safeH, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)

                // Dark studio matte surround (#131313)
                canvas.drawColor(Color.rgb(19, 19, 19))

                val cw = comp.settings.width.toFloat().coerceAtLeast(1f)
                val ch = comp.settings.height.toFloat().coerceAtLeast(1f)
                val scale = min(safeW.toFloat() / cw, safeH.toFloat() / ch)
                val drawW = cw * scale
                val drawH = ch * scale
                val dx = (safeW - drawW) * 0.5f
                val dy = (safeH - drawH) * 0.5f

                canvas.save()
                canvas.translate(dx, dy)
                canvas.scale(scale, scale)
                canvas.clipRect(0f, 0f, cw, ch)
                AndroidCanvasRenderer.renderComposition(
                    canvas = canvas,
                    context = context,
                    project = project,
                    evaluated = evaluated,
                    overlays = RenderOverlayOptions(reducedQuality = true),
                )
                canvas.restore()

                // Subtle hairline frame around non-16:9 compositions (e.g. 9:16 reels or 1:1 squares)
                if (dx > 2f || dy > 2f) {
                    val borderPaint = Paint().apply {
                        style = Paint.Style.STROKE
                        strokeWidth = 1.5f
                        color = Color.argb(170, 60, 60, 60)
                    }
                    canvas.drawRect(dx, dy, dx + drawW, dy + drawH, borderPaint)
                }

                bitmap.asImageBitmap()
            }.getOrNull()
        }

        if (rendered != null) {
            cache.put(key, rendered)
        }
        return rendered
    }
}
