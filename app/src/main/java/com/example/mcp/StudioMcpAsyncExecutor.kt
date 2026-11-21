package com.example.mcp

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.example.assets.BackgroundRemovalMode
import com.example.assets.BackgroundRemovalOptions
import com.example.assets.ProceduralAssetRenderer
import com.example.assets.SpriteBackgroundRemover
import com.example.commands.ProjectCommand
import com.example.commands.ProjectCommands
import com.example.commands.nowIso
import com.example.commands.uid
import com.example.export.ExportFormat
import com.example.export.ExportProgress
import com.example.export.ExportScope
import com.example.export.StudioExporter
import com.example.schema.AssetCategory
import com.example.schema.AssetKind
import com.example.schema.AssetRecord
import com.example.schema.parseHexColor
import com.example.storage.ProjectStorage
import com.example.viewmodel.StudioUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.roundToInt

/**
 * Executes both synchronous [LocalMcpEngine] tools and asynchronous device tools
 * (base64 sprite import, offline background removal, undo/redo, MP4/GIF/PNG rendering)
 * for both the embedded HTTP MCP server and the in-app MCP tool tester.
 */
class StudioMcpAsyncExecutor(
    private val app: Application,
    private val storage: ProjectStorage,
    private val getState: () -> StudioUiState,
    private val dispatchCommandOnMain: suspend (ProjectCommand) -> Boolean,
    private val setActiveCompositionOnMain: suspend (String) -> Unit,
    private val undoOnMain: suspend () -> Boolean,
    private val redoOnMain: suspend () -> Boolean,
    private val onExportStateChanged: (isExporting: Boolean, progress: ExportProgress?) -> Unit,
    private val onRegisterExport: (McpExportedFileEntry) -> Unit,
) {
    suspend fun execute(
        toolName: String,
        args: JsonObject,
        baseUrl: String,
    ): Pair<String, Boolean> {
        return when (toolName) {
            "undo_redo" -> handleUndoRedo(args)
            "import_sprite_base64" -> handleImportSpriteBase64(args)
            "remove_sprite_background" -> handleRemoveSpriteBackground(args)
            "render_video" -> handleRenderVideo(args, baseUrl)
            "render_frame_png" -> handleRenderFramePng(args, baseUrl)
            else -> {
                val state = getState()
                val outcome = LocalMcpEngine.executeTool(
                    project = state.project,
                    activeCompId = state.activeCompositionId,
                    toolName = toolName,
                    args = args,
                )
                if (!outcome.isError) {
                    if (outcome.command != null) {
                        val applied = dispatchCommandOnMain(outcome.command)
                        if (!applied) {
                            return """{"error":"Command failed to apply to studio state"}""" to true
                        }
                    }
                    outcome.switchCompId?.let { setActiveCompositionOnMain(it) }
                }
                outcome.outputJson to outcome.isError
            }
        }
    }

    private suspend fun handleUndoRedo(args: JsonObject): Pair<String, Boolean> {
        val action = args["action"]?.jsonPrimitive?.contentOrNull?.trim()?.lowercase()
        val performed = when (action) {
            "undo" -> undoOnMain()
            "redo" -> redoOnMain()
            else -> return """{"error":"action must be 'undo' or 'redo'."}""" to true
        }
        val state = getState()
        val json = buildJsonObject {
            put("performed", performed)
            put("action", action)
            put("canUndo", state.canUndo)
            put("canRedo", state.canRedo)
        }.toString()
        return json to false
    }

    private suspend fun handleImportSpriteBase64(args: JsonObject): Pair<String, Boolean> {
        val name = args["name"]?.jsonPrimitive?.contentOrNull?.trim()
        val rawData = args["base64Data"]?.jsonPrimitive?.contentOrNull?.trim()
        if (name.isNullOrEmpty() || rawData.isNullOrEmpty()) {
            return """{"error":"import_sprite_base64 requires user-provided 'name' and non-empty 'base64Data'. Do NOT synthesize placeholder images—ask the user for an image file first."}""" to true
        }

        val cleanedB64 = rawData.substringAfter("base64,", rawData).replace("\\s+".toRegex(), "")
        val decodedBytes = runCatching { Base64.decode(cleanedB64, Base64.DEFAULT) }.getOrElse { ex ->
            return """{"error":"Invalid base64 image data: ${ex.message}"}""" to true
        }
        if (decodedBytes.isEmpty()) {
            return """{"error":"Decoded base64 image payload is empty."}""" to true
        }

        val bitmap = withContext(Dispatchers.Default) {
            BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
        } ?: return """{"error":"Could not decode PNG/JPG/WebP bitmap from base64Data."}""" to true

        val outW = bitmap.width
        val outH = bitmap.height
        val pngBytes = withContext(Dispatchers.Default) {
            ByteArrayOutputStream().use { baos ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
                baos.toByteArray()
            }
        }
        bitmap.recycle()

        val saved = withContext(Dispatchers.IO) { storage.saveProcessedPng(pngBytes) }
            ?: return """{"error":"Failed to persist imported sprite PNG on device."}""" to true
        val (file, hash) = saved

        val catStr = args["category"]?.jsonPrimitive?.contentOrNull?.trim()?.lowercase()
        val category = when (catStr) {
            "background" -> AssetCategory.BACKGROUND
            "character" -> AssetCategory.CHARACTER
            "effect" -> AssetCategory.EFFECT
            else -> AssetCategory.PROP
        }

        val record = AssetRecord(
            id = uid("asset"),
            name = name,
            kind = AssetKind.IMAGE,
            category = category,
            mimeType = "image/png",
            hash = hash,
            dataUrl = file.absolutePath,
            width = outW,
            height = outH,
            createdAt = nowIso(),
        )
        dispatchCommandOnMain(ProjectCommands.addAsset(record))

        val out = buildJsonObject {
            put("imported", true)
            put("assetId", record.id)
            put("name", record.name)
            put("category", category.name.lowercase())
            put("width", outW)
            put("height", outH)
        }.toString()
        return out to false
    }

    private suspend fun handleRemoveSpriteBackground(args: JsonObject): Pair<String, Boolean> {
        val state = getState()
        val assetId = args["assetId"]?.jsonPrimitive?.contentOrNull?.trim()
        val asset = state.project.assets.find { it.id == assetId && it.kind == AssetKind.IMAGE }
        if (asset == null) {
            val errJson = buildJsonObject {
                put("error", "Image asset '$assetId' does not exist in the project. Call list_assets to inspect verified image assets.")
                put("availableImageAssets", buildJsonArray {
                    state.project.assets.filter { it.kind == AssetKind.IMAGE }.forEach { a ->
                        add(buildJsonObject {
                            put("id", a.id)
                            put("name", a.name)
                        })
                    }
                })
            }.toString()
            return errJson to true
        }

        val modeStr = args["mode"]?.jsonPrimitive?.contentOrNull?.trim()?.lowercase() ?: "smart_edge"
        val mode = when (modeStr) {
            "color_key", "colorkey" -> BackgroundRemovalMode.COLOR_KEY
            "white_bg", "whitebg", "white" -> BackgroundRemovalMode.WHITE_BG
            "black_bg", "blackbg", "black" -> BackgroundRemovalMode.BLACK_BG
            else -> BackgroundRemovalMode.SMART_EDGE
        }
        val targetHex = args["targetColorHex"]?.jsonPrimitive?.contentOrNull?.trim()
        val targetColor = if (!targetHex.isNullOrEmpty()) parseHexColor(targetHex) else null

        val options = BackgroundRemovalOptions(
            mode = mode,
            targetColor = targetColor,
            tolerance = (args["tolerance"]?.jsonPrimitive?.intOrNull ?: 28).coerceIn(0, 100),
            feather = (args["feather"]?.jsonPrimitive?.intOrNull ?: 12).coerceIn(0, 40),
            despill = (args["despill"]?.jsonPrimitive?.doubleOrNull?.toFloat() ?: 0.35f).coerceIn(0f, 1f),
            edgeShrinkPx = (args["edgeShrinkPx"]?.jsonPrimitive?.intOrNull ?: 0).coerceIn(0, 3),
        )
        val saveAsCopy = args["saveAsCopy"]?.jsonPrimitive?.booleanOrNull ?: false

        val sourceUrl = asset.originalDataUrl ?: asset.dataUrl
        val processed = withContext(Dispatchers.Default) {
            val sourceBitmap = ProceduralAssetRenderer.getBitmap(app, sourceUrl) ?: return@withContext null
            val (bmp, result) = SpriteBackgroundRemover.removeBackground(sourceBitmap, options)
            val bytes = ByteArrayOutputStream().use { baos ->
                bmp.compress(Bitmap.CompressFormat.PNG, 100, baos)
                baos.toByteArray()
            }
            val w = bmp.width
            val h = bmp.height
            bmp.recycle()
            Triple(bytes, result.removedPercent, w to h)
        } ?: return """{"error":"Could not decode bitmap for asset '${asset.id}'."}""" to true

        val (pngBytes, removedPercent, dims) = processed
        val saved = withContext(Dispatchers.IO) { storage.saveProcessedPng(pngBytes) }
            ?: return """{"error":"Failed to write cutout PNG for asset '${asset.id}'."}""" to true
        val (file, hash) = saved

        val resultAssetId = if (saveAsCopy) {
            val copyRecord = AssetRecord(
                id = uid("asset"),
                name = "${asset.name} (No BG)",
                kind = AssetKind.IMAGE,
                category = asset.category,
                mimeType = "image/png",
                hash = hash,
                dataUrl = file.absolutePath,
                width = dims.first,
                height = dims.second,
                createdAt = nowIso(),
                originalDataUrl = sourceUrl,
            )
            dispatchCommandOnMain(ProjectCommands.addAsset(copyRecord))
            copyRecord.id
        } else {
            dispatchCommandOnMain(
                ProjectCommands.replaceAsset(asset.id) { a ->
                    a.copy(
                        dataUrl = file.absolutePath,
                        mimeType = "image/png",
                        hash = hash,
                        width = dims.first,
                        height = dims.second,
                        originalDataUrl = a.originalDataUrl ?: a.dataUrl,
                    )
                }
            )
            asset.id
        }

        val out = buildJsonObject {
            put("removed", true)
            put("assetId", resultAssetId)
            put("sourceAssetId", asset.id)
            put("mode", mode.name.lowercase())
            put("savedAsCopy", saveAsCopy)
            put("removedPercent", (removedPercent * 10f).roundToInt() / 10.0)
            put("width", dims.first)
            put("height", dims.second)
        }.toString()
        return out to false
    }

    private suspend fun handleRenderVideo(args: JsonObject, baseUrl: String): Pair<String, Boolean> {
        val state = getState()
        val formatStr = args["format"]?.jsonPrimitive?.contentOrNull?.trim()?.lowercase()
        val format = when (formatStr) {
            "mp4" -> ExportFormat.MP4
            "gif" -> ExportFormat.GIF
            else -> return """{"error":"format is required and must be 'mp4' or 'gif'."}""" to true
        }
        val scopeStr = args["scope"]?.jsonPrimitive?.contentOrNull?.trim()?.lowercase() ?: "active_comp"
        val scope = if (scopeStr == "full_movie") ExportScope.FULL_MOVIE else ExportScope.ACTIVE_COMP
        val requestedCompId = args["compositionId"]?.jsonPrimitive?.contentOrNull?.trim()
        val targetCompId = if (!requestedCompId.isNullOrEmpty()) {
            if (state.project.compositions.none { it.id == requestedCompId }) {
                return """{"error":"Composition '$requestedCompId' not found."}""" to true
            }
            requestedCompId
        } else {
            state.activeCompositionId
        }

        val gifFps = (args["gifFps"]?.jsonPrimitive?.intOrNull ?: 15).coerceIn(6, 24)
        val gifMaxDimension = (args["gifMaxDimension"]?.jsonPrimitive?.intOrNull ?: 480).coerceIn(240, 720)
        val outFile = StudioExporter.createShareExportFile(app, state.project.name, format)

        onExportStateChanged(true, ExportProgress(0, 1, "MCP rendering ${format.name}…"))
        val res = try {
            when (format) {
                ExportFormat.MP4 -> StudioExporter.exportMp4ToFile(
                    context = app,
                    project = state.project,
                    activeCompId = targetCompId,
                    scope = scope,
                    outputFile = outFile,
                    onProgress = { p -> onExportStateChanged(true, p) },
                )
                ExportFormat.GIF -> StudioExporter.exportGifToFile(
                    context = app,
                    project = state.project,
                    activeCompId = targetCompId,
                    scope = scope,
                    outputFile = outFile,
                    gifFps = gifFps,
                    maxDimension = gifMaxDimension,
                    onProgress = { p -> onExportStateChanged(true, p) },
                )
            }
        } finally {
            onExportStateChanged(false, null)
        }

        return res.fold(
            onSuccess = { artifact ->
                val entry = McpExportedFileEntry(
                    fileName = artifact.file.name,
                    file = artifact.file,
                    mimeType = artifact.mimeType,
                    format = format.extension,
                    sizeBytes = artifact.sizeBytes,
                    createdAt = nowIso(),
                )
                onRegisterExport(entry)
                val downloadUrl = if (baseUrl.isNotBlank()) "$baseUrl/exports/${entry.fileName}" else ""
                val out = buildJsonObject {
                    put("exported", true)
                    put("format", format.extension)
                    put("scope", scope.name.lowercase())
                    put("fileName", entry.fileName)
                    put("mimeType", entry.mimeType)
                    put("sizeBytes", entry.sizeBytes)
                    put("localPath", artifact.file.absolutePath)
                    if (downloadUrl.isNotBlank()) put("downloadUrl", downloadUrl)
                }.toString()
                out to false
            },
            onFailure = { err ->
                outFile.delete()
                """{"error":"Video export failed: ${err.message}"}""" to true
            },
        )
    }

    private suspend fun handleRenderFramePng(args: JsonObject, baseUrl: String): Pair<String, Boolean> {
        val state = getState()
        val frame = (args["frame"]?.jsonPrimitive?.intOrNull ?: 0).coerceAtLeast(0)
        val scopeStr = args["scope"]?.jsonPrimitive?.contentOrNull?.trim()?.lowercase() ?: "active_comp"
        val scope = if (scopeStr == "full_movie") ExportScope.FULL_MOVIE else ExportScope.ACTIVE_COMP
        val requestedCompId = args["compositionId"]?.jsonPrimitive?.contentOrNull?.trim()
        val targetCompId = if (!requestedCompId.isNullOrEmpty()) {
            if (state.project.compositions.none { it.id == requestedCompId }) {
                return """{"error":"Composition '$requestedCompId' not found."}""" to true
            }
            requestedCompId
        } else {
            state.activeCompositionId
        }

        val dir = File(app.cacheDir, "shared_exports").apply { mkdirs() }
        val safeSlug = state.project.name.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').ifEmpty { "frame" }
        val outFile = File(dir, "${safeSlug}_f${frame}_${System.currentTimeMillis()}.png")

        val res = StudioExporter.exportPngSnapshotToFile(
            context = app,
            project = state.project,
            activeCompId = targetCompId,
            frame = frame,
            scope = scope,
            outputFile = outFile,
        )

        return res.fold(
            onSuccess = { savedFile ->
                val entry = McpExportedFileEntry(
                    fileName = savedFile.name,
                    file = savedFile,
                    mimeType = "image/png",
                    format = "png",
                    sizeBytes = savedFile.length(),
                    createdAt = nowIso(),
                )
                onRegisterExport(entry)
                val downloadUrl = if (baseUrl.isNotBlank()) "$baseUrl/exports/${entry.fileName}" else ""
                val out = buildJsonObject {
                    put("exported", true)
                    put("format", "png")
                    put("frame", frame)
                    put("scope", scope.name.lowercase())
                    put("fileName", entry.fileName)
                    put("mimeType", "image/png")
                    put("sizeBytes", entry.sizeBytes)
                    put("localPath", savedFile.absolutePath)
                    if (downloadUrl.isNotBlank()) put("downloadUrl", downloadUrl)
                }.toString()
                out to false
            },
            onFailure = { err ->
                outFile.delete()
                """{"error":"PNG frame render failed: ${err.message}"}""" to true
            },
        )
    }
}
