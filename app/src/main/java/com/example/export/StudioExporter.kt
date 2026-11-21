package com.example.export

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.engine.evaluateCompositionAtFrame
import com.example.engine.evaluateProjectTimelineAtFrame
import com.example.engine.getProjectMovieDurationFrames
import com.example.renderer.AndroidCanvasRenderer
import com.example.renderer.RenderOverlayOptions
import com.example.schema.MotionProject
import com.example.schema.fpsToDouble
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.math.roundToInt

enum class ExportScope {
    ACTIVE_COMP,
    FULL_MOVIE,
}

enum class ExportFormat(val extension: String, val mimeType: String) {
    GIF("gif", "image/gif"),
    MP4("mp4", "video/mp4"),
}

data class ExportProgress(
    val currentFrame: Int,
    val totalFrames: Int,
    val stage: String,
)

data class ExportedArtifact(
    val file: File,
    val mimeType: String,
    val format: ExportFormat,
    val displayName: String,
    val sizeBytes: Long,
)

object StudioExporter {

    fun createShareExportFile(context: Context, slug: String, format: ExportFormat): File {
        val dir = File(context.cacheDir, "shared_exports").apply { mkdirs() }
        // Prune older temp exports beyond the 6 most recent to avoid unbounded cache growth
        dir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(5)?.forEach { it.delete() }
        val safeSlug = slug.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').ifEmpty { "motion_studio" }
        return File(dir, "${safeSlug}_${System.currentTimeMillis()}.${format.extension}")
    }

    fun launchShareIntent(context: Context, artifact: ExportedArtifact): Result<Unit> = runCatching {
        val authority = "${context.packageName}.fileprovider"
        val contentUri = FileProvider.getUriForFile(context, authority, artifact.file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = artifact.mimeType
            putExtra(Intent.EXTRA_STREAM, contentUri)
            putExtra(Intent.EXTRA_SUBJECT, artifact.displayName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(shareIntent, "Share ${artifact.displayName}").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    suspend fun exportPngSnapshot(
        context: Context,
        project: MotionProject,
        activeCompId: String,
        frame: Int,
        scope: ExportScope,
        destUri: Uri,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val comp = project.compositions.find { it.id == activeCompId } ?: project.compositions.first()
            val w = comp.settings.width.coerceIn(160, 1920)
            val h = comp.settings.height.coerceIn(160, 1920)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)

            if (scope == ExportScope.FULL_MOVIE) {
                val ms = evaluateProjectTimelineAtFrame(project, frame)
                AndroidCanvasRenderer.renderMovieFrame(canvas, context, project, ms, w, h)
            } else {
                val eval = evaluateCompositionAtFrame(project, comp.id, frame)
                if (eval != null) {
                    AndroidCanvasRenderer.renderComposition(canvas, context, project, eval, RenderOverlayOptions())
                }
            }

            context.contentResolver.openOutputStream(destUri)?.use { out ->
                bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            } ?: error("Cannot open output stream for PNG export")
            bmp.recycle()
        }
    }

    suspend fun exportPngSnapshotToFile(
        context: Context,
        project: MotionProject,
        activeCompId: String,
        frame: Int,
        scope: ExportScope,
        outputFile: File,
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val comp = project.compositions.find { it.id == activeCompId } ?: project.compositions.first()
            val w = comp.settings.width.coerceIn(160, 1920)
            val h = comp.settings.height.coerceIn(160, 1920)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)

            if (scope == ExportScope.FULL_MOVIE) {
                val ms = evaluateProjectTimelineAtFrame(project, frame)
                AndroidCanvasRenderer.renderMovieFrame(canvas, context, project, ms, w, h)
            } else {
                val eval = evaluateCompositionAtFrame(project, comp.id, frame)
                if (eval != null) {
                    AndroidCanvasRenderer.renderComposition(canvas, context, project, eval, RenderOverlayOptions())
                }
            }

            FileOutputStream(outputFile).use { out ->
                bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            bmp.recycle()
            outputFile
        }
    }

    suspend fun exportGifToFile(
        context: Context,
        project: MotionProject,
        activeCompId: String,
        scope: ExportScope,
        outputFile: File,
        gifFps: Int = 15,
        maxDimension: Int = 480,
        onProgress: (ExportProgress) -> Unit,
    ): Result<ExportedArtifact> = withContext(Dispatchers.IO) {
        runCatching {
            val comp = project.compositions.find { it.id == activeCompId } ?: project.compositions.first()
            val sourceFps = max(1.0, fpsToDouble(comp.settings.fpsOverride ?: project.fps))
            val totalSourceFrames = if (scope == ExportScope.FULL_MOVIE) {
                getProjectMovieDurationFrames(project)
            } else {
                comp.settings.durationFrames
            }.coerceAtLeast(1)

            val targetFps = gifFps.coerceIn(6, 24)
            val durationSec = totalSourceFrames.toDouble() / sourceFps
            val outFrameCount = max(1, (durationSec * targetFps).roundToInt()).coerceAtMost(300)
            val delayMs = (1000.0 / targetFps).roundToInt().coerceAtLeast(20)

            val compW = comp.settings.width.coerceAtLeast(1)
            val compH = comp.settings.height.coerceAtLeast(1)
            val scaleFactor = minOf(
                1.0f,
                maxDimension.toFloat() / max(compW, compH).toFloat(),
            )
            val outW = max(64, ((compW * scaleFactor).roundToInt() / 2) * 2)
            val outH = max(64, ((compH * scaleFactor).roundToInt() / 2) * 2)

            val bmp = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            val scaleX = outW.toFloat() / compW.toFloat()
            val scaleY = outH.toFloat() / compH.toFloat()

            try {
                FileOutputStream(outputFile).use { fos ->
                    val encoder = AnimatedGifEncoder(fos, outW, outH, loopCount = 0)
                    for (idx in 0 until outFrameCount) {
                        coroutineContext.ensureActive()
                        val srcFrame = if (outFrameCount <= 1) {
                            0
                        } else {
                            ((idx.toDouble() / (outFrameCount - 1).toDouble()) * (totalSourceFrames - 1))
                                .roundToInt()
                                .coerceIn(0, totalSourceFrames - 1)
                        }
                        onProgress(
                            ExportProgress(
                                currentFrame = idx + 1,
                                totalFrames = outFrameCount,
                                stage = "Encoding GIF frame ${idx + 1} / $outFrameCount",
                            )
                        )
                        bmp.eraseColor(android.graphics.Color.BLACK)
                        canvas.save()
                        canvas.scale(scaleX, scaleY)
                        if (scope == ExportScope.FULL_MOVIE) {
                            val ms = evaluateProjectTimelineAtFrame(project, srcFrame)
                            AndroidCanvasRenderer.renderMovieFrame(canvas, context, project, ms, compW, compH)
                        } else {
                            val ev = evaluateCompositionAtFrame(project, comp.id, srcFrame)
                            if (ev != null) {
                                AndroidCanvasRenderer.renderComposition(canvas, context, project, ev, RenderOverlayOptions())
                            }
                        }
                        canvas.restore()
                        encoder.addFrame(bmp, delayMs)
                    }
                    onProgress(ExportProgress(outFrameCount, outFrameCount, "Finalizing animated GIF…"))
                    encoder.finish()
                }
            } finally {
                bmp.recycle()
            }

            ExportedArtifact(
                file = outputFile,
                mimeType = ExportFormat.GIF.mimeType,
                format = ExportFormat.GIF,
                displayName = outputFile.name,
                sizeBytes = outputFile.length(),
            )
        }
    }

    suspend fun exportGifToUri(
        context: Context,
        project: MotionProject,
        activeCompId: String,
        scope: ExportScope,
        destUri: Uri,
        gifFps: Int = 15,
        maxDimension: Int = 480,
        onProgress: (ExportProgress) -> Unit,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val tmpFile = File(context.cacheDir, "export_${System.currentTimeMillis()}.gif")
        val res = exportGifToFile(context, project, activeCompId, scope, tmpFile, gifFps, maxDimension, onProgress)
        res.mapCatching {
            context.contentResolver.openOutputStream(destUri)?.use { out ->
                tmpFile.inputStream().use { inp -> inp.copyTo(out) }
            } ?: error("Cannot write exported GIF to destination URI")
            tmpFile.delete()
            Unit
        }.also {
            if (it.isFailure) tmpFile.delete()
        }
    }

    suspend fun exportMp4ToFile(
        context: Context,
        project: MotionProject,
        activeCompId: String,
        scope: ExportScope,
        outputFile: File,
        onProgress: (ExportProgress) -> Unit,
    ): Result<ExportedArtifact> = withContext(Dispatchers.IO) {
        runCatching {
            val comp = project.compositions.find { it.id == activeCompId } ?: project.compositions.first()
            val fps = max(1, fpsToDouble(comp.settings.fpsOverride ?: project.fps).roundToInt())
            val totalFrames = if (scope == ExportScope.FULL_MOVIE) {
                getProjectMovieDurationFrames(project)
            } else {
                comp.settings.durationFrames
            }.coerceAtLeast(1)

            // Hardware H.264 requires dimensions divisible by 16
            val rawW = project.exportSettings.width.coerceIn(320, 1280)
            val rawH = project.exportSettings.height.coerceIn(240, 1280)
            val encW = (rawW / 16) * 16
            val encH = (rawH / 16) * 16

            val bitrate = when (project.exportSettings.quality.lowercase()) {
                "ultra" -> 10_000_000
                "high" -> 6_000_000
                else -> 3_000_000
            }
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, encW, encH).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
                setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }

            val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            var muxer: MediaMuxer? = null
            var muxerStarted = false
            var trackIndex = -1
            var encodedFrameIndex = 0L
            val bufferInfo = MediaCodec.BufferInfo()
            val sampleInfo = MediaCodec.BufferInfo()

            try {
                encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                val inputSurface = encoder.createInputSurface()
                encoder.start()
                muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

                fun drainEncoder(endOfStream: Boolean) {
                    if (endOfStream) encoder.signalEndOfInputStream()
                    var emptyRetries = 0
                    while (true) {
                        val outIdx = encoder.dequeueOutputBuffer(bufferInfo, 10_000)
                        when {
                            outIdx == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                                if (!endOfStream || ++emptyRetries > 200) break
                            }
                            outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                                trackIndex = muxer!!.addTrack(encoder.outputFormat)
                                muxer!!.start()
                                muxerStarted = true
                            }
                            outIdx >= 0 -> {
                                emptyRetries = 0
                                val outBuf = encoder.getOutputBuffer(outIdx)
                                if (outBuf != null && (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 && bufferInfo.size > 0 && muxerStarted) {
                                    outBuf.position(bufferInfo.offset)
                                    outBuf.limit(bufferInfo.offset + bufferInfo.size)
                                    val ptsUs = (encodedFrameIndex * 1_000_000L) / fps
                                    encodedFrameIndex++
                                    sampleInfo.set(bufferInfo.offset, bufferInfo.size, ptsUs, bufferInfo.flags)
                                    muxer!!.writeSampleData(trackIndex, outBuf, sampleInfo)
                                }
                                encoder.releaseOutputBuffer(outIdx, false)
                                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break
                            }
                        }
                    }
                }

                for (f in 0 until totalFrames) {
                    coroutineContext.ensureActive()
                    onProgress(ExportProgress(f + 1, totalFrames, "Rendering frame ${f + 1} / $totalFrames"))
                    val surfaceCanvas = inputSurface.lockCanvas(null)
                    try {
                        val scaleX = encW.toFloat() / comp.settings.width.toFloat()
                        val scaleY = encH.toFloat() / comp.settings.height.toFloat()
                        surfaceCanvas.save()
                        surfaceCanvas.scale(scaleX, scaleY)
                        if (scope == ExportScope.FULL_MOVIE) {
                            val ms = evaluateProjectTimelineAtFrame(project, f)
                            AndroidCanvasRenderer.renderMovieFrame(surfaceCanvas, context, project, ms, comp.settings.width, comp.settings.height)
                        } else {
                            val ev = evaluateCompositionAtFrame(project, comp.id, f)
                            if (ev != null) {
                                AndroidCanvasRenderer.renderComposition(surfaceCanvas, context, project, ev, RenderOverlayOptions())
                            }
                        }
                        surfaceCanvas.restore()
                    } finally {
                        inputSurface.unlockCanvasAndPost(surfaceCanvas)
                    }
                    drainEncoder(false)
                }

                onProgress(ExportProgress(totalFrames, totalFrames, "Finalizing MP4 container…"))
                drainEncoder(true)
            } finally {
                runCatching { encoder.stop() }
                runCatching { encoder.release() }
                if (muxerStarted) runCatching { muxer?.stop() }
                runCatching { muxer?.release() }
            }

            ExportedArtifact(
                file = outputFile,
                mimeType = ExportFormat.MP4.mimeType,
                format = ExportFormat.MP4,
                displayName = outputFile.name,
                sizeBytes = outputFile.length(),
            )
        }
    }

    suspend fun exportMp4Video(
        context: Context,
        project: MotionProject,
        activeCompId: String,
        scope: ExportScope,
        destUri: Uri,
        onProgress: (ExportProgress) -> Unit,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val tmpFile = File(context.cacheDir, "export_${System.currentTimeMillis()}.mp4")
        val res = exportMp4ToFile(context, project, activeCompId, scope, tmpFile, onProgress)
        res.mapCatching {
            context.contentResolver.openOutputStream(destUri)?.use { out ->
                tmpFile.inputStream().use { inp -> inp.copyTo(out) }
            } ?: error("Cannot write exported MP4 to destination URI")
            tmpFile.delete()
            Unit
        }.also {
            if (it.isFailure) tmpFile.delete()
        }
    }
}
