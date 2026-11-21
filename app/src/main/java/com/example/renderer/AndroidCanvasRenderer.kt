package com.example.renderer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.example.assets.ProceduralAssetRenderer
import com.example.effects.Matrix2D
import com.example.effects.ParticlePool
import com.example.engine.EvaluatedCompositionState
import com.example.engine.EvaluatedLayerState
import com.example.engine.EvaluatedMeshDeform
import com.example.engine.EvaluatedMovieState
import com.example.engine.LayerMotionPath
import com.example.engine.evaluateCompositionAtFrame
import com.example.schema.BlendModeType
import com.example.schema.EnvironmentDocument
import com.example.schema.LayerRole
import com.example.schema.LyricsAlign
import com.example.schema.LyricsStyle
import com.example.schema.MotionProject
import com.example.schema.SceneObjectDocument
import com.example.schema.ShapeType
import com.example.schema.TransitionType
import com.example.schema.WeatherType
import com.example.schema.WindDirection
import com.example.schema.parseHexColor
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

data class OnionSkinFrameOverlay(
    val evaluated: EvaluatedCompositionState,
    val alpha: Float,
    val tintColor: Int,
    val selectedLayerIdOnly: String? = null,
)

data class RenderOverlayOptions(
    val selectedLayerId: String? = null,
    val selectedKeyframeFrame: Int? = null,
    val showGuides: Boolean = false,
    val showMotionPath: Boolean = true,
    val motionPath: LayerMotionPath? = null,
    val reducedQuality: Boolean = false,
    val particleScale: Float = 1.0f,
    val maxParticles: Int = 160,
    val onionSkinFrames: List<OnionSkinFrameOverlay> = emptyList(),
)

enum class GizmoHandle {
    NONE,
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT,
    ROTATE,
}

object AndroidCanvasRenderer {

    private fun hash(n: Int): Double {
        val x = sin(n * 127.1 + 311.7) * 43758.5453123
        return x - floor(x)
    }

    fun renderMovieFrame(
        canvas: Canvas,
        context: Context?,
        project: MotionProject,
        movieState: EvaluatedMovieState,
        width: Int,
        height: Int,
    ) {
        val bgPaint = Paint().apply { color = Color.BLACK }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        val primary = movieState.primary ?: return
        val progress = movieState.transitionProgress.coerceIn(0.0, 1.0)

        when (movieState.transitionType) {
            TransitionType.CROSS_DISSOLVE -> {
                if (movieState.outgoing != null && progress < 1.0) {
                    renderComposition(canvas, context, project, movieState.outgoing, RenderOverlayOptions(), depth = 0)
                    val layerAlpha = (progress * 255).toInt().coerceIn(0, 255)
                    val saveId = canvas.saveLayerAlpha(0f, 0f, width.toFloat(), height.toFloat(), layerAlpha)
                    renderComposition(canvas, context, project, primary, RenderOverlayOptions(), depth = 0)
                    canvas.restoreToCount(saveId)
                } else {
                    renderComposition(canvas, context, project, primary, RenderOverlayOptions(), depth = 0)
                }
            }
            TransitionType.FADE_BLACK -> {
                if (progress < 0.5 && movieState.outgoing != null) {
                    renderComposition(canvas, context, project, movieState.outgoing, RenderOverlayOptions(), depth = 0)
                    val dim = ((progress * 2.0) * 255).toInt().coerceIn(0, 255)
                    canvas.drawColor(Color.argb(dim, 0, 0, 0))
                } else {
                    renderComposition(canvas, context, project, primary, RenderOverlayOptions(), depth = 0)
                    val fadeIn = if (movieState.outgoing != null) ((1.0 - (progress - 0.5) * 2.0).coerceIn(0.0, 1.0)) else (1.0 - progress)
                    val dim = (fadeIn * 255).toInt().coerceIn(0, 255)
                    if (dim > 0) canvas.drawColor(Color.argb(dim, 0, 0, 0))
                }
            }
            TransitionType.CUT -> {
                renderComposition(canvas, context, project, primary, RenderOverlayOptions(), depth = 0)
            }
        }
    }

    fun renderComposition(
        canvas: Canvas,
        context: Context?,
        project: MotionProject,
        evaluated: EvaluatedCompositionState,
        overlays: RenderOverlayOptions = RenderOverlayOptions(),
        depth: Int = 0,
    ) {
        val compW = evaluated.composition.settings.width.toFloat()
        val compH = evaluated.composition.settings.height.toFloat()
        val env = evaluated.environment
        val cam = evaluated.camera

        canvas.save()
        canvas.clipRect(0f, 0f, compW, compH)

        // 1. Sky gradient
        if (!env.skyTopColor.equals("transparent", ignoreCase = true) &&
            !env.skyBottomColor.equals("transparent", ignoreCase = true)
        ) {
            val skyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f, 0f, 0f, compH,
                    parseHexColor(env.skyTopColor, Color.parseColor("#0B1120")),
                    parseHexColor(env.skyBottomColor, Color.parseColor("#1E3A5F")),
                    Shader.TileMode.CLAMP,
                )
            }
            canvas.drawRect(0f, 0f, compW, compH, skyPaint)
        }

        // 2. Drifting clouds behind layers
        if (env.cloudDensity > 0.01 && !overlays.reducedQuality) {
            drawClouds(canvas, compW, compH, evaluated.frame, env)
        }

        // 2.5 Onion skin ghost frames (previous & next frames tinted behind active frame)
        if (depth == 0 && overlays.onionSkinFrames.isNotEmpty()) {
            for (ghost in overlays.onionSkinFrames) {
                val ghostAlpha = (ghost.alpha * 255f).toInt().coerceIn(8, 220)
                val ghostPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                    alpha = ghostAlpha
                    colorFilter = PorterDuffColorFilter(ghost.tintColor, PorterDuff.Mode.SRC_ATOP)
                }
                val ghostSaveId = canvas.saveLayer(0f, 0f, compW, compH, ghostPaint)
                val ghostCam = ghost.evaluated.camera

                canvas.save()
                canvas.translate(compW / 2f, compH / 2f)
                canvas.scale(ghostCam.zoom.toFloat(), ghostCam.zoom.toFloat())
                canvas.rotate(-ghostCam.rotationDeg.toFloat())
                canvas.translate(-compW / 2f, -compH / 2f)

                for (layerState in ghost.evaluated.layers) {
                    if (!layerState.isActiveAtFrame) continue
                    if (ghost.selectedLayerIdOnly != null) {
                        if (layerState.layer.id != ghost.selectedLayerIdOnly) continue
                    } else if (layerState.layer.role == LayerRole.BACKGROUND) {
                        val kfs = layerState.layer.keyframes
                        val hasAnim = kfs.x.isNotEmpty() || kfs.y.isNotEmpty() ||
                            kfs.rotation.isNotEmpty() || kfs.scaleX.isNotEmpty() || kfs.scaleY.isNotEmpty()
                        if (!hasAnim) continue
                    }

                    val wt = layerState.worldTransform
                    if (wt.opacity <= 0.004) continue

                    val parallaxFactor = 1.0 + layerState.depth * ghostCam.parallaxStrength * 0.45
                    val drawX = (wt.x - ghostCam.x * parallaxFactor).toFloat()
                    val drawY = (wt.y - ghostCam.y * parallaxFactor).toFloat()

                    val subSaveId = if (wt.opacity < 0.995) {
                        canvas.saveLayerAlpha(0f, 0f, compW, compH, (wt.opacity * 255).toInt().coerceIn(0, 255))
                    } else -1

                    canvas.save()
                    canvas.translate(drawX, drawY)
                    canvas.rotate(wt.rotationDeg.toFloat())
                    canvas.scale(wt.scaleX.toFloat(), wt.scaleY.toFloat())

                    for (obj in layerState.layer.objects) {
                        drawSceneObject(canvas, context, project, ghost.evaluated, layerState, obj, depth + 1, reducedQuality = true)
                    }

                    canvas.restore()
                    if (subSaveId >= 0) {
                        canvas.restoreToCount(subSaveId)
                    }
                }

                canvas.restore()
                canvas.restoreToCount(ghostSaveId)
            }
        }

        // 3. Camera transform + layers bottom-to-top
        val layerMatrix2D = Matrix2D()
        val layerAndroidMatrix = Matrix()
        val matrixScratch9 = FloatArray(9)
        val layerParticlePool = ParticlePool()

        canvas.save()
        canvas.translate(compW / 2f, compH / 2f)
        canvas.scale(cam.zoom.toFloat(), cam.zoom.toFloat())
        canvas.rotate(-cam.rotationDeg.toFloat())
        canvas.translate(-compW / 2f, -compH / 2f)

        for (layerState in evaluated.layers) {
            if (!layerState.isActiveAtFrame) continue
            val wt = layerState.worldTransform
            if (wt.opacity <= 0.004) continue

            val parallaxFactor = 1.0 + layerState.depth * cam.parallaxStrength * 0.45
            val drawX = wt.x - cam.x * parallaxFactor
            val drawY = wt.y - cam.y * parallaxFactor
            val vfx = layerState.visualFx
            val colorMatrixFilter = EffectCanvasDrawer.buildColorMatrixFilter(vfx)

            val needsBlendLayer = layerState.blendMode != BlendModeType.NORMAL || wt.opacity < 0.995 || colorMatrixFilter != null
            val layerPaint = if (needsBlendLayer) {
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                    alpha = (wt.opacity * 255).toInt().coerceIn(0, 255)
                    xfermode = toPorterDuff(layerState.blendMode)?.let { PorterDuffXfermode(it) }
                    if (colorMatrixFilter != null) colorFilter = colorMatrixFilter
                }
            } else null

            val saveLayerId = if (layerPaint != null) {
                canvas.saveLayer(0f, 0f, compW, compH, layerPaint)
            } else -1

            layerMatrix2D.composeFromTransform(
                tx = drawX,
                ty = drawY,
                scaleX = wt.scaleX,
                scaleY = wt.scaleY,
                rotationDeg = wt.rotationDeg,
                skewXDeg = wt.skewXDeg,
                skewYDeg = wt.skewYDeg,
                flipX = wt.flipX,
                flipY = wt.flipY,
                anchorOffsetX = wt.anchorOffsetX,
                anchorOffsetY = wt.anchorOffsetY,
            )
            layerMatrix2D.toAndroidMatrix(layerAndroidMatrix, matrixScratch9)

            val primaryObj = layerState.layer.objects.firstOrNull()
            val halfW = ((primaryObj?.width ?: 200.0) * 0.5).toFloat()
            val halfH = ((primaryObj?.height ?: 200.0) * 0.5).toFloat()

            canvas.save()
            canvas.concat(layerAndroidMatrix)

            val visibleAfterClip = EffectCanvasDrawer.applyTransitionClips(canvas, vfx, halfW, halfH)
            if (visibleAfterClip) {
                val drawObjects = {
                    for (obj in layerState.layer.objects) {
                        drawSceneObject(canvas, context, project, evaluated, layerState, obj, depth, overlays.reducedQuality)
                    }
                }
                EffectCanvasDrawer.drawUnderlayVisualFx(
                    canvas = canvas,
                    vfx = vfx,
                    halfW = halfW,
                    halfH = halfH,
                    reducedQuality = overlays.reducedQuality,
                    drawGeometry = drawObjects,
                )
                drawObjects()
                EffectCanvasDrawer.drawOverlayEffects(
                    canvas = canvas,
                    vfx = vfx,
                    particles = layerState.particleFxList,
                    audioFx = layerState.audioFx,
                    halfW = halfW,
                    halfH = halfH,
                    reducedQuality = overlays.reducedQuality,
                    particleScale = overlays.particleScale,
                    maxParticles = overlays.maxParticles,
                    particlePool = layerParticlePool,
                )
            }

            canvas.restore()
            if (saveLayerId >= 0) {
                canvas.restoreToCount(saveLayerId)
            }
        }

        // 4. Spatial Motion Path (world space)
        if (overlays.showMotionPath && overlays.motionPath != null) {
            val layerState = evaluated.layers.find { it.layer.id == overlays.motionPath.layerId }
            val parallaxFactor = 1.0 + (layerState?.depth ?: 0.0) * cam.parallaxStrength * 0.45
            drawMotionPath(
                canvas = canvas,
                pathData = overlays.motionPath,
                camOffsetX = cam.x * parallaxFactor,
                camOffsetY = cam.y * parallaxFactor,
                currentFrame = evaluated.frame,
                selectedKeyframeFrame = overlays.selectedKeyframeFrame,
            )
        }

        // 5. Selection Gizmo
        if (overlays.selectedLayerId != null) {
            val sel = evaluated.layers.find { it.layer.id == overlays.selectedLayerId }
            if (sel != null) {
                val parallaxFactor = 1.0 + sel.depth * cam.parallaxStrength * 0.45
                drawSelectionGizmo(canvas, sel, cam.x * parallaxFactor, cam.y * parallaxFactor, cam.zoom.toFloat())
            }
        }

        canvas.restore() // end camera transform

        // 6. Atmospheric haze
        if (env.hazeIntensity > 0.01) {
            val hc = parseHexColor(env.hazeColor, Color.parseColor("#60A5FA"))
            val alpha = (env.hazeIntensity * 150).toInt().coerceIn(0, 220)
            val hazePaint = Paint().apply {
                shader = LinearGradient(
                    0f, compH * 0.42f, 0f, compH,
                    Color.TRANSPARENT,
                    Color.argb(alpha, Color.red(hc), Color.green(hc), Color.blue(hc)),
                    Shader.TileMode.CLAMP,
                )
            }
            canvas.drawRect(0f, compH * 0.42f, compW, compH, hazePaint)
        }

        // 7. Rolling fog
        if (env.fogOpacity > 0.01 && !overlays.reducedQuality) {
            drawFog(canvas, compW, compH, evaluated.frame, env)
        }

        // 8. Wind-blown grass
        if (env.grassDensity > 0.01 && !overlays.reducedQuality) {
            drawWindGrass(canvas, compW, compH, evaluated.frame, env)
        }

        // 9. Weather particles
        if (env.weatherType != WeatherType.NONE && env.weatherDensity > 0.01) {
            drawWeatherParticles(
                canvas = canvas,
                w = compW,
                h = compH,
                frame = evaluated.frame,
                fps = evaluated.fps,
                env = env,
                reducedQuality = overlays.reducedQuality,
                particleScale = overlays.particleScale,
                maxParticles = overlays.maxParticles,
            )
        }

        // 10. Time of day tint
        if (env.tintOpacity > 0.005) {
            val tc = parseHexColor(env.timeOfDayTint, Color.BLACK)
            val tintAlpha = (env.tintOpacity * 255).toInt().coerceIn(0, 220)
            canvas.drawColor(Color.argb(tintAlpha, Color.red(tc), Color.green(tc), Color.blue(tc)))
        }

        // 11. Safe Guides
        if (overlays.showGuides) {
            drawSafeGuides(canvas, compW, compH)
        }

        canvas.restore()
    }

    private fun drawSceneObject(
        canvas: Canvas,
        context: Context?,
        project: MotionProject,
        evaluated: EvaluatedCompositionState,
        layerState: EvaluatedLayerState,
        obj: SceneObjectDocument,
        depth: Int,
        reducedQuality: Boolean,
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val halfW = (obj.width / 2.0).toFloat()
        val halfH = (obj.height / 2.0).toFloat()

        when (obj) {
            is SceneObjectDocument.SpriteObject -> {
                val asset = project.assets.find { it.id == obj.assetId }
                val bmp = asset?.let { ProceduralAssetRenderer.getBitmap(context, it.dataUrl) }
                if (bmp != null) {
                    canvas.save()
                    if (obj.flipX || obj.flipY) {
                        canvas.scale(if (obj.flipX) -1f else 1f, if (obj.flipY) -1f else 1f)
                    }
                    val deform = layerState.meshDeform
                    if (deform != null) {
                        drawBitmapWithMeshDeform(canvas, bmp, null, RectF(-halfW, -halfH, halfW, halfH), deform, paint, reducedQuality)
                    } else {
                        canvas.drawBitmap(bmp, null, RectF(-halfW, -halfH, halfW, halfH), paint)
                    }
                    canvas.restore()
                } else {
                    paint.color = Color.argb(90, 59, 130, 246)
                    canvas.drawRoundRect(RectF(-halfW, -halfH, halfW, halfH), 12f, 12f, paint)
                }
            }
            is SceneObjectDocument.CharacterObject -> {
                val charDef = project.characters.find { it.id == obj.characterId }
                val asset = charDef?.let { cd -> project.assets.find { it.id == cd.assetId } }
                val bmp = asset?.let { ProceduralAssetRenderer.getBitmap(context, it.dataUrl) }
                if (bmp != null && charDef != null) {
                    val g = charDef.grid
                    val cols = max(1, g.cols)
                    val frameIdx = layerState.characterFrameIndex ?: 0
                    val col = frameIdx % cols
                    val row = frameIdx / cols
                    val sx = g.marginX + col * (g.cellWidth + g.spacingX)
                    val sy = g.marginY + row * (g.cellHeight + g.spacingY)
                    val srcRect = Rect(
                        sx.coerceIn(0, max(0, bmp.width - 1)),
                        sy.coerceIn(0, max(0, bmp.height - 1)),
                        (sx + g.cellWidth).coerceIn(1, bmp.width),
                        (sy + g.cellHeight).coerceIn(1, bmp.height),
                    )
                    canvas.save()
                    if (obj.flipX) canvas.scale(-1f, 1f)
                    val deform = layerState.meshDeform
                    if (deform != null) {
                        drawBitmapWithMeshDeform(canvas, bmp, srcRect, RectF(-halfW, -halfH, halfW, halfH), deform, paint, reducedQuality)
                    } else {
                        canvas.drawBitmap(bmp, srcRect, RectF(-halfW, -halfH, halfW, halfH), paint)
                    }
                    canvas.restore()
                }
            }
            is SceneObjectDocument.ShapeObject -> {
                val rect = RectF(-halfW, -halfH, halfW, halfH)
                paint.style = Paint.Style.FILL
                paint.color = parseHexColor(obj.fill, Color.parseColor("#3B82F6"))
                when (obj.shapeType) {
                    ShapeType.RECT -> canvas.drawRoundRect(rect, obj.cornerRadius.toFloat(), obj.cornerRadius.toFloat(), paint)
                    ShapeType.ELLIPSE -> canvas.drawOval(rect, paint)
                    ShapeType.POLYGON -> canvas.drawPath(buildPolygonPath(6, halfW, halfH), paint)
                    ShapeType.STAR -> canvas.drawPath(buildStarPath(5, halfW, halfH), paint)
                }
                if (obj.strokeWidth > 0.1) {
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = obj.strokeWidth.toFloat()
                    paint.color = parseHexColor(obj.stroke, Color.WHITE)
                    when (obj.shapeType) {
                        ShapeType.RECT -> canvas.drawRoundRect(rect, obj.cornerRadius.toFloat(), obj.cornerRadius.toFloat(), paint)
                        ShapeType.ELLIPSE -> canvas.drawOval(rect, paint)
                        ShapeType.POLYGON -> canvas.drawPath(buildPolygonPath(6, halfW, halfH), paint)
                        ShapeType.STAR -> canvas.drawPath(buildStarPath(5, halfW, halfH), paint)
                    }
                }
            }
            is SceneObjectDocument.TextObject -> {
                drawTextOrLyrics(canvas, obj, layerState)
            }
            is SceneObjectDocument.NestedCompositionObject -> {
                if (depth < 4) {
                    val childComp = project.compositions.find { it.id == obj.compositionId } ?: return
                    val cDur = max(1, childComp.settings.durationFrames)
                    val rawFrame = ((evaluated.frame + obj.timeOffsetFrames) * obj.playbackRate).toInt()
                    val childFrame = if (obj.loop) ((rawFrame % cDur) + cDur) % cDur else rawFrame.coerceIn(0, cDur - 1)
                    val childEval = evaluateCompositionAtFrame(project, childComp.id, childFrame) ?: return

                    val offW = childComp.settings.width.coerceIn(64, 1280)
                    val offH = childComp.settings.height.coerceIn(64, 720)
                    val offBmp = Bitmap.createBitmap(offW, offH, Bitmap.Config.ARGB_8888)
                    val offCanvas = Canvas(offBmp)
                    offCanvas.scale(offW / childComp.settings.width.toFloat(), offH / childComp.settings.height.toFloat())
                    renderComposition(offCanvas, context, project, childEval, RenderOverlayOptions(reducedQuality = reducedQuality), depth + 1)
                    canvas.drawBitmap(offBmp, null, RectF(-halfW, -halfH, halfW, halfH), paint)
                    offBmp.recycle()
                }
            }
        }
    }

    private fun smoothStep(edge0: Double, edge1: Double, x: Double): Double {
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0.0, 1.0)
        return t * t * (3.0 - 2.0 * t)
    }

    private fun drawBitmapWithMeshDeform(
        canvas: Canvas,
        bmp: Bitmap,
        srcBounds: Rect?,
        dstRect: RectF,
        deform: EvaluatedMeshDeform,
        paint: Paint,
        reducedQuality: Boolean = false,
    ) {
        val meshBmp: Bitmap
        val ownsTempBmp: Boolean
        if (srcBounds != null &&
            (srcBounds.left != 0 || srcBounds.top != 0 || srcBounds.right != bmp.width || srcBounds.bottom != bmp.height)
        ) {
            val cropW = srcBounds.width().coerceIn(1, bmp.width - srcBounds.left)
            val cropH = srcBounds.height().coerceIn(1, bmp.height - srcBounds.top)
            meshBmp = Bitmap.createBitmap(bmp, srcBounds.left, srcBounds.top, cropW, cropH)
            ownsTempBmp = true
        } else {
            meshBmp = bmp
            ownsTempBmp = false
        }

        val cols = when {
            reducedQuality -> 10
            deform.isStrandFlow -> 22
            else -> 16
        }
        val rows = when {
            reducedQuality -> 12
            deform.isStrandFlow -> 26
            else -> 16
        }

        val dstW = dstRect.width()
        val dstH = dstRect.height()
        val verts = FloatArray((cols + 1) * (rows + 1) * 2)
        val skewXRad = deform.skewXDeg * PI / 180.0
        val skewYRad = deform.skewYDeg * PI / 180.0

        var idx = 0
        for (row in 0..rows) {
            val v = row.toDouble() / rows.toDouble()
            for (col in 0..cols) {
                val u = col.toDouble() / cols.toDouble()
                val baseX = dstRect.left + (u * dstW).toFloat()
                val baseY = dstRect.top + (v * dstH).toFloat()

                var dx = 0.0
                var dy = 0.0

                if (deform.waveAxis == "vertical") {
                    val distFromPin = if (deform.pinEdge == "right") (1.0 - u) else u
                    val pinWeight = smoothStep(0.04, 1.0, distFromPin)
                    val flex = pinWeight * pinWeight
                    val crossPhase = sin(v * 5.2 + 0.6) * 0.45
                    val primary = sin(distFromPin * deform.waveFrequency * 2.0 * PI - deform.wavePhase + crossPhase)
                    val secondary = 0.32 * sin(distFromPin * deform.waveFrequency * 3.8 * PI - deform.wavePhase * 1.55 + crossPhase * 1.4)
                    dy = (primary + secondary) * deform.waveAmplitude * flex + sin(skewYRad) * (dstW * 0.25) * flex
                    dx = -abs(dy) * 0.14 * (if (deform.pinEdge == "right") -1.0 else 1.0)
                } else if (deform.isStrandFlow) {
                    val distFromPin = if (deform.pinEdge == "bottom") (1.0 - v) else v
                    // Keep scalp roots strictly locked while mid-lengths bend and tips whip freely
                    val rootLock = smoothStep(0.06, 0.98, distFromPin)
                    val flex = rootLock * rootLock * (0.65 + 0.35 * distFromPin)

                    // Smooth multi-octave virtual strand field across u so neighboring locks separate organically
                    val strandCoord = u * deform.strandCount.toDouble()
                    val strandPhase =
                        sin(strandCoord * 1.85 + 0.65) * 1.35 +
                        cos(strandCoord * 3.65 - 1.15) * 0.68 +
                        sin(strandCoord * 6.40 + 2.20) * 0.32
                    val edgeLightness = 0.72 + 0.56 * abs(u - 0.5) * 2.0
                    val strandAmpMod = (0.76 + 0.36 * sin(strandCoord * 2.55 + 1.1) + 0.14 * cos(strandCoord * 5.1)) * edgeLightness
                    val phaseLag = strandPhase * deform.strandVariance

                    // Multi-harmonic traveling S-wave down each strand + high-frequency tip micro-flutter
                    val primaryWave = sin(distFromPin * deform.waveFrequency * 2.0 * PI - deform.wavePhase + phaseLag)
                    val secondaryWave = 0.42 * sin(distFromPin * deform.waveFrequency * 3.65 * PI - deform.wavePhase * 1.65 + phaseLag * 1.5)
                    val tipFlutter = deform.turbulence * distFromPin * distFromPin *
                        sin(distFromPin * 11.5 - deform.wavePhase * 2.35 + strandCoord * 2.4)

                    // Steady wind drift modulated per strand + gentle tip fan-out so strands feather apart in wind
                    val windGustMod = 0.76 + 0.24 * sin(deform.wavePhase * 0.85 + phaseLag * 0.65)
                    val steadyWind = deform.windBias * windGustMod * (0.82 + 0.32 * edgeLightness)
                    val fanSpread = (u - 0.5) * (abs(deform.waveAmplitude) + abs(deform.windBias) * 0.4) *
                        0.26 * flex * (0.45 + 0.55 * sin(deform.wavePhase + phaseLag * 0.8))
                    val skewBend = sin(skewXRad) * (dstH * 0.22) * flex

                    dx = (steadyWind + (primaryWave + secondaryWave + tipFlutter) * deform.waveAmplitude * strandAmpMod) * flex +
                        fanSpread + skewBend

                    // Arc-length preservation + upward wind lift at tips so hair arcs naturally instead of stretching flat
                    val tipCurlY = cos(distFromPin * deform.waveFrequency * 2.0 * PI - deform.wavePhase + phaseLag) *
                        abs(deform.waveAmplitude) * 0.16 * flex
                    val arcLift = -abs(dx) * deform.tipLift * (0.45 + 0.55 * distFromPin)
                    dy = (if (deform.pinEdge == "bottom") -arcLift else arcLift) + tipCurlY
                } else {
                    val distFromPin = if (deform.pinEdge == "bottom") (1.0 - v) else v
                    val pinWeight = smoothStep(0.05, 1.0, distFromPin)
                    val flex = pinWeight * pinWeight
                    val crossPhase = sin(u * 4.5 + 0.4) * 0.5
                    val primary = sin(distFromPin * deform.waveFrequency * 2.0 * PI - deform.wavePhase + crossPhase)
                    val secondary = 0.30 * sin(distFromPin * deform.waveFrequency * 3.5 * PI - deform.wavePhase * 1.5 + crossPhase * 1.3)
                    dx = (primary + secondary) * deform.waveAmplitude * flex + sin(skewXRad) * (dstH * 0.22) * flex
                    dy = -abs(dx) * 0.14 * (if (deform.pinEdge == "bottom") -1.0 else 1.0)
                }

                verts[idx++] = baseX + dx.toFloat()
                verts[idx++] = baseY + dy.toFloat()
            }
        }

        canvas.drawBitmapMesh(meshBmp, cols, rows, verts, 0, null, 0, paint)
        if (ownsTempBmp) {
            meshBmp.recycle()
        }
    }

    private fun drawTextOrLyrics(
        canvas: Canvas,
        obj: SceneObjectDocument.TextObject,
        layerState: EvaluatedLayerState,
    ) {
        val lyrics = obj.lyrics
        val evalLyrics = layerState.lyricsState

        val baseColor = parseHexColor(obj.color, Color.WHITE)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = obj.fontSize.toFloat().coerceIn(10f, 240f)
            typeface = Typeface.create(Typeface.SANS_SERIF, if (obj.fontWeight >= 600) Typeface.BOLD else Typeface.NORMAL)
            color = baseColor
            textAlign = Paint.Align.CENTER
        }

        if (lyrics == null || !lyrics.enabled) {
            textPaint.setShadowLayer(8f, 0f, 3f, Color.argb(150, 0, 0, 0))
            val textFx = layerState.textFx
            if (textFx != null) {
                EffectCanvasDrawer.drawKineticText(canvas, obj.text, textPaint, textFx)
            } else {
                val fm = textPaint.fontMetrics
                val baseline = -(fm.ascent + fm.descent) / 2f
                canvas.drawText(obj.text, 0f, baseline, textPaint)
            }
            return
        }

        if (evalLyrics == null || evalLyrics.cue == null || evalLyrics.opacity <= 0.01) return

        canvas.save()
        canvas.translate(0f, evalLyrics.offsetY.toFloat())
        canvas.scale(evalLyrics.scale.toFloat(), evalLyrics.scale.toFloat())

        val alphaInt = (evalLyrics.opacity * 255).toInt().coerceIn(0, 255)
        val highlightColor = parseHexColor(lyrics.highlightColor, Color.parseColor("#F59E0B"))
        val glowColor = parseHexColor(lyrics.glowColor, Color.parseColor("#38BDF8"))
        val shadowColor = parseHexColor(lyrics.shadowColor, Color.BLACK)

        textPaint.textAlign = when (lyrics.align) {
            LyricsAlign.LEFT -> Paint.Align.LEFT
            LyricsAlign.CENTER -> Paint.Align.CENTER
            LyricsAlign.RIGHT -> Paint.Align.RIGHT
        }

        if (lyrics.style in listOf(LyricsStyle.KARAOKE, LyricsStyle.WORD_REVEAL, LyricsStyle.STAGGER) && evalLyrics.words.isNotEmpty()) {
            val spaceW = textPaint.measureText(" ")
            val widths = evalLyrics.words.map { textPaint.measureText(it.text) }
            val totalW = widths.sum() + spaceW * max(0, evalLyrics.words.size - 1)
            var cursorX = when (lyrics.align) {
                LyricsAlign.LEFT -> -lyrics.maxWidth.toFloat() / 2f
                LyricsAlign.CENTER -> -totalW / 2f
                LyricsAlign.RIGHT -> lyrics.maxWidth.toFloat() / 2f - totalW
            }
            val fm = textPaint.fontMetrics
            val baseY = -(fm.ascent + fm.descent) / 2f
            textPaint.textAlign = Paint.Align.LEFT

            evalLyrics.words.forEachIndexed { idx, w ->
                val ww = widths[idx]
                val wAlpha = (alphaInt * w.revealAlpha).toInt().coerceIn(0, 255)
                canvas.save()
                canvas.translate(cursorX + ww / 2f, baseY + w.offsetY.toFloat())
                canvas.scale(w.scale.toFloat(), w.scale.toFloat())

                // Base dim text
                textPaint.color = Color.argb(wAlpha, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor))
                textPaint.setShadowLayer(lyrics.shadowBlur.toFloat(), 0f, lyrics.shadowOffsetY.toFloat(), shadowColor)
                canvas.drawText(w.text, -ww / 2f, 0f, textPaint)

                // Progressive karaoke highlight overlay
                if (w.progress > 0.01) {
                    canvas.save()
                    val clipRight = -ww / 2f + ww * w.progress.toFloat()
                    canvas.clipRect(-ww / 2f - 6f, fm.ascent - 10f, clipRight, fm.descent + 10f)
                    textPaint.color = Color.argb(wAlpha, Color.red(highlightColor), Color.green(highlightColor), Color.blue(highlightColor))
                    val glowRad = (lyrics.glowStrength * (0.7 + evalLyrics.glowBoost)).toFloat().coerceAtLeast(1f)
                    textPaint.setShadowLayer(glowRad, 0f, 0f, glowColor)
                    canvas.drawText(w.text, -ww / 2f, 0f, textPaint)
                    canvas.restore()
                }
                canvas.restore()
                cursorX += ww + spaceW
            }
        } else {
            val fm = textPaint.fontMetrics
            val baseY = -(fm.ascent + fm.descent) / 2f
            val glowRad = (lyrics.glowStrength * (0.5 + evalLyrics.glowBoost)).toFloat().coerceAtLeast(1f)
            textPaint.color = Color.argb(alphaInt, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor))
            textPaint.setShadowLayer(glowRad, 0f, lyrics.shadowOffsetY.toFloat(), if (evalLyrics.glowBoost > 0.35) glowColor else shadowColor)
            val anchorX = when (lyrics.align) {
                LyricsAlign.LEFT -> -lyrics.maxWidth.toFloat() / 2f
                LyricsAlign.CENTER -> 0f
                LyricsAlign.RIGHT -> lyrics.maxWidth.toFloat() / 2f
            }
            canvas.drawText(evalLyrics.visibleText, anchorX, baseY, textPaint)
        }

        canvas.restore()
    }

    private fun drawClouds(canvas: Canvas, w: Float, h: Float, frame: Int, env: EnvironmentDocument) {
        val count = (env.cloudDensity * 9).toInt().coerceIn(1, 10)
        val dir = if (env.windDirection == WindDirection.LEFT) -1f else 1f
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb((env.cloudDensity * 75).toInt().coerceIn(12, 95), 226, 232, 240)
        }
        for (i in 0 until count) {
            val baseX = hash(i * 17 + 1).toFloat() * (w + 360f)
            val speed = (0.4f + hash(i * 23 + 3).toFloat() * 0.9f) * env.cloudSpeed.toFloat() * 1.4f
            val cx = (((baseX + frame * speed * dir) % (w + 360f)) + (w + 360f)) % (w + 360f) - 180f
            val cy = h * (0.10f + hash(i * 31 + 5).toFloat() * 0.28f)
            val rx = 70f + hash(i * 41 + 7).toFloat() * 95f
            val ry = rx * 0.34f
            canvas.drawOval(RectF(cx - rx, cy - ry, cx + rx, cy + ry), p)
            canvas.drawOval(RectF(cx - rx * 0.55f, cy - ry * 1.35f, cx + rx * 0.45f, cy + ry * 0.4f), p)
        }
    }

    private fun drawFog(canvas: Canvas, w: Float, h: Float, frame: Int, env: EnvironmentDocument) {
        val fc = parseHexColor(env.fogColor, Color.parseColor("#CBD5E1"))
        val alpha = (env.fogOpacity * 115).toInt().coerceIn(10, 140)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        for (band in 0..2) {
            val shift = ((frame * env.fogSpeed * (0.9 + band * 0.5)) % (w * 1.4)).toFloat()
            val y = h * (0.68f + band * 0.11f)
            p.shader = RadialGradient(
                shift, y, w * 0.45f,
                Color.argb(alpha, Color.red(fc), Color.green(fc), Color.blue(fc)),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP,
            )
            canvas.drawRect(0f, h * 0.45f, w, h, p)
        }
    }

    private fun drawWindGrass(canvas: Canvas, w: Float, h: Float, frame: Int, env: EnvironmentDocument) {
        val blades = (env.grassDensity * 72).toInt().coerceIn(10, 80)
        val gc = parseHexColor(env.grassColor, Color.parseColor("#14532D"))
        val dir = if (env.windDirection == WindDirection.LEFT) -1.0 else 1.0
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = gc
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        val path = Path()
        for (i in 0 until blades) {
            val bx = (i.toFloat() / blades) * w + (hash(i * 11).toFloat() - 0.5f) * 20f
            val bh = 28f + hash(i * 19 + 2).toFloat() * 54f
            val sway = sin(frame * 0.09 + i * 0.37) * 14.0 * env.windStrength * dir + dir * 8.0 * env.windStrength
            p.strokeWidth = 2.5f + hash(i * 29).toFloat() * 3f
            path.reset()
            path.moveTo(bx, h + 4f)
            path.quadTo(bx + sway.toFloat() * 0.45f, h - bh * 0.55f, bx + sway.toFloat(), h - bh)
            canvas.drawPath(path, p)
        }
    }

    private fun drawWeatherParticles(
        canvas: Canvas,
        w: Float,
        h: Float,
        frame: Int,
        fps: Double,
        env: EnvironmentDocument,
        reducedQuality: Boolean,
        particleScale: Float = 1.0f,
        maxParticles: Int = 160,
    ) {
        val baseCount = when (env.weatherType) {
            WeatherType.RAIN -> 130
            WeatherType.SNOW -> 90
            WeatherType.FIREFLIES -> 46
            WeatherType.DUST -> 65
            WeatherType.LEAVES -> 42
            WeatherType.NONE -> 0
        }
        val qualityFactor = (if (reducedQuality) 0.55 else 1.0) * particleScale.coerceIn(0.2f, 1.0f)
        val count = (baseCount * env.weatherDensity * qualityFactor).toInt().coerceIn(0, maxParticles.coerceAtLeast(0))
        val tSec = frame / max(1.0, fps)
        val rad = env.weatherDirectionDeg * PI / 180.0
        val dxFactor = sin(rad)
        val dyFactor = cos(rad)
        val sz = env.weatherSize.toFloat().coerceIn(0.4f, 3f)
        val op = env.weatherOpacity.coerceIn(0.1, 1.0)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val leafRect = RectF(-6f * sz, -3f * sz, 6f * sz, 3f * sz)

        for (i in 0 until count) {
            val h1 = hash(i * 37 + 11)
            val h2 = hash(i * 53 + 23)
            val h3 = hash(i * 71 + 41)
            val speed = (0.55 + h3 * 0.9) * env.weatherSpeed

            when (env.weatherType) {
                WeatherType.RAIN -> {
                    val px = (((h1 * w + tSec * speed * 420.0 * dxFactor) % w) + w) % w
                    val py = (((h2 * h + tSec * speed * 620.0 * dyFactor) % h) + h) % h
                    p.color = Color.argb((op * 170).toInt(), 186, 230, 253)
                    p.strokeWidth = 1.6f * sz
                    canvas.drawLine(px.toFloat(), py.toFloat(), (px + dxFactor * 18 * sz).toFloat(), (py + dyFactor * 22 * sz).toFloat(), p)
                }
                WeatherType.SNOW -> {
                    val drift = sin(tSec * 1.4 + i) * 26.0
                    val px = (((h1 * w + tSec * speed * 60.0 * dxFactor + drift) % w) + w) % w
                    val py = (((h2 * h + tSec * speed * 95.0) % h) + h) % h
                    p.color = Color.argb((op * (140 + h3 * 110)).toInt().coerceIn(0, 255), 241, 245, 249)
                    canvas.drawCircle(px.toFloat(), py.toFloat(), (2.2f + h3.toFloat() * 3.2f) * sz, p)
                }
                WeatherType.FIREFLIES -> {
                    val px = h1 * w + sin(tSec * speed * 0.9 + i * 1.7) * 55.0
                    val py = h2 * h + cos(tSec * speed * 1.1 + i * 2.1) * 42.0
                    val pulse = (0.35 + 0.65 * (0.5 + 0.5 * sin(tSec * 2.6 + i * 2.3))) * op
                    val radius = (5f + h3.toFloat() * 6f) * sz
                    if (reducedQuality) {
                        // Low-end lightweight two-circle glow approximation (zero shader allocation)
                        p.color = Color.argb((pulse * 80).toInt(), 253, 224, 71)
                        canvas.drawCircle(px.toFloat(), py.toFloat(), radius * 2.0f, p)
                    } else {
                        p.shader = RadialGradient(
                            px.toFloat(), py.toFloat(), radius * 2.2f,
                            Color.argb((pulse * 220).toInt(), 253, 224, 71),
                            Color.TRANSPARENT,
                            Shader.TileMode.CLAMP,
                        )
                        canvas.drawCircle(px.toFloat(), py.toFloat(), radius * 2.2f, p)
                        p.shader = null
                    }
                    p.color = Color.argb((pulse * 255).toInt(), 254, 249, 195)
                    canvas.drawCircle(px.toFloat(), py.toFloat(), radius * 0.45f, p)
                }
                WeatherType.DUST -> {
                    val px = (((h1 * w + tSec * speed * 22.0) % w) + w) % w
                    val py = h2 * h + sin(tSec * 0.7 + i) * 18.0
                    p.color = Color.argb((op * 125).toInt(), 253, 230, 138)
                    canvas.drawCircle(px.toFloat(), py.toFloat(), (1.5f + h3.toFloat() * 2.2f) * sz, p)
                }
                WeatherType.LEAVES -> {
                    val sway = sin(tSec * 2.0 + i * 1.5) * 32.0
                    val px = (((h1 * w + tSec * speed * 110.0 * max(0.25, dxFactor) + sway) % w) + w) % w
                    val py = (((h2 * h + tSec * speed * 90.0) % h) + h) % h
                    val rot = (tSec * 90.0 * (0.5 + h3) + i * 35.0).toFloat()
                    p.color = if (i % 2 == 0) Color.argb((op * 230).toInt(), 249, 168, 212) else Color.argb((op * 210).toInt(), 251, 113, 133)
                    canvas.save()
                    canvas.translate(px.toFloat(), py.toFloat())
                    canvas.rotate(rot)
                    canvas.drawOval(leafRect, p)
                    canvas.restore()
                }
                WeatherType.NONE -> Unit
            }
        }
    }

    private fun drawMotionPath(
        canvas: Canvas,
        pathData: LayerMotionPath,
        camOffsetX: Double,
        camOffsetY: Double,
        currentFrame: Int,
        selectedKeyframeFrame: Int? = null,
    ) {
        if (pathData.samples.size < 2 && pathData.keyframeNodes.isEmpty()) return
        if (pathData.samples.size >= 2) {
            val curvePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#38BDF8")
                style = Paint.Style.STROKE
                strokeWidth = 2.5f
                pathEffect = DashPathEffect(floatArrayOf(10f, 6f), 0f)
            }
            val path = Path()
            pathData.samples.forEachIndexed { idx, s ->
                val sx = (s.x - camOffsetX).toFloat()
                val sy = (s.y - camOffsetY).toFloat()
                if (idx == 0) path.moveTo(sx, sy) else path.lineTo(sx, sy)
            }
            canvas.drawPath(path, curvePaint)

            // Velocity dots
            val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(180, 56, 189, 248) }
            for (i in pathData.samples.indices step 3) {
                val s = pathData.samples[i]
                canvas.drawCircle((s.x - camOffsetX).toFloat(), (s.y - camOffsetY).toFloat(), 3f, dotPaint)
            }
        }

        // Keyframe diamonds (with touch-clear selected halo + frame badge)
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(72, 75, 155, 232)
            style = Paint.Style.FILL
        }
        val haloRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#4B9BE8")
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
        }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.2f
        }
        val pillBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(230, 19, 19, 19)
            style = Paint.Style.FILL
        }
        val pillTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#9ECAFF")
            textSize = 18f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }

        for (node in pathData.keyframeNodes) {
            val nx = (node.x - camOffsetX).toFloat()
            val ny = (node.y - camOffsetY).toFloat()
            val isSelectedNode = selectedKeyframeFrame != null && node.frame == selectedKeyframeFrame
            val isCurrentNode = node.frame == currentFrame
            val r = when {
                isSelectedNode -> 11.5f
                isCurrentNode -> 10f
                else -> 8f
            }

            if (isSelectedNode) {
                canvas.drawCircle(nx, ny, 22f, haloPaint)
                canvas.drawCircle(nx, ny, 22f, haloRingPaint)
            }

            fillPaint.color = when {
                isSelectedNode -> Color.parseColor("#4B9BE8")
                isCurrentNode -> Color.WHITE
                else -> Color.parseColor("#F59E0B")
            }
            strokePaint.color = if (isSelectedNode) Color.WHITE else Color.parseColor("#111317")

            canvas.save()
            canvas.translate(nx, ny)
            canvas.rotate(45f)
            canvas.drawRect(-r, -r, r, r, fillPaint)
            canvas.drawRect(-r, -r, r, r, strokePaint)
            canvas.restore()

            if (isSelectedNode) {
                val label = "F${node.frame}"
                val textW = pillTextPaint.measureText(label)
                val pillW = textW + 18f
                val pillH = 26f
                val left = nx - pillW / 2f
                val top = ny - 46f
                canvas.drawRoundRect(left, top, left + pillW, top + pillH, 6f, 6f, pillBgPaint)
                canvas.drawRoundRect(left, top, left + pillW, top + pillH, 6f, 6f, haloRingPaint)
                canvas.drawText(label, nx, top + 19f, pillTextPaint)
            }
        }
    }

    private fun drawSelectionGizmo(
        canvas: Canvas,
        layerState: EvaluatedLayerState,
        camOffsetX: Double,
        camOffsetY: Double,
        camZoom: Float,
    ) {
        val obj = layerState.layer.objects.firstOrNull()
        val w = ((obj?.width ?: 180.0) * abs(layerState.worldTransform.scaleX)).toFloat()
        val h = ((obj?.height ?: 180.0) * abs(layerState.worldTransform.scaleY)).toFloat()
        val cx = (layerState.worldTransform.x - camOffsetX).toFloat()
        val cy = (layerState.worldTransform.y - camOffsetY).toFloat()
        val invZ = (1f / max(0.25f, camZoom))

        canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(layerState.worldTransform.rotationDeg.toFloat())

        val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#3B82F6")
            style = Paint.Style.STROKE
            strokeWidth = 2.2f * invZ
        }
        canvas.drawRect(-w / 2f, -h / 2f, w / 2f, h / 2f, boxPaint)

        // Rotation stem + handle
        val stemLen = 36f * invZ
        canvas.drawLine(0f, -h / 2f, 0f, -h / 2f - stemLen, boxPaint)

        val handleFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        val handleR = 8f * invZ
        canvas.drawCircle(0f, -h / 2f - stemLen, handleR, handleFill)
        canvas.drawCircle(0f, -h / 2f - stemLen, handleR, boxPaint)

        // 4 corner scale handles
        val corners = listOf(
            -w / 2f to -h / 2f,
            w / 2f to -h / 2f,
            -w / 2f to h / 2f,
            w / 2f to h / 2f,
        )
        for ((hx, hy) in corners) {
            canvas.drawRect(hx - handleR, hy - handleR, hx + handleR, hy + handleR, handleFill)
            canvas.drawRect(hx - handleR, hy - handleR, hx + handleR, hy + handleR, boxPaint)
        }
        canvas.restore()
    }

    private fun drawSafeGuides(canvas: Canvas, w: Float, h: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
            color = Color.argb(110, 56, 189, 248)
            pathEffect = DashPathEffect(floatArrayOf(8f, 6f), 0f)
        }
        canvas.drawRect(w * 0.05f, h * 0.05f, w * 0.95f, h * 0.95f, p)
        p.color = Color.argb(95, 245, 158, 11)
        canvas.drawRect(w * 0.1f, h * 0.1f, w * 0.9f, h * 0.9f, p)
        canvas.drawLine(w / 2f, 0f, w / 2f, h, p)
        canvas.drawLine(0f, h / 2f, w, h / 2f, p)
    }

    fun hitTestLayer(
        evaluated: EvaluatedCompositionState,
        compX: Double,
        compY: Double,
    ): String? {
        val cam = evaluated.camera
        val compW = evaluated.composition.settings.width
        val compH = evaluated.composition.settings.height

        // Invert camera transform
        val dx0 = (compX - compW / 2.0) / max(0.1, cam.zoom)
        val dy0 = (compY - compH / 2.0) / max(0.1, cam.zoom)
        val cRad = cam.rotationDeg * PI / 180.0
        val worldX = dx0 * cos(cRad) - dy0 * sin(cRad) + compW / 2.0
        val worldY = dx0 * sin(cRad) + dy0 * cos(cRad) + compH / 2.0

        for (ls in evaluated.layers.asReversed()) {
            if (!ls.isActiveAtFrame || ls.layer.locked) continue
            val parallax = 1.0 + ls.depth * cam.parallaxStrength * 0.45
            val lx = ls.worldTransform.x - cam.x * parallax
            val ly = ls.worldTransform.y - cam.y * parallax
            val obj = ls.layer.objects.firstOrNull()
            val halfW = ((obj?.width ?: 180.0) * abs(ls.worldTransform.scaleX)) / 2.0 + 14.0
            val halfH = ((obj?.height ?: 180.0) * abs(ls.worldTransform.scaleY)) / 2.0 + 14.0

            val relX = worldX - lx
            val relY = worldY - ly
            val lRad = -ls.worldTransform.rotationDeg * PI / 180.0
            val localX = relX * cos(lRad) - relY * sin(lRad)
            val localY = relX * sin(lRad) + relY * cos(lRad)

            if (abs(localX) <= halfW && abs(localY) <= halfH) {
                return ls.layer.id
            }
        }
        return null
    }

    fun hitTestGizmoHandle(
        evaluated: EvaluatedCompositionState,
        selectedLayerId: String,
        compX: Double,
        compY: Double,
    ): GizmoHandle {
        val ls = evaluated.layers.find { it.layer.id == selectedLayerId } ?: return GizmoHandle.NONE
        val cam = evaluated.camera
        val compW = evaluated.composition.settings.width
        val compH = evaluated.composition.settings.height

        val dx0 = (compX - compW / 2.0) / max(0.1, cam.zoom)
        val dy0 = (compY - compH / 2.0) / max(0.1, cam.zoom)
        val cRad = cam.rotationDeg * PI / 180.0
        val worldX = dx0 * cos(cRad) - dy0 * sin(cRad) + compW / 2.0
        val worldY = dx0 * sin(cRad) + dy0 * cos(cRad) + compH / 2.0

        val parallax = 1.0 + ls.depth * cam.parallaxStrength * 0.45
        val cx = ls.worldTransform.x - cam.x * parallax
        val cy = ls.worldTransform.y - cam.y * parallax
        val relX = worldX - cx
        val relY = worldY - cy
        val lRad = -ls.worldTransform.rotationDeg * PI / 180.0
        val localX = relX * cos(lRad) - relY * sin(lRad)
        val localY = relX * sin(lRad) + relY * cos(lRad)

        val obj = ls.layer.objects.firstOrNull()
        val halfW = ((obj?.width ?: 180.0) * abs(ls.worldTransform.scaleX)) / 2.0
        val halfH = ((obj?.height ?: 180.0) * abs(ls.worldTransform.scaleY)) / 2.0
        val invZ = 1.0 / max(0.25, cam.zoom)
        val hitRad = 26.0 * invZ

        if (hypot(localX, localY - (-halfH - 36.0 * invZ)) <= hitRad) return GizmoHandle.ROTATE
        if (hypot(localX + halfW, localY + halfH) <= hitRad) return GizmoHandle.TOP_LEFT
        if (hypot(localX - halfW, localY + halfH) <= hitRad) return GizmoHandle.TOP_RIGHT
        if (hypot(localX + halfW, localY - halfH) <= hitRad) return GizmoHandle.BOTTOM_LEFT
        if (hypot(localX - halfW, localY - halfH) <= hitRad) return GizmoHandle.BOTTOM_RIGHT
        return GizmoHandle.NONE
    }

    private fun buildPolygonPath(sides: Int, rx: Float, ry: Float): Path = Path().apply {
        for (i in 0 until sides) {
            val angle = -PI / 2.0 + (2.0 * PI * i) / sides
            val px = (cos(angle) * rx).toFloat()
            val py = (sin(angle) * ry).toFloat()
            if (i == 0) moveTo(px, py) else lineTo(px, py)
        }
        close()
    }

    private fun buildStarPath(points: Int, rx: Float, ry: Float): Path = Path().apply {
        val total = points * 2
        for (i in 0 until total) {
            val rScale = if (i % 2 == 0) 1.0 else 0.44
            val angle = -PI / 2.0 + (2.0 * PI * i) / total
            val px = (cos(angle) * rx * rScale).toFloat()
            val py = (sin(angle) * ry * rScale).toFloat()
            if (i == 0) moveTo(px, py) else lineTo(px, py)
        }
        close()
    }

    private fun toPorterDuff(mode: BlendModeType): PorterDuff.Mode? = when (mode) {
        BlendModeType.NORMAL -> null
        BlendModeType.MULTIPLY -> PorterDuff.Mode.MULTIPLY
        BlendModeType.SCREEN -> PorterDuff.Mode.SCREEN
        BlendModeType.OVERLAY -> PorterDuff.Mode.OVERLAY
        BlendModeType.ADD -> PorterDuff.Mode.ADD
        BlendModeType.DARKEN -> PorterDuff.Mode.DARKEN
        BlendModeType.LIGHTEN -> PorterDuff.Mode.LIGHTEN
    }
}
