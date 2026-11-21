package com.example.renderer

import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import com.example.effects.AudioEnvelopeAnalyzer
import com.example.effects.EvaluatedAudioFx
import com.example.effects.EvaluatedParticleLayerFx
import com.example.effects.EvaluatedTextFx
import com.example.effects.EvaluatedVisualFx
import com.example.effects.ParticleEffectKind
import com.example.effects.ParticlePool
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Lightweight, GPU-friendly 2D Canvas drawer for Phases 2–5 modular effects:
 * - Phase 2 Character overlays (Eye Blink, Eye Look, Mouth Shapes)
 * - Phase 3 Visual effects (ColorMatrix Brightness/Contrast/Saturation, Drop Shadow, Outline, Glow, Blur, Vignette, Pixelate, Film Grain)
 * - Phase 4 Pooled Sprite Particles (Rain, Snow, Dust, Fireflies, Sprite Smoke, Falling Leaves)
 * - Phase 5 Transitions (Wipe, Iris, Dissolve), Kinetic Typography, and Audio-Reactive Waveforms & Beat Markers
 *
 * Uses local reusable scratch objects and [ParticlePool] to stay low-memory and low-end friendly.
 */
object EffectCanvasDrawer {

    /**
     * Builds a single combined [ColorMatrixColorFilter] for Brightness + Contrast + Saturation.
     * Returns null when all color adjustments are neutral (zero overhead).
     */
    fun buildColorMatrixFilter(vfx: EvaluatedVisualFx?): ColorMatrixColorFilter? {
        if (vfx == null || !vfx.hasColorMatrix()) return null
        val cm = ColorMatrix()
        if (abs(vfx.saturation - 1f) > 0.01f) {
            cm.setSaturation(vfx.saturation)
        }
        val c = vfx.contrast
        val b = vfx.brightness * 255f
        val t = (1f - c) * 128f + b
        if (abs(c - 1f) > 0.01f || abs(b) > 0.5f) {
            val cb = ColorMatrix(
                floatArrayOf(
                    c, 0f, 0f, 0f, t,
                    0f, c, 0f, 0f, t,
                    0f, 0f, c, 0f, t,
                    0f, 0f, 0f, 1f, 0f,
                )
            )
            cm.postConcat(cb)
        }
        return ColorMatrixColorFilter(cm)
    }

    /**
     * Applies lightweight Canvas hardware clips for Phase 5 Wipe & Iris transitions.
     * Returns true if the layer should still be drawn, false if clipped completely invisible.
     */
    fun applyTransitionClips(
        canvas: Canvas,
        vfx: EvaluatedVisualFx?,
        halfW: Float,
        halfH: Float,
    ): Boolean {
        if (vfx == null) return true
        val pad = 40f
        val left = -halfW - pad
        val top = -halfH - pad
        val right = halfW + pad
        val bottom = halfH + pad
        val totalW = right - left
        val totalH = bottom - top

        if (vfx.wipeDirection >= 0) {
            val p = vfx.wipeProgress.coerceIn(0f, 1f)
            if (p <= 0.002f) return false
            when (vfx.wipeDirection) {
                0 -> canvas.clipRect(left, top, left + totalW * p, bottom)
                1 -> canvas.clipRect(right - totalW * p, top, right, bottom)
                2 -> canvas.clipRect(left, top, right, top + totalH * p)
                3 -> canvas.clipRect(left, bottom - totalH * p, right, bottom)
            }
        }

        if (vfx.irisProgress >= 0f) {
            val p = vfx.irisProgress.coerceIn(0f, 1f)
            if (p <= 0.002f) return false
            val maxRadius = hypot(halfW + pad, halfH + pad)
            val irisPath = Path().apply {
                addCircle(0f, 0f, maxRadius * p, Path.Direction.CW)
            }
            canvas.clipPath(irisPath)
        }

        return true
    }

    /**
     * Draws lightweight backing visual effects BEFORE the main layer objects:
     * Drop Shadow, Outline, Glow halo, and soft Blur backing.
     */
    fun drawUnderlayVisualFx(
        canvas: Canvas,
        vfx: EvaluatedVisualFx?,
        halfW: Float,
        halfH: Float,
        reducedQuality: Boolean,
        drawGeometry: () -> Unit,
    ) {
        if (vfx == null) return

        // 1. Drop Shadow (single tinted offset pass; crisp on Low-End)
        if (vfx.shadowAlpha > 0.01f) {
            val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                alpha = (vfx.shadowAlpha * 255f).toInt().coerceIn(0, 255)
                colorFilter = PorterDuffColorFilter(vfx.shadowColorInt, PorterDuff.Mode.SRC_IN)
                if (!reducedQuality && vfx.shadowRadius > 1f) {
                    maskFilter = BlurMaskFilter(vfx.shadowRadius.coerceAtMost(18f), BlurMaskFilter.Blur.NORMAL)
                }
            }
            val saveId = canvas.saveLayer(-halfW - 48f, -halfH - 48f, halfW + 48f, halfH + 48f, shadowPaint)
            canvas.translate(vfx.shadowOffsetX, vfx.shadowOffsetY)
            drawGeometry()
            canvas.restoreToCount(saveId)
        }

        // 2. Glow Halo (lightweight translucent radial halo on Low-End; soft tinted pass on Mid/High)
        if (vfx.glowIntensity > 0.01f && vfx.glowRadius > 0.5f) {
            val glowAlpha = (vfx.glowIntensity * 145f).toInt().coerceIn(12, 220)
            if (reducedQuality) {
                val r = max(halfW, halfH) + vfx.glowRadius
                val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb(
                        (glowAlpha * 0.45f).toInt().coerceIn(8, 130),
                        Color.red(vfx.glowColorInt),
                        Color.green(vfx.glowColorInt),
                        Color.blue(vfx.glowColorInt),
                    )
                }
                canvas.drawCircle(0f, 0f, r * 0.85f, haloPaint)
            } else {
                val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    alpha = glowAlpha
                    colorFilter = PorterDuffColorFilter(vfx.glowColorInt, PorterDuff.Mode.SRC_IN)
                    maskFilter = BlurMaskFilter(vfx.glowRadius.coerceIn(2f, 28f), BlurMaskFilter.Blur.NORMAL)
                }
                val pad = vfx.glowRadius + 24f
                val saveId = canvas.saveLayer(-halfW - pad, -halfH - pad, halfW + pad, halfH + pad, glowPaint)
                canvas.scale(1.06f, 1.06f)
                drawGeometry()
                canvas.restoreToCount(saveId)
            }
        }

        // 3. Outline (lightweight 4-way crisp offset silhouette)
        if (vfx.outlineWidth > 0.4f) {
            val ow = vfx.outlineWidth.coerceIn(1f, 14f)
            val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                colorFilter = PorterDuffColorFilter(vfx.outlineColorInt, PorterDuff.Mode.SRC_IN)
            }
            val pad = ow + 12f
            val saveId = canvas.saveLayer(-halfW - pad, -halfH - pad, halfW + pad, halfH + pad, outlinePaint)
            val dirs = arrayOf(ow to 0f, -ow to 0f, 0f to ow, 0f to -ow)
            for ((dx, dy) in dirs) {
                canvas.save()
                canvas.translate(dx, dy)
                drawGeometry()
                canvas.restore()
            }
            canvas.restoreToCount(saveId)
        }
    }

    /**
     * Draws lightweight post-object overlays in local layer space:
     * Character Eye Blink / Eye Look / Mouth Shapes, Vignette, Pixelate, Film Grain, Dissolve,
     * Pooled Particle Systems, and Audio Waveform / Beat Markers.
     */
    fun drawOverlayEffects(
        canvas: Canvas,
        vfx: EvaluatedVisualFx?,
        particles: List<EvaluatedParticleLayerFx>,
        audioFx: EvaluatedAudioFx?,
        halfW: Float,
        halfH: Float,
        reducedQuality: Boolean,
        particleScale: Float,
        maxParticles: Int,
        particlePool: ParticlePool,
    ) {
        // 1. Character facial overlays (Eye Look, Eye Blink, Mouth Shapes)
        if (vfx != null) {
            if (vfx.hasEyeLook || vfx.eyeBlinkClose > 0.01f) {
                drawCharacterEyeOverlay(canvas, vfx, halfW, halfH)
            }
            if (vfx.mouthShapeIndex >= 0) {
                drawCharacterMouthOverlay(canvas, vfx.mouthShapeIndex, halfW, halfH)
            }
            if (vfx.blurRadius > 0.5f) {
                // Subtle soft-focus diffusion veil representing blur on lightweight 2D canvas
                val softPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb((min(1f, vfx.blurRadius / 28f) * 68f).toInt().coerceIn(8, 72), 240, 245, 255)
                }
                canvas.drawRoundRect(-halfW, -halfH, halfW, halfH, 12f, 12f, softPaint)
            }
            if (vfx.pixelateBlockSize >= 2f) {
                drawPixelateOverlay(canvas, vfx.pixelateBlockSize, halfW, halfH)
            }
            if (vfx.dissolveProgress in 0f..0.995f) {
                drawDissolveMaskOverlay(canvas, vfx.dissolveProgress, vfx.dissolveBlockSize, halfW, halfH)
            }
            if (vfx.vignetteIntensity > 0.01f) {
                drawVignetteOverlay(canvas, vfx.vignetteIntensity, vfx.vignetteRadius, halfW, halfH)
            }
            if (vfx.filmGrainIntensity > 0.01f) {
                drawFilmGrainOverlay(canvas, vfx.filmGrainIntensity, vfx.filmGrainSeed, halfW, halfH, reducedQuality)
            }
        }

        // 2. Phase 4 Pooled Sprite Particles
        for (pfx in particles) {
            drawPooledParticles(canvas, pfx, particleScale, maxParticles, reducedQuality, particlePool)
        }

        // 3. Phase 5 Audio Waveform & Beat Markers
        if (audioFx != null) {
            if (audioFx.showWaveform) {
                drawAudioWaveform(canvas, audioFx, halfW, halfH)
            }
            if (audioFx.showBeatMarkers) {
                drawAudioBeatMarker(canvas, audioFx, halfW, halfH)
            }
        }
    }

    private fun drawCharacterEyeOverlay(
        canvas: Canvas,
        vfx: EvaluatedVisualFx,
        halfW: Float,
        halfH: Float,
    ) {
        val eyeY = -halfH * 0.22f
        val eyeSpacing = halfW * 0.26f
        val eyeRx = max(6f, halfW * 0.11f)
        val eyeRy = max(5f, halfH * 0.08f)

        val scleraPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        val pupilPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#0F172A") }
        val lidPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1E293B")
            strokeWidth = max(2.5f, eyeRy * 0.35f)
            strokeCap = Paint.Cap.ROUND
            style = Paint.Style.STROKE
        }

        for (side in intArrayOf(-1, 1)) {
            val cx = side * eyeSpacing
            if (vfx.hasEyeLook) {
                canvas.drawOval(cx - eyeRx, eyeY - eyeRy, cx + eyeRx, eyeY + eyeRy, scleraPaint)
                val px = cx + vfx.eyeLookX * eyeRx * 0.48f
                val py = eyeY + vfx.eyeLookY * eyeRy * 0.42f
                canvas.drawCircle(px, py, min(eyeRx, eyeRy) * 0.56f, pupilPaint)
            }
            if (vfx.eyeBlinkClose > 0.05f) {
                val closedH = eyeRy * 2f * vfx.eyeBlinkClose
                val coverPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb((vfx.eyeBlinkClose * 235f).toInt().coerceIn(20, 240), 22, 27, 42)
                }
                canvas.drawRoundRect(
                    cx - eyeRx * 1.15f,
                    eyeY - eyeRy,
                    cx + eyeRx * 1.15f,
                    eyeY - eyeRy + closedH,
                    eyeRy * 0.6f,
                    eyeRy * 0.6f,
                    coverPaint,
                )
                if (vfx.eyeBlinkClose > 0.7f) {
                    canvas.drawLine(cx - eyeRx, eyeY + eyeRy * 0.15f, cx + eyeRx, eyeY + eyeRy * 0.15f, lidPaint)
                }
            }
        }
    }

    private fun drawCharacterMouthOverlay(
        canvas: Canvas,
        viseme: Int,
        halfW: Float,
        halfH: Float,
    ) {
        val mouthY = halfH * 0.22f
        val mw = max(10f, halfW * 0.22f)
        val mh = max(6f, halfH * 0.12f)
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#7F1D1D") }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1E293B")
            style = Paint.Style.STROKE
            strokeWidth = 3f
            strokeCap = Paint.Cap.ROUND
        }
        when (viseme) {
            0 -> canvas.drawLine(-mw * 0.7f, mouthY, mw * 0.7f, mouthY, strokePaint)
            1 -> { // Ah (open oval)
                canvas.drawOval(-mw * 0.75f, mouthY - mh * 0.65f, mw * 0.75f, mouthY + mh * 0.85f, fillPaint)
                canvas.drawOval(-mw * 0.75f, mouthY - mh * 0.65f, mw * 0.75f, mouthY + mh * 0.85f, strokePaint)
            }
            2 -> { // Oh (rounded small O)
                val r = min(mw, mh) * 0.72f
                canvas.drawCircle(0f, mouthY + mh * 0.1f, r, fillPaint)
                canvas.drawCircle(0f, mouthY + mh * 0.1f, r, strokePaint)
            }
            3 -> { // Ee (wide horizontal)
                val teethPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
                canvas.drawRoundRect(-mw, mouthY - mh * 0.35f, mw, mouthY + mh * 0.35f, mh * 0.35f, mh * 0.35f, teethPaint)
                canvas.drawRoundRect(-mw, mouthY - mh * 0.35f, mw, mouthY + mh * 0.35f, mh * 0.35f, mh * 0.35f, strokePaint)
            }
            4 -> { // Smile arc
                val smileRect = RectF(-mw, mouthY - mh * 0.6f, mw, mouthY + mh * 0.8f)
                canvas.drawArc(smileRect, 15f, 150f, false, strokePaint)
            }
        }
    }

    private fun drawVignetteOverlay(
        canvas: Canvas,
        intensity: Float,
        radiusRatio: Float,
        halfW: Float,
        halfH: Float,
    ) {
        val r = max(halfW, halfH) * 1.45f
        val edgeAlpha = (intensity * 225f).toInt().coerceIn(0, 235)
        val stop = radiusRatio.coerceIn(0.25f, 0.92f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                0f, 0f, max(1f, r),
                intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, Color.argb(edgeAlpha, 0, 0, 0)),
                floatArrayOf(0f, stop, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(-halfW, -halfH, halfW, halfH, paint)
    }

    private fun drawPixelateOverlay(
        canvas: Canvas,
        blockSize: Float,
        halfW: Float,
        halfH: Float,
    ) {
        val step = blockSize.coerceIn(4f, 40f)
        val gridPaint = Paint().apply {
            color = Color.argb(42, 0, 0, 0)
            strokeWidth = 1f
        }
        var x = -halfW
        while (x <= halfW) {
            canvas.drawLine(x, -halfH, x, halfH, gridPaint)
            x += step
        }
        var y = -halfH
        while (y <= halfH) {
            canvas.drawLine(-halfW, y, halfW, y, gridPaint)
            y += step
        }
    }

    private fun drawDissolveMaskOverlay(
        canvas: Canvas,
        progress: Float,
        blockSize: Float,
        halfW: Float,
        halfH: Float,
    ) {
        val step = blockSize.coerceIn(8f, 36f)
        val cols = max(2, ceil((halfW * 2f) / step).toInt()).coerceAtMost(24)
        val rows = max(2, ceil((halfH * 2f) / step).toInt()).coerceAtMost(24)
        val cellW = (halfW * 2f) / cols
        val cellH = (halfH * 2f) / rows
        val maskPaint = Paint().apply { color = Color.argb(215, 11, 15, 25) }
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val h = sin((r * 37.1 + c * 91.7)) * 43758.5453
                val threshold = (h - floor(h)).toFloat()
                if (threshold > progress) {
                    val x0 = -halfW + c * cellW
                    val y0 = -halfH + r * cellH
                    canvas.drawRect(x0, y0, x0 + cellW + 0.5f, y0 + cellH + 0.5f, maskPaint)
                }
            }
        }
    }

    private fun drawFilmGrainOverlay(
        canvas: Canvas,
        intensity: Float,
        seed: Int,
        halfW: Float,
        halfH: Float,
        reducedQuality: Boolean,
    ) {
        val count = ((if (reducedQuality) 36 else 78) * intensity).toInt().coerceIn(8, 90)
        val paint = Paint()
        for (i in 0 until count) {
            val h1 = sin((seed * 17.3 + i * 12.9898)) * 43758.5453
            val h2 = sin((seed * 29.1 + i * 78.233)) * 43758.5453
            val rx = (h1 - floor(h1)).toFloat()
            val ry = (h2 - floor(h2)).toFloat()
            val px = -halfW + rx * halfW * 2f
            val py = -halfH + ry * halfH * 2f
            val bright = (i % 2 == 0)
            paint.color = Color.argb(
                (intensity * 135f).toInt().coerceIn(18, 150),
                if (bright) 245 else 10,
                if (bright) 245 else 10,
                if (bright) 245 else 15,
            )
            canvas.drawRect(px, py, px + 2.2f, py + 2.2f, paint)
        }
    }

    private fun drawPooledParticles(
        canvas: Canvas,
        fx: EvaluatedParticleLayerFx,
        particleScale: Float,
        maxParticles: Int,
        reducedQuality: Boolean,
        pool: ParticlePool,
    ) {
        val count = pool.populateDeterministic(fx, particleScale = particleScale, maxAllowed = maxParticles)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val r = Color.red(fx.colorInt)
        val g = Color.green(fx.colorInt)
        val b = Color.blue(fx.colorInt)
        val leafRect = RectF()

        for (i in 0 until count) {
            val p = pool.get(i)
            val a = (p.alpha * 255f).toInt().coerceIn(6, 255)
            when (fx.kind) {
                ParticleEffectKind.RAIN -> {
                    paint.color = Color.argb(a, r, g, b)
                    paint.strokeWidth = p.size
                    paint.strokeCap = Paint.Cap.ROUND
                    canvas.drawLine(p.x, p.y, p.x + p.vx, p.y + p.vy, paint)
                }
                ParticleEffectKind.SNOW, ParticleEffectKind.DUST -> {
                    paint.color = Color.argb(a, r, g, b)
                    canvas.drawCircle(p.x, p.y, p.size, paint)
                }
                ParticleEffectKind.FIREFLIES -> {
                    // Lightweight 2-circle glow approximation (zero per-particle RadialGradient allocation)
                    paint.color = Color.argb((a * 0.28f).toInt().coerceIn(8, 110), r, g, b)
                    canvas.drawCircle(p.x, p.y, p.size * (if (reducedQuality) 2.2f else 3.0f), paint)
                    paint.color = Color.argb(a, 255, 250, 210)
                    canvas.drawCircle(p.x, p.y, p.size * 0.72f, paint)
                }
                ParticleEffectKind.SMOKE -> {
                    paint.color = Color.argb((a * 0.45f).toInt().coerceIn(6, 120), r, g, b)
                    canvas.drawCircle(p.x, p.y, p.size, paint)
                    if (!reducedQuality) {
                        paint.color = Color.argb((a * 0.25f).toInt().coerceIn(4, 70), r, g, b)
                        canvas.drawCircle(p.x + p.size * 0.28f, p.y - p.size * 0.18f, p.size * 0.72f, paint)
                    }
                }
                ParticleEffectKind.FALLING_LEAVES -> {
                    val leafColor = when (i % 3) {
                        0 -> Color.argb(a, 234, 88, 12)
                        1 -> Color.argb(a, 245, 158, 11)
                        else -> Color.argb(a, 180, 83, 9)
                    }
                    paint.color = leafColor
                    canvas.save()
                    canvas.translate(p.x, p.y)
                    canvas.rotate(p.rotationDeg)
                    leafRect.set(-p.size, -p.size * 0.48f, p.size, p.size * 0.48f)
                    canvas.drawOval(leafRect, paint)
                    canvas.restore()
                }
            }
        }
    }

    private fun drawAudioWaveform(
        canvas: Canvas,
        audioFx: EvaluatedAudioFx,
        halfW: Float,
        halfH: Float,
    ) {
        val bars = audioFx.waveformBars.coerceIn(6, 36)
        val totalW = max(120f, halfW * 1.9f)
        val gap = 4f
        val barW = max(3f, (totalW - gap * (bars - 1)) / bars)
        val baseY = halfH + 18f
        val startX = -totalW * 0.5f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val r = Color.red(audioFx.waveformColorInt)
        val g = Color.green(audioFx.waveformColorInt)
        val b = Color.blue(audioFx.waveformColorInt)

        for (i in 0 until bars) {
            val mag = AudioEnvelopeAnalyzer.barMagnitude(i, bars, audioFx.timeSec, audioFx.bpm)
            val bh = max(4f, mag * audioFx.waveformHeight)
            val x = startX + i * (barW + gap)
            paint.color = Color.argb((140 + (mag * 115f).toInt()).coerceIn(100, 255), r, g, b)
            canvas.drawRoundRect(x, baseY - bh, x + barW, baseY, barW * 0.45f, barW * 0.45f, paint)
        }
    }

    private fun drawAudioBeatMarker(
        canvas: Canvas,
        audioFx: EvaluatedAudioFx,
        halfW: Float,
        halfH: Float,
    ) {
        val pulse = audioFx.beatPulse01.coerceIn(0f, 1f)
        if (pulse <= 0.04f) return
        val radius = max(halfW, halfH) * (0.95f + (1f - pulse) * 0.35f)
        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.5f + pulse * 4f
            color = Color.argb((pulse * 195f).toInt().coerceIn(0, 210), 56, 189, 248)
        }
        canvas.drawCircle(0f, 0f, radius, ringPaint)
    }

    /**
     * Renders kinetic typography effects (Typewriter, Character Reveal, Word Reveal,
     * Wave, Tracking, per-char Bounce, per-char Rotation) with cached system font glyphs.
     */
    fun drawKineticText(
        canvas: Canvas,
        rawText: String,
        textPaint: Paint,
        textFx: EvaluatedTextFx,
    ) {
        if (rawText.isEmpty()) return
        val fm = textPaint.fontMetrics
        val baseline = -(fm.ascent + fm.descent) / 2f

        // Word reveal filtering
        var workingText = if (textFx.wordReveal01 < 0.999f) {
            val words = rawText.split(" ")
            val visibleWords = ceil(words.size * textFx.wordReveal01).toInt().coerceIn(0, words.size)
            words.take(visibleWords).joinToString(" ")
        } else rawText

        // Typewriter / character reveal filtering
        val charRatio = min(textFx.typewriterReveal01, textFx.charReveal01)
        if (charRatio < 0.999f) {
            val count = (workingText.length * charRatio).toInt().coerceIn(0, workingText.length)
            workingText = workingText.take(count)
        }
        if (textFx.showCursor && textFx.typewriterReveal01 < 0.999f) {
            workingText += "|"
        }
        if (workingText.isEmpty()) return

        val needsPerChar = abs(textFx.waveAmplitude) > 0.1f ||
            abs(textFx.trackingPx) > 0.1f ||
            abs(textFx.charBounceAmplitude) > 0.1f ||
            abs(textFx.charRotationDeg) > 0.1f ||
            abs(textFx.charScaleBoost) > 0.01f

        if (!needsPerChar) {
            canvas.drawText(workingText, 0f, baseline, textPaint)
            return
        }

        val widths = FloatArray(workingText.length)
        textPaint.getTextWidths(workingText, widths)
        val totalWidth = widths.sum() + textFx.trackingPx * max(0, workingText.length - 1)
        val origAlign = textPaint.textAlign
        textPaint.textAlign = Paint.Align.LEFT

        var cursorX = -totalWidth * 0.5f
        for (i in workingText.indices) {
            val ch = workingText[i].toString()
            val cw = widths[i]
            val waveY = if (abs(textFx.waveAmplitude) > 0.1f) {
                sin((i * textFx.waveFrequency + textFx.wavePhase).toDouble()).toFloat() * textFx.waveAmplitude
            } else 0f
            val bounceY = if (abs(textFx.charBounceAmplitude) > 0.1f) {
                -abs(sin((i * 0.55f + textFx.charBouncePhase).toDouble())).toFloat() * textFx.charBounceAmplitude
            } else 0f
            val charRot = if (abs(textFx.charRotationDeg) > 0.1f) {
                sin((i * 0.8 + PI * 0.25)).toFloat() * textFx.charRotationDeg
            } else 0f
            val charScale = 1f + textFx.charScaleBoost

            canvas.save()
            canvas.translate(cursorX + cw * 0.5f, waveY + bounceY)
            if (abs(charRot) > 0.05f) canvas.rotate(charRot)
            if (abs(charScale - 1f) > 0.005f) canvas.scale(charScale, charScale)
            canvas.drawText(ch, -cw * 0.5f, baseline, textPaint)
            canvas.restore()

            cursorX += cw + textFx.trackingPx
        }
        textPaint.textAlign = origAlign
    }
}
