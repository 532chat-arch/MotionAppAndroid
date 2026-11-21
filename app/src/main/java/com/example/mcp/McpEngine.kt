package com.example.mcp

import com.example.commands.ProjectCommand
import com.example.commands.ProjectCommands
import com.example.commands.uid
import com.example.data.createBlankComposition
import com.example.effects.DevicePerformanceProfile
import com.example.effects.EffectBudgetValidator
import com.example.effects.EffectCategory
import com.example.effects.EffectsLibraryRegistry
import com.example.effects.PortableEffectKnowledgeExporter
import com.example.engine.AnimationPresetRegistry
import com.example.engine.SKY_ATMOSPHERE_PRESETS
import com.example.engine.applyPatch
import com.example.engine.evaluateCompositionAtFrame
import com.example.engine.parsePlainLyrics
import com.example.schema.AssetCategory
import com.example.schema.AssetKind
import com.example.schema.AudioClipDocument
import com.example.schema.AudioTrackDocument
import com.example.schema.CameraAnimatableProp
import com.example.schema.InterpolationType
import com.example.schema.LayerAnimatableProp
import com.example.schema.LayerDocument
import com.example.schema.LayerRole
import com.example.schema.LyricsConfig
import com.example.schema.LyricsStyle
import com.example.schema.MotionProject
import com.example.schema.ProjectTimelineClip
import com.example.schema.SceneObjectDocument
import com.example.schema.ShapeType
import com.example.schema.StudioJson
import com.example.schema.Transform2D
import com.example.schema.TransitionType
import com.example.schema.getActiveCompFps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.max
import kotlin.math.roundToInt

@Serializable
data class McpToolInfo(
    val name: String,
    val title: String = "",
    val description: String = "",
    val sampleArgsJson: String = "{}",
    val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {})
    },
)

@Serializable
data class McpResourceInfo(
    val uri: String,
    val name: String,
    val description: String = "",
    val mimeType: String = "application/json",
)

@Serializable
data class McpPromptInfo(
    val name: String,
    val description: String = "",
)

data class McpActivityLogEntry(
    val id: String,
    val timestamp: String,
    val direction: String, // "local", "server", or "client"
    val serverName: String,
    val method: String,
    val detail: String,
    val isError: Boolean,
    val durationMs: Long,
)

data class ExternalMcpServer(
    val id: String,
    val name: String,
    val url: String,
    val bearerToken: String = "",
    val status: String = "disconnected",
    val errorMessage: String? = null,
    val sessionId: String? = null,
    val tools: List<McpToolInfo> = emptyList(),
    val resources: List<McpResourceInfo> = emptyList(),
    val prompts: List<McpPromptInfo> = emptyList(),
)

object LocalMcpEngine {
    val tools: List<McpToolInfo> get() = McpCatalog.tools
    val prompts: List<McpPromptInfo> get() = McpCatalog.prompts

    fun resourcesFor(project: MotionProject): List<McpResourceInfo> = buildList {
        add(McpResourceInfo("motion://project/current", "Current MotionProject JSON", "Full project state for ${project.name}"))
        add(McpResourceInfo("motion://assets/list", "Project Assets & Characters", "All real image sprites, audio tracks, and character sheets currently in the project"))
        add(McpResourceInfo("motion://effects/catalog", "Modular Effects Catalog (61)", "All 61 modular effects across Phases 1–5 with IDs, categories, and costs"))
        add(McpResourceInfo("motion://effects/combos", "Multi-Effect Combos", "Built-in and custom effect combinations"))
        add(McpResourceInfo("motion://project/schema", "Motion Studio Schema Guide", "Layer kinds, shapes, easing curves, and preset IDs"))
        add(McpResourceInfo("motion://effects/knowledge.json", "Effects Library Knowledge (JSON)", "Portable AI effects schema with CPU/GPU/memory cost metadata & device profiles", "application/json"))
        add(McpResourceInfo("motion://effects/knowledge.md", "Effects Library Knowledge (Markdown)", "Human & AI-readable low-end Android effects guide & parameter table", "text/markdown"))
        add(McpResourceInfo("motion://exports/recent", "Recent Exported Videos & Frames", "Recent MP4, GIF, and PNG renders with LAN download URLs"))
        for (c in project.compositions) {
            add(McpResourceInfo("motion://composition/${c.id}", "Composition: ${c.name}", "${c.settings.width}x${c.settings.height}, ${c.settings.durationFrames}f, ${c.layers.size} layers"))
        }
    }

    fun readResource(
        project: MotionProject,
        uri: String,
        recentExports: List<McpExportedFileEntry> = emptyList(),
        baseUrl: String = "",
    ): String = when {
        uri == "motion://project/current" -> StudioJson.encodeToString(project)
        uri == "motion://assets/list" -> buildAssetsJson(project, kindFilter = "all").toString()
        uri == "motion://effects/catalog" -> buildEffectsCatalogJson(null, null, null, null).toString()
        uri == "motion://effects/combos" -> StudioJson.encodeToString(EffectsLibraryRegistry.allCombos())
        uri == "motion://effects/knowledge.json" -> PortableEffectKnowledgeExporter.exportJson()
        uri == "motion://effects/knowledge.md" -> PortableEffectKnowledgeExporter.exportMarkdown()
        uri == "motion://exports/recent" -> buildJsonArray {
            for (e in recentExports) {
                add(buildJsonObject {
                    put("fileName", e.fileName)
                    put("format", e.format)
                    put("mimeType", e.mimeType)
                    put("sizeBytes", e.sizeBytes)
                    put("createdAt", e.createdAt)
                    if (baseUrl.isNotBlank()) put("downloadUrl", "$baseUrl/exports/${e.fileName}")
                })
            }
        }.toString()
        uri == "motion://project/schema" -> buildJsonObject {
            put("schemaVersion", 1)
            put("layerKinds", buildJsonArray { listOf("sprite", "character", "shape", "text", "nestedComposition").forEach { add(JsonPrimitive(it)) } })
            put("shapes", buildJsonArray { listOf("rect", "ellipse", "polygon", "star").forEach { add(JsonPrimitive(it)) } })
            put("interpolations", buildJsonArray { listOf("linear", "hold", "easeIn", "easeOut", "easeInOut").forEach { add(JsonPrimitive(it)) } })
            put("presetCount", AnimationPresetRegistry.allPresets().size)
            put("effectModuleCount", EffectsLibraryRegistry.builtinModules.size)
            put("askFirstPolicy", "Always audit list_assets & check_video_requirements; never invent missing assets or copy.")
        }.toString()
        uri.startsWith("motion://composition/") -> {
            val id = uri.removePrefix("motion://composition/")
            val comp = project.compositions.find { it.id == id }
            if (comp != null) StudioJson.encodeToString(comp) else """{"error":"Composition not found: $id"}"""
        }
        else -> """{"error":"Unknown resource URI: $uri"}"""
    }

    data class LocalToolOutcome(
        val outputJson: String,
        val command: ProjectCommand? = null,
        val switchCompId: String? = null,
        val isError: Boolean = false,
    )

    fun executeTool(
        project: MotionProject,
        activeCompId: String,
        toolName: String,
        args: JsonObject,
    ): LocalToolOutcome {
        val targetCompId = args["compositionId"]?.jsonPrimitive?.contentOrNull ?: activeCompId
        val comp = project.compositions.find { it.id == targetCompId }
            ?: if (args["compositionId"] == null) project.compositions.firstOrNull() else null

        return when (toolName) {
            "get_project_summary" -> LocalToolOutcome(
                outputJson = buildJsonObject {
                    put("id", project.id)
                    put("name", project.name)
                    put("activeCompositionId", activeCompId)
                    put("fps", "${project.fps.num}/${project.fps.den}")
                    put("compositions", project.compositions.size)
                    put("imageAssets", project.assets.count { it.kind == AssetKind.IMAGE })
                    put("audioAssets", project.assets.count { it.kind == AssetKind.AUDIO })
                    put("characters", project.characters.size)
                    put("movieClips", project.projectTimeline.size)
                    put("reminder", "Call list_assets and check_video_requirements before creating scenes; never invent missing assets or text.")
                }.toString()
            )

            "list_assets" -> {
                val kind = args["kind"]?.jsonPrimitive?.contentOrNull ?: "all"
                LocalToolOutcome(buildAssetsJson(project, kind).toString())
            }

            "list_compositions" -> LocalToolOutcome(
                outputJson = buildJsonArray {
                    for (c in project.compositions) {
                        add(buildJsonObject {
                            put("id", c.id)
                            put("name", c.name)
                            put("isActive", c.id == activeCompId)
                            put("width", c.settings.width)
                            put("height", c.settings.height)
                            put("durationFrames", c.settings.durationFrames)
                            put("layers", buildJsonArray {
                                c.layers.forEach { l ->
                                    add(buildJsonObject {
                                        put("id", l.id)
                                        put("name", l.name)
                                        put("role", l.role.name.lowercase())
                                        put("objectKind", l.objects.firstOrNull()?.let { it::class.simpleName } ?: "Empty")
                                        put("effectCount", l.modifiers.size)
                                    })
                                }
                            })
                            put("audioTracks", c.audioTracks.size)
                        })
                    }
                }.toString()
            )

            "check_video_requirements" -> handleCheckVideoRequirements(project, args)

            "list_effects" -> {
                val catStr = args["category"]?.jsonPrimitive?.contentOrNull?.takeUnless { it.equals("all", ignoreCase = true) }
                val phase = args["phase"]?.jsonPrimitive?.intOrNull
                val profileStr = args["profile"]?.jsonPrimitive?.contentOrNull
                val query = args["query"]?.jsonPrimitive?.contentOrNull
                LocalToolOutcome(buildEffectsCatalogJson(catStr, phase, profileStr, query).toString())
            }

            "create_composition" -> {
                val name = args["name"]?.jsonPrimitive?.contentOrNull?.trim()
                val w = args["width"]?.jsonPrimitive?.intOrNull
                val h = args["height"]?.jsonPrimitive?.intOrNull
                val dur = args["durationFrames"]?.jsonPrimitive?.intOrNull
                if (name.isNullOrEmpty() || w == null || h == null || dur == null) {
                    return missingParamError(
                        "create_composition requires explicit user-confirmed name, width, height, and durationFrames. Ask the user for video aspect ratio/resolution and duration before creating a composition.",
                    )
                }
                val envId = args["environmentPreset"]?.jsonPrimitive?.contentOrNull
                var newComp = createBlankComposition(name, w.coerceIn(160, 1920), h.coerceIn(160, 1920), dur.coerceIn(24, 3600))
                val patch = SKY_ATMOSPHERE_PRESETS.find { it.id == envId }?.patch
                if (patch != null) {
                    newComp = newComp.copy(environment = newComp.environment.applyPatch(patch))
                }
                LocalToolOutcome(
                    outputJson = buildJsonObject {
                        put("created", true)
                        put("compositionId", newComp.id)
                        put("name", newComp.name)
                        put("width", newComp.settings.width)
                        put("height", newComp.settings.height)
                        put("durationFrames", newComp.settings.durationFrames)
                    }.toString(),
                    command = ProjectCommands.addComposition(newComp),
                    switchCompId = newComp.id,
                )
            }

            "set_active_composition" -> {
                val cid = args["compositionId"]?.jsonPrimitive?.contentOrNull?.trim()
                val found = project.compositions.find { it.id == cid }
                    ?: return missingParamError(
                        "Composition '$cid' not found. Available composition IDs: ${project.compositions.joinToString { it.id }}",
                    )
                LocalToolOutcome(
                    outputJson = """{"switched":true,"compositionId":"${found.id}","name":"${found.name}"}""",
                    switchCompId = found.id,
                )
            }

            "add_movie_clip" -> {
                val cid = args["compositionId"]?.jsonPrimitive?.contentOrNull?.trim()
                val found = project.compositions.find { it.id == cid }
                    ?: return missingParamError(
                        "Composition '$cid' not found. Available composition IDs: ${project.compositions.joinToString { it.id }}",
                    )
                val trStr = args["transition"]?.jsonPrimitive?.contentOrNull ?: "cut"
                val trType = when (trStr.lowercase()) {
                    "crossfade", "crossdissolve", "cross_dissolve" -> TransitionType.CROSS_DISSOLVE
                    "fade_black", "fadeblack" -> TransitionType.FADE_BLACK
                    else -> TransitionType.CUT
                }
                val trFrames = (args["transitionFrames"]?.jsonPrimitive?.intOrNull ?: 0).coerceIn(0, 48)
                val nextStart = project.projectTimeline.sumOf { max(1, it.durationFrames - it.transitionDurationFrames) }
                val clip = ProjectTimelineClip(
                    id = uid("mclip"),
                    compositionId = found.id,
                    startFrame = nextStart,
                    durationFrames = found.settings.durationFrames,
                    transitionIn = trType,
                    transitionDurationFrames = trFrames,
                )
                LocalToolOutcome(
                    outputJson = """{"added":true,"clipId":"${clip.id}","compositionId":"${found.id}","startFrame":$nextStart}""",
                    command = ProjectCommands.addMovieClip(clip),
                )
            }

            "add_sprite_layer" -> {
                if (comp == null) return missingParamError("Composition '$targetCompId' not found.")
                val assetId = args["assetId"]?.jsonPrimitive?.contentOrNull?.trim()
                val asset = project.assets.find { it.id == assetId && it.kind == AssetKind.IMAGE }
                    ?: return missingAssetError(
                        message = "Image asset '$assetId' does not exist in the project. Do NOT invent an asset ID or substitute a placeholder shape—ask the user to import the sprite (or upload via import_sprite_base64) or pick from availableImageAssets.",
                        project = project,
                    )
                val w = (args["width"]?.jsonPrimitive?.doubleOrNull ?: (asset.width ?: 320).toDouble())
                    .coerceIn(32.0, comp.settings.width.toDouble() * 2.0)
                val h = (args["height"]?.jsonPrimitive?.doubleOrNull ?: (asset.height ?: 320).toDouble())
                    .coerceIn(32.0, comp.settings.height.toDouble() * 2.0)
                val roleStr = args["role"]?.jsonPrimitive?.contentOrNull
                val role = LayerRole.entries.find { it.name.equals(roleStr, ignoreCase = true) } ?: when (asset.category) {
                    AssetCategory.BACKGROUND -> LayerRole.BACKGROUND
                    AssetCategory.CHARACTER -> LayerRole.CHARACTER
                    AssetCategory.EFFECT -> LayerRole.EFFECT
                    else -> LayerRole.PROP
                }
                val layer = LayerDocument(
                    id = uid("layer"),
                    name = args["name"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty().ifEmpty { asset.name },
                    role = role,
                    depth = args["depth"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                    outFrame = comp.settings.durationFrames,
                    transform = Transform2D(
                        x = args["x"]?.jsonPrimitive?.doubleOrNull ?: (comp.settings.width / 2.0),
                        y = args["y"]?.jsonPrimitive?.doubleOrNull ?: (comp.settings.height / 2.0),
                    ),
                    objects = listOf(
                        SceneObjectDocument.SpriteObject(
                            id = uid("obj"),
                            name = asset.name,
                            assetId = asset.id,
                            width = w,
                            height = h,
                            flipX = args["flipX"]?.jsonPrimitive?.booleanOrNull ?: false,
                            flipY = args["flipY"]?.jsonPrimitive?.booleanOrNull ?: false,
                        )
                    ),
                )
                LocalToolOutcome(
                    outputJson = """{"added":true,"layerId":"${layer.id}","assetId":"${asset.id}","compositionId":"${comp.id}"}""",
                    command = ProjectCommands.addLayer(comp.id, layer),
                )
            }

            "add_character_layer" -> {
                if (comp == null) return missingParamError("Composition '$targetCompId' not found.")
                val charId = args["characterId"]?.jsonPrimitive?.contentOrNull?.trim()
                val charDef = project.characters.find { it.id == charId }
                    ?: return missingAssetError(
                        message = "Character '$charId' does not exist in the project. Ask the user to import/create the character sprite sheet first or choose from availableCharacters.",
                        project = project,
                    )
                val requestedClip = args["clipId"]?.jsonPrimitive?.contentOrNull
                val clipId = charDef.clips.find { it.id == requestedClip }?.id
                    ?: charDef.defaultClipId.ifEmpty { charDef.clips.firstOrNull()?.id ?: "" }
                val layer = LayerDocument(
                    id = uid("layer"),
                    name = args["name"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty().ifEmpty { charDef.name },
                    role = LayerRole.CHARACTER,
                    outFrame = comp.settings.durationFrames,
                    transform = Transform2D(
                        x = args["x"]?.jsonPrimitive?.doubleOrNull ?: (comp.settings.width / 2.0),
                        y = args["y"]?.jsonPrimitive?.doubleOrNull ?: (comp.settings.height * 0.62),
                    ),
                    objects = listOf(
                        SceneObjectDocument.CharacterObject(
                            id = uid("obj"),
                            name = charDef.name,
                            characterId = charDef.id,
                            activeClipId = clipId,
                            flipX = args["flipX"]?.jsonPrimitive?.booleanOrNull ?: false,
                            width = args["width"]?.jsonPrimitive?.doubleOrNull ?: 220.0,
                            height = args["height"]?.jsonPrimitive?.doubleOrNull ?: 220.0,
                        )
                    ),
                )
                LocalToolOutcome(
                    outputJson = """{"added":true,"layerId":"${layer.id}","characterId":"${charDef.id}","clipId":"$clipId"}""",
                    command = ProjectCommands.addLayer(comp.id, layer),
                )
            }

            "add_layer" -> {
                if (comp == null) return missingParamError("Composition '$targetCompId' not found.")
                val name = args["name"]?.jsonPrimitive?.contentOrNull?.trim()
                    ?: return missingParamError("name is required for add_layer.")
                val kind = args["kind"]?.jsonPrimitive?.contentOrNull?.trim()?.lowercase()
                    ?: return missingParamError("kind ('text' or 'shape') is required for add_layer.")
                val x = args["x"]?.jsonPrimitive?.doubleOrNull ?: (comp.settings.width / 2.0)
                val y = args["y"]?.jsonPrimitive?.doubleOrNull ?: (comp.settings.height / 2.0)
                val obj: SceneObjectDocument = when (kind) {
                    "text" -> {
                        val txt = args["text"]?.jsonPrimitive?.contentOrNull?.trim()
                        if (txt.isNullOrEmpty()) {
                            return missingParamError("Text layers require explicit user-provided 'text'. Do not invent copy—ask the user for the exact title or text.")
                        }
                        SceneObjectDocument.TextObject(
                            id = uid("obj"),
                            name = name,
                            text = txt,
                            fontSize = args["fontSize"]?.jsonPrimitive?.doubleOrNull ?: 48.0,
                            color = args["color"]?.jsonPrimitive?.contentOrNull ?: "#F8FAFC",
                        )
                    }
                    "shape" -> {
                        val shape = when (args["shapeType"]?.jsonPrimitive?.contentOrNull?.lowercase()) {
                            "ellipse" -> ShapeType.ELLIPSE
                            "polygon" -> ShapeType.POLYGON
                            "star" -> ShapeType.STAR
                            else -> ShapeType.RECT
                        }
                        SceneObjectDocument.ShapeObject(
                            id = uid("obj"),
                            name = name,
                            shapeType = shape,
                            width = args["width"]?.jsonPrimitive?.doubleOrNull ?: 160.0,
                            height = args["height"]?.jsonPrimitive?.doubleOrNull ?: 160.0,
                            fill = args["fill"]?.jsonPrimitive?.contentOrNull ?: "#38BDF8",
                            stroke = args["stroke"]?.jsonPrimitive?.contentOrNull ?: "#E0F2FE",
                            strokeWidth = args["strokeWidth"]?.jsonPrimitive?.doubleOrNull ?: 3.0,
                        )
                    }
                    else -> return missingParamError("Unsupported kind '$kind'. Use add_sprite_layer for images, add_character_layer for characters, or kind='text'/'shape'.")
                }
                val layer = LayerDocument(
                    id = uid("layer"),
                    name = name,
                    role = if (kind == "text") LayerRole.FOREGROUND else LayerRole.PROP,
                    depth = args["depth"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                    outFrame = comp.settings.durationFrames,
                    transform = Transform2D(x = x, y = y),
                    objects = listOf(obj),
                )
                LocalToolOutcome(
                    outputJson = """{"added":true,"layerId":"${layer.id}","compositionId":"${comp.id}"}""",
                    command = ProjectCommands.addLayer(comp.id, layer),
                )
            }

            "delete_layer" -> {
                if (comp == null) return missingParamError("Composition '$targetCompId' not found.")
                val layerId = args["layerId"]?.jsonPrimitive?.contentOrNull?.trim()
                if (comp.layers.none { it.id == layerId }) {
                    return missingParamError("Layer '$layerId' not found in composition '${comp.id}'.")
                }
                LocalToolOutcome(
                    outputJson = """{"deleted":true,"layerId":"$layerId"}""",
                    command = ProjectCommands.deleteLayer(comp.id, layerId!!),
                )
            }

            "add_audio_clip" -> {
                if (comp == null) return missingParamError("Composition '$targetCompId' not found.")
                val assetId = args["assetId"]?.jsonPrimitive?.contentOrNull?.trim()
                val asset = project.assets.find { it.id == assetId && it.kind == AssetKind.AUDIO }
                    ?: return missingAssetError(
                        message = "Audio asset '$assetId' does not exist in the project. Ask the user to import an audio file or pick from availableAudioAssets.",
                        project = project,
                    )
                val fps = getActiveCompFps(project, comp.id)
                val startFrame = (args["startFrame"]?.jsonPrimitive?.intOrNull ?: 0).coerceAtLeast(0)
                val durFrames = (args["durationFrames"]?.jsonPrimitive?.intOrNull
                    ?: max(24, ((asset.durationSec ?: 5.0) * fps).roundToInt())).coerceAtLeast(1)
                val clip = AudioClipDocument(
                    id = uid("aclip"),
                    assetId = asset.id,
                    name = asset.name,
                    startFrame = startFrame,
                    durationFrames = durFrames,
                )
                val cmd = ProjectCommand("Add Audio Clip ${asset.name}") { p ->
                    p.copy(compositions = p.compositions.map { c ->
                        if (c.id != comp.id) c
                        else {
                            val tracks = c.audioTracks.ifEmpty { listOf(AudioTrackDocument(uid("atrack"), "Audio Track 1")) }
                            val firstId = tracks.first().id
                            c.copy(audioTracks = tracks.map { t -> if (t.id == firstId) t.copy(clips = t.clips + clip) else t })
                        }
                    })
                }
                LocalToolOutcome(
                    outputJson = """{"added":true,"clipId":"${clip.id}","assetId":"${asset.id}","durationFrames":$durFrames}""",
                    command = cmd,
                )
            }

            "update_layer_transform" -> {
                if (comp == null) return missingParamError("Composition '$targetCompId' not found.")
                val layerId = args["layerId"]?.jsonPrimitive?.contentOrNull?.trim()
                if (comp.layers.none { it.id == layerId }) {
                    return missingParamError("Layer '$layerId' not found. Available layer IDs: ${comp.layers.joinToString { it.id }}")
                }
                val frame = args["frame"]?.jsonPrimitive?.intOrNull ?: 0
                val cmd = ProjectCommands.updateLayerTransformValue(
                    compId = comp.id,
                    layerId = layerId!!,
                    frame = frame,
                    x = args["x"]?.jsonPrimitive?.doubleOrNull,
                    y = args["y"]?.jsonPrimitive?.doubleOrNull,
                    rotationDeg = args["rotationDeg"]?.jsonPrimitive?.doubleOrNull,
                    scaleX = args["scaleX"]?.jsonPrimitive?.doubleOrNull,
                    scaleY = args["scaleY"]?.jsonPrimitive?.doubleOrNull,
                    opacity = args["opacity"]?.jsonPrimitive?.doubleOrNull,
                )
                LocalToolOutcome("""{"updated":true,"layerId":"$layerId","frame":$frame}""", command = cmd)
            }

            "set_keyframe" -> {
                if (comp == null) return missingParamError("Composition '$targetCompId' not found.")
                val layerId = args["layerId"]?.jsonPrimitive?.contentOrNull?.trim()
                if (comp.layers.none { it.id == layerId }) {
                    return missingParamError("Layer '$layerId' not found. Available layer IDs: ${comp.layers.joinToString { it.id }}")
                }
                val prop = LayerAnimatableProp.fromKey(args["property"]?.jsonPrimitive?.contentOrNull ?: "")
                    ?: return missingParamError("Invalid property. Use one of: x, y, rotation, scaleX, scaleY, opacity.")
                val frame = args["frame"]?.jsonPrimitive?.intOrNull
                    ?: return missingParamError("frame is required.")
                val value = args["value"]?.jsonPrimitive?.doubleOrNull
                    ?: return missingParamError("value is required.")
                val interp = parseInterpolation(args["interpolation"]?.jsonPrimitive?.contentOrNull)
                LocalToolOutcome(
                    outputJson = """{"keyframed":true,"layerId":"$layerId","property":"${prop.key}","frame":$frame,"value":$value,"interpolation":"${interp.name.lowercase()}"}""",
                    command = ProjectCommands.setLayerKeyframe(comp.id, layerId!!, prop, frame, value, interp),
                )
            }

            "clear_layer_animation" -> {
                if (comp == null) return missingParamError("Composition '$targetCompId' not found.")
                val layerId = args["layerId"]?.jsonPrimitive?.contentOrNull?.trim()
                if (comp.layers.none { it.id == layerId }) {
                    return missingParamError("Layer '$layerId' not found.")
                }
                val propStr = args["property"]?.jsonPrimitive?.contentOrNull ?: "all"
                if (propStr.equals("all", ignoreCase = true)) {
                    LocalToolOutcome(
                        outputJson = """{"cleared":"all","layerId":"$layerId"}""",
                        command = ProjectCommands.clearAllLayerAnimation(comp.id, layerId!!),
                    )
                } else {
                    val prop = LayerAnimatableProp.fromKey(propStr)
                        ?: return missingParamError("Invalid property '$propStr'.")
                    LocalToolOutcome(
                        outputJson = """{"cleared":"${prop.key}","layerId":"$layerId"}""",
                        command = ProjectCommands.clearLayerKeyframeChannel(comp.id, layerId!!, prop),
                    )
                }
            }

            "set_camera_keyframe" -> {
                if (comp == null) return missingParamError("Composition '$targetCompId' not found.")
                val prop = CameraAnimatableProp.fromKey(args["property"]?.jsonPrimitive?.contentOrNull ?: "")
                    ?: return missingParamError("Invalid camera property. Use one of: x, y, zoom.")
                val frame = args["frame"]?.jsonPrimitive?.intOrNull ?: return missingParamError("frame is required.")
                val value = args["value"]?.jsonPrimitive?.doubleOrNull ?: return missingParamError("value is required.")
                val interp = parseInterpolation(args["interpolation"]?.jsonPrimitive?.contentOrNull)
                LocalToolOutcome(
                    outputJson = """{"keyframed":true,"target":"camera","property":"${prop.key}","frame":$frame,"value":$value}""",
                    command = ProjectCommands.setCameraKeyframe(comp.id, prop, frame, value, interp),
                )
            }

            "apply_motion_preset" -> {
                if (comp == null) return missingParamError("Composition '$targetCompId' not found.")
                val layerId = args["layerId"]?.jsonPrimitive?.contentOrNull?.trim()
                if (comp.layers.none { it.id == layerId }) {
                    return missingParamError("Layer '$layerId' not found.")
                }
                val presetId = args["presetId"]?.jsonPrimitive?.contentOrNull?.trim()
                    ?: return missingParamError("presetId is required.")
                val fps = getActiveCompFps(project, comp.id)
                val mod = AnimationPresetRegistry.createModifierFromPreset(presetId, fps, uid("mod"))
                    ?: return missingParamError("Unknown preset: $presetId")
                LocalToolOutcome(
                    outputJson = """{"applied":true,"layerId":"$layerId","presetId":"$presetId","modifierId":"${mod.id}"}""",
                    command = ProjectCommands.addLayerModifier(comp.id, layerId!!, mod),
                )
            }

            "update_environment" -> {
                if (comp == null) return missingParamError("Composition '$targetCompId' not found.")
                val presetId = args["presetId"]?.jsonPrimitive?.contentOrNull?.trim()
                    ?: return missingParamError("presetId is required (twilight, dawn, aurora, storm, daylight).")
                val patch = SKY_ATMOSPHERE_PRESETS.find { it.id == presetId }?.patch
                    ?: return missingParamError("Unknown environment preset: $presetId")
                LocalToolOutcome(
                    outputJson = """{"updated":true,"environmentPreset":"$presetId"}""",
                    command = ProjectCommands.updateEnvironment(comp.id, patch),
                )
            }

            "set_lyrics_cues" -> {
                if (comp == null) return missingParamError("Composition '$targetCompId' not found.")
                val layerId = args["layerId"]?.jsonPrimitive?.contentOrNull?.trim()
                val layer = comp.layers.find { it.id == layerId }
                    ?: return missingParamError("Text layer '$layerId' not found.")
                val textObj = layer.objects.firstOrNull { it is SceneObjectDocument.TextObject } as? SceneObjectDocument.TextObject
                    ?: return missingParamError("Layer '$layerId' does not contain a text object.")
                val rawText = args["text"]?.jsonPrimitive?.contentOrNull?.trim()
                if (rawText.isNullOrEmpty()) {
                    return missingParamError("Exact user-provided lyrics 'text' is required. Do NOT invent lyrics—ask the user for the lyrics text first.")
                }
                val cues = parsePlainLyrics(rawText, getActiveCompFps(project, comp.id))
                val styleName = args["style"]?.jsonPrimitive?.contentOrNull ?: "karaoke"
                val style = LyricsStyle.entries.find { it.name.equals(styleName, ignoreCase = true) } ?: LyricsStyle.KARAOKE
                val cfg = (textObj.lyrics ?: LyricsConfig()).copy(enabled = true, style = style, cues = cues)
                LocalToolOutcome(
                    outputJson = """{"updated":true,"layerId":"${layer.id}","cueCount":${cues.size},"style":"${style.name.lowercase()}"}""",
                    command = ProjectCommands.updateObject(comp.id, layer.id, textObj.id) { o ->
                        if (o is SceneObjectDocument.TextObject) o.copy(lyrics = cfg) else o
                    },
                )
            }

            "evaluate_frame" -> {
                if (comp == null) return missingParamError("Composition '$targetCompId' not found.")
                val frame = args["frame"]?.jsonPrimitive?.intOrNull ?: 0
                val ev = evaluateCompositionAtFrame(project, comp.id, frame)
                    ?: return missingParamError("Evaluation failed")
                val out = buildJsonObject {
                    put("compositionId", comp.id)
                    put("frame", frame)
                    put("camera", buildJsonObject {
                        put("x", ev.camera.x)
                        put("y", ev.camera.y)
                        put("zoom", ev.camera.zoom)
                    })
                    put("activeLayers", buildJsonArray {
                        ev.layers.filter { it.isActiveAtFrame }.forEach { ls ->
                            add(buildJsonObject {
                                put("id", ls.layer.id)
                                put("name", ls.layer.name)
                                put("x", ls.worldTransform.x)
                                put("y", ls.worldTransform.y)
                                put("opacity", ls.worldTransform.opacity)
                            })
                        }
                    })
                }
                LocalToolOutcome(out.toString())
            }

            "apply_effect_module" -> {
                if (comp == null) return missingParamError("Composition '$targetCompId' not found.")
                val layerId = args["layerId"]?.jsonPrimitive?.contentOrNull?.trim()
                val layer = comp.layers.find { it.id == layerId }
                    ?: return missingParamError("Layer '$layerId' not found.")
                val requestedId = args["effectId"]?.jsonPrimitive?.contentOrNull?.trim()
                    ?: return missingParamError("effectId is required. Call list_effects to inspect valid effect IDs.")
                val profileStr = args["profile"]?.jsonPrimitive?.contentOrNull ?: "low_end"
                val profile = DevicePerformanceProfile.entries.find { it.name.equals(profileStr, ignoreCase = true) }
                    ?: DevicePerformanceProfile.LOW_END
                val fps = getActiveCompFps(project, comp.id)
                val meta = EffectsLibraryRegistry.getPerformanceMetadata(requestedId)
                val resolvedId = if (profile == DevicePerformanceProfile.LOW_END && !meta.supportsLowEnd && meta.fallbackEffect != null) {
                    meta.fallbackEffect
                } else requestedId
                val startFrame = (args["startFrame"]?.jsonPrimitive?.intOrNull ?: 0).coerceAtLeast(0)
                var instance = EffectsLibraryRegistry.createInstanceFromModule(resolvedId, fps, uid("fx"), startFrame)
                    ?: return missingParamError("Unknown effect module: $resolvedId. Call list_effects for valid IDs.")
                val overrideParams = (args["params"] as? JsonObject)?.mapNotNull { (k, v) ->
                    v.jsonPrimitive.doubleOrNull?.let { k to it }
                }?.toMap().orEmpty()
                instance = instance.copy(
                    intensity = args["intensity"]?.jsonPrimitive?.doubleOrNull ?: instance.intensity,
                    speed = args["speed"]?.jsonPrimitive?.doubleOrNull ?: instance.speed,
                    loop = args["loop"]?.jsonPrimitive?.booleanOrNull ?: instance.loop,
                    params = if (overrideParams.isEmpty()) instance.params else instance.params + overrideParams,
                )
                val validation = EffectBudgetValidator.validateLayerStack(layer.modifiers + instance, profile)
                val out = buildJsonObject {
                    put("applied", true)
                    put("layerId", layer.id)
                    put("effectId", resolvedId)
                    put("modifierId", instance.id)
                    put("substitutedFallback", resolvedId != requestedId)
                    put("withinBudget", validation.isWithinBudget)
                    put("usedBudgetPoints", validation.usedBudgetPoints)
                    put("maxBudgetPoints", validation.maxBudgetPoints)
                    put("warnings", buildJsonArray { validation.warnings.forEach { add(JsonPrimitive(it)) } })
                }
                LocalToolOutcome(
                    outputJson = out.toString(),
                    command = ProjectCommands.addLayerModifier(comp.id, layer.id, instance),
                )
            }

            "apply_effect_combo" -> {
                if (comp == null) return missingParamError("Composition '$targetCompId' not found.")
                val layerId = args["layerId"]?.jsonPrimitive?.contentOrNull?.trim()
                val layer = comp.layers.find { it.id == layerId }
                    ?: return missingParamError("Layer '$layerId' not found.")
                val comboId = args["comboId"]?.jsonPrimitive?.contentOrNull?.trim()
                val combo = EffectsLibraryRegistry.allCombos().find { it.id == comboId }
                    ?: return missingParamError("Unknown comboId '$comboId'. Available combos: ${EffectsLibraryRegistry.allCombos().joinToString { it.id }}")
                val fps = getActiveCompFps(project, comp.id)
                val startFrame = (args["startFrame"]?.jsonPrimitive?.intOrNull ?: 0).coerceAtLeast(0)
                val mods = EffectsLibraryRegistry.instantiateCombo(combo, fps, startFrame) { uid("fx") }
                LocalToolOutcome(
                    outputJson = buildJsonObject {
                        put("applied", true)
                        put("layerId", layer.id)
                        put("comboId", combo.id)
                        put("addedCount", mods.size)
                    }.toString(),
                    command = ProjectCommands.addLayerModifiersBatch(comp.id, layer.id, mods, combo.name),
                )
            }

            "validate_effect_stack" -> {
                if (comp == null) return missingParamError("Composition '$targetCompId' not found.")
                val layerId = args["layerId"]?.jsonPrimitive?.contentOrNull?.trim()
                val layer = comp.layers.find { it.id == layerId }
                    ?: return missingParamError("Layer '$layerId' not found.")
                val profileStr = args["profile"]?.jsonPrimitive?.contentOrNull ?: "low_end"
                val profile = DevicePerformanceProfile.entries.find { it.name.equals(profileStr, ignoreCase = true) }
                    ?: DevicePerformanceProfile.LOW_END
                val res = EffectBudgetValidator.validateLayerStack(layer.modifiers, profile)
                val out = buildJsonObject {
                    put("layerId", layer.id)
                    put("profile", profile.name.lowercase())
                    put("activeEffectCount", res.activeEffectCount)
                    put("usedBudgetPoints", res.usedBudgetPoints)
                    put("maxBudgetPoints", res.maxBudgetPoints)
                    put("isWithinBudget", res.isWithinBudget)
                    put("recommendedProfile", res.recommendedProfile.name.lowercase())
                    put("warnings", buildJsonArray { res.warnings.forEach { add(JsonPrimitive(it)) } })
                }
                LocalToolOutcome(out.toString())
            }

            else -> LocalToolOutcome("""{"error":"Unknown or async-only local tool: $toolName"}""", isError = true)
        }
    }

    private fun handleCheckVideoRequirements(project: MotionProject, args: JsonObject): LocalToolOutcome {
        val missingAssets = mutableListOf<String>()
        val missingDetails = mutableListOf<String>()
        val questions = mutableListOf<String>()

        val reqAssetIds = (args["requiredAssetIds"] as? JsonArray)
            ?.mapNotNull { it.jsonPrimitive.contentOrNull?.trim() }
            .orEmpty()
        for (aid in reqAssetIds) {
            if (project.assets.none { it.id == aid }) {
                missingAssets.add("assetId:$aid")
                questions.add("Asset '$aid' is not in the project. Please import it or choose an existing asset ID from availableAssets.")
            }
        }

        val reqCharIds = (args["requiredCharacterIds"] as? JsonArray)
            ?.mapNotNull { it.jsonPrimitive.contentOrNull?.trim() }
            .orEmpty()
        for (cid in reqCharIds) {
            if (project.characters.none { it.id == cid }) {
                missingAssets.add("characterId:$cid")
                questions.add("Character '$cid' is not in the project. Please import a character sprite sheet or pick from availableCharacters.")
            }
        }

        val reqSlots = (args["requiredAssetSlots"] as? JsonArray)
            ?.mapNotNull { it.jsonPrimitive.contentOrNull?.trim()?.lowercase() }
            .orEmpty()
        for (slot in reqSlots) {
            val hasMatch = when (slot) {
                "audio", "music", "voiceover" -> project.assets.any { it.kind == AssetKind.AUDIO }
                "character", "characters" -> project.characters.isNotEmpty() || project.assets.any { it.category == AssetCategory.CHARACTER }
                "background", "backdrop" -> project.assets.any { it.category == AssetCategory.BACKGROUND }
                "prop", "sprite", "image" -> project.assets.any { it.kind == AssetKind.IMAGE }
                else -> project.assets.any { it.kind == AssetKind.IMAGE }
            }
            if (!hasMatch) {
                missingAssets.add("slot:$slot")
                questions.add("Please provide or import a '$slot' asset (or upload via import_sprite_base64) before building this scene.")
            }
        }

        if (args["width"]?.jsonPrimitive?.intOrNull == null || args["height"]?.jsonPrimitive?.intOrNull == null) {
            missingDetails.add("resolution")
            questions.add("What video resolution or aspect ratio would you like (e.g., 16:9 1280x720, 9:16 1080x1920, or 1:1 1080x1080)?")
        }
        if (args["durationFrames"]?.jsonPrimitive?.intOrNull == null) {
            missingDetails.add("durationFrames")
            questions.add("How long should the video or scene be (in seconds or frames)?")
        }
        val needsAudio = args["needsAudio"]?.jsonPrimitive?.booleanOrNull == true
        if (needsAudio && project.assets.none { it.kind == AssetKind.AUDIO }) {
            missingAssets.add("audio")
            questions.add("No audio track is loaded in the project yet. Please import an audio file in Motion Studio first.")
        }
        val needsLyrics = args["needsLyrics"]?.jsonPrimitive?.booleanOrNull == true
        val lyricsText = args["lyricsText"]?.jsonPrimitive?.contentOrNull?.trim()
        if (needsLyrics && lyricsText.isNullOrEmpty()) {
            missingDetails.add("lyricsText")
            questions.add("Please share the exact lyrics text you want animated—I will not invent lyrics.")
        }

        val ready = missingAssets.isEmpty() && missingDetails.isEmpty()
        val out = buildJsonObject {
            put("ready", ready)
            put("policy", "Ask the user for every missing asset or detail before calling creation tools. Never invent placeholders.")
            put("missingAssets", buildJsonArray { missingAssets.forEach { add(JsonPrimitive(it)) } })
            put("missingDetails", buildJsonArray { missingDetails.forEach { add(JsonPrimitive(it)) } })
            put("questionsToAskUser", buildJsonArray { questions.distinct().forEach { add(JsonPrimitive(it)) } })
            put("availableAssets", buildAssetsJson(project, "all"))
        }
        return LocalToolOutcome(out.toString())
    }

    private fun buildAssetsJson(project: MotionProject, kindFilter: String): JsonObject {
        val wantImages = kindFilter.equals("all", true) || kindFilter.equals("image", true)
        val wantAudio = kindFilter.equals("all", true) || kindFilter.equals("audio", true)
        return buildJsonObject {
            put("images", buildJsonArray {
                if (wantImages) {
                    project.assets.filter { it.kind == AssetKind.IMAGE }.forEach { a ->
                        add(buildJsonObject {
                            put("id", a.id)
                            put("name", a.name)
                            put("category", a.category.name.lowercase())
                            put("width", a.width ?: 0)
                            put("height", a.height ?: 0)
                            put("hasBackgroundRemoved", a.originalDataUrl != null)
                        })
                    }
                }
            })
            put("audio", buildJsonArray {
                if (wantAudio) {
                    project.assets.filter { it.kind == AssetKind.AUDIO }.forEach { a ->
                        add(buildJsonObject {
                            put("id", a.id)
                            put("name", a.name)
                            put("durationSec", a.durationSec ?: 0.0)
                        })
                    }
                }
            })
            put("characters", buildJsonArray {
                project.characters.forEach { ch ->
                    add(buildJsonObject {
                        put("id", ch.id)
                        put("name", ch.name)
                        put("assetId", ch.assetId)
                        put("clips", buildJsonArray { ch.clips.forEach { add(JsonPrimitive(it.id)) } })
                    })
                }
            })
        }
    }

    private fun buildEffectsCatalogJson(
        catStr: String?,
        phase: Int?,
        profileStr: String?,
        query: String?,
    ): JsonObject {
        val category = catStr?.let { s -> EffectCategory.entries.find { it.name.equals(s, true) } }
        val profile = profileStr?.let { s -> DevicePerformanceProfile.entries.find { it.name.equals(s, true) } }
        val q = query?.trim()?.lowercase().orEmpty()
        val modules = EffectsLibraryRegistry.builtinModules.filter { m ->
            (category == null || m.category == category) &&
                (phase == null || m.phase == phase) &&
                (profile == null || profile in m.performance.supportedProfiles()) &&
                (q.isEmpty() || m.name.lowercase().contains(q) || m.id.lowercase().contains(q))
        }
        return buildJsonObject {
            put("count", modules.size)
            put("effects", buildJsonArray {
                modules.forEach { m ->
                    add(buildJsonObject {
                        put("id", m.id)
                        put("name", m.name)
                        put("category", m.category.name.lowercase())
                        put("phase", m.phase)
                        put("level", m.performance.level.name.lowercase())
                        put("cost", m.performance.cost)
                        put("supportsLowEnd", m.performance.supportsLowEnd)
                    })
                }
            })
            put("combos", buildJsonArray {
                EffectsLibraryRegistry.allCombos().forEach { c ->
                    add(buildJsonObject {
                        put("id", c.id)
                        put("name", c.name)
                        put("description", c.description)
                    })
                }
            })
        }
    }

    private fun parseInterpolation(raw: String?): InterpolationType = when (raw?.lowercase()) {
        "linear" -> InterpolationType.LINEAR
        "hold" -> InterpolationType.HOLD
        "easein", "ease_in" -> InterpolationType.EASE_IN
        "easeout", "ease_out" -> InterpolationType.EASE_OUT
        else -> InterpolationType.EASE_IN_OUT
    }

    private fun missingParamError(message: String): LocalToolOutcome = LocalToolOutcome(
        outputJson = buildJsonObject {
            put("error", message)
            put("actionRequired", "Ask the user for the missing required parameter before retrying.")
        }.toString(),
        isError = true,
    )

    private fun missingAssetError(message: String, project: MotionProject): LocalToolOutcome = LocalToolOutcome(
        outputJson = buildJsonObject {
            put("error", message)
            put("actionRequired", "Ask the user to supply/import the missing asset or choose one from availableAssets.")
            put("availableAssets", buildAssetsJson(project, "all"))
        }.toString(),
        isError = true,
    )
}

object RemoteMcpClient {
    private var rpcId = 1

    suspend fun connect(url: String, bearerToken: String): Result<ExternalMcpServer> = withContext(Dispatchers.IO) {
        runCatching {
            val (initJson, sessionId) = postRpc(
                url = url,
                method = "initialize",
                params = buildJsonObject {
                    put("protocolVersion", "2025-03-26")
                    put("capabilities", buildJsonObject {})
                    put("clientInfo", buildJsonObject {
                        put("name", "motion-studio-android")
                        put("version", "1.0.0")
                    })
                },
                sessionId = null,
                bearerToken = bearerToken,
            )

            runCatching {
                postRpc(url, "notifications/initialized", buildJsonObject {}, sessionId, bearerToken, isNotification = true)
            }

            val tools = runCatching {
                val (res, _) = postRpc(url, "tools/list", buildJsonObject {}, sessionId, bearerToken)
                res["tools"]?.jsonArray?.mapNotNull { el ->
                    val o = el.jsonObject
                    val n = o["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    McpToolInfo(
                        name = n,
                        title = o["title"]?.jsonPrimitive?.contentOrNull ?: n,
                        description = o["description"]?.jsonPrimitive?.contentOrNull ?: "",
                    )
                } ?: emptyList()
            }.getOrDefault(emptyList())

            val resources = runCatching {
                val (res, _) = postRpc(url, "resources/list", buildJsonObject {}, sessionId, bearerToken)
                res["resources"]?.jsonArray?.mapNotNull { el ->
                    val o = el.jsonObject
                    val uri = o["uri"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    McpResourceInfo(
                        uri = uri,
                        name = o["name"]?.jsonPrimitive?.contentOrNull ?: uri,
                        description = o["description"]?.jsonPrimitive?.contentOrNull ?: "",
                    )
                } ?: emptyList()
            }.getOrDefault(emptyList())

            val prompts = runCatching {
                val (res, _) = postRpc(url, "prompts/list", buildJsonObject {}, sessionId, bearerToken)
                res["prompts"]?.jsonArray?.mapNotNull { el ->
                    val o = el.jsonObject
                    val n = o["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    McpPromptInfo(n, o["description"]?.jsonPrimitive?.contentOrNull ?: "")
                } ?: emptyList()
            }.getOrDefault(emptyList())

            val srvName = initJson["serverInfo"]?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull ?: url
            ExternalMcpServer(
                id = uid("mcpsrv"),
                name = srvName,
                url = url,
                bearerToken = bearerToken,
                status = "connected",
                sessionId = sessionId,
                tools = tools,
                resources = resources,
                prompts = prompts,
            )
        }
    }

    suspend fun callTool(
        server: ExternalMcpServer,
        toolName: String,
        args: JsonObject,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val (res, _) = postRpc(
                url = server.url,
                method = "tools/call",
                params = buildJsonObject {
                    put("name", toolName)
                    put("arguments", args)
                },
                sessionId = server.sessionId,
                bearerToken = server.bearerToken,
            )
            val contentArr = res["content"] as? JsonArray
            contentArr?.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull }?.joinToString("\n")
                ?: res.toString()
        }
    }

    suspend fun readResource(
        server: ExternalMcpServer,
        uri: String,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val (res, _) = postRpc(
                url = server.url,
                method = "resources/read",
                params = buildJsonObject { put("uri", uri) },
                sessionId = server.sessionId,
                bearerToken = server.bearerToken,
            )
            val contents = res["contents"] as? JsonArray
            contents?.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull }?.joinToString("\n\n")
                ?: res.toString()
        }
    }

    private fun postRpc(
        url: String,
        method: String,
        params: JsonObject,
        sessionId: String?,
        bearerToken: String,
        isNotification: Boolean = false,
    ): Pair<JsonObject, String?> {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 15_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json, text/event-stream")
            if (!sessionId.isNullOrBlank()) setRequestProperty("Mcp-Session-Id", sessionId)
            if (bearerToken.isNotBlank()) setRequestProperty("Authorization", "Bearer ${bearerToken.trim()}")
        }

        val payload = buildJsonObject {
            put("jsonrpc", "2.0")
            if (!isNotification) put("id", rpcId++)
            put("method", method)
            put("params", params)
        }.toString()

        conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }

        val code = conn.responseCode
        val nextSession = conn.getHeaderField("Mcp-Session-Id") ?: sessionId
        if (code !in 200..299) {
            val err = conn.errorStream?.bufferedReader()?.readText() ?: conn.responseMessage
            throw IllegalStateException("HTTP $code: $err")
        }
        if (isNotification || code == 202 || code == 204) {
            return buildJsonObject {} to nextSession
        }

        val rawBody = conn.inputStream.bufferedReader().readText()
        val ctype = conn.contentType ?: ""
        val rpcJson: JsonElement = if (ctype.contains("text/event-stream", ignoreCase = true)) {
            val lastData = rawBody.lines()
                .filter { it.startsWith("data:") }
                .map { it.removePrefix("data:").trim() }
                .lastOrNull { it.startsWith("{") || it.startsWith("[") }
                ?: throw IllegalStateException("Empty SSE response")
            StudioJson.parseToJsonElement(lastData)
        } else {
            StudioJson.parseToJsonElement(rawBody)
        }

        val obj = if (rpcJson is JsonArray) rpcJson.first().jsonObject else rpcJson.jsonObject
        obj["error"]?.jsonObject?.let { err ->
            val msg = err["message"]?.jsonPrimitive?.contentOrNull ?: "MCP Error"
            throw IllegalStateException(msg)
        }
        val result = obj["result"]?.jsonObject ?: buildJsonObject {}
        return result to nextSession
    }
}
