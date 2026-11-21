package com.example.mcp

import com.example.effects.DevicePerformanceProfile
import com.example.effects.EffectsLibraryRegistry
import com.example.engine.AnimationPresetRegistry
import com.example.engine.SKY_ATMOSPHERE_PRESETS
import com.example.schema.AssetKind
import com.example.schema.CameraAnimatableProp
import com.example.schema.LayerAnimatableProp
import com.example.schema.MotionProject
import com.example.schema.SceneObjectDocument
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Operation scope validated by [McpRequestVerifier] prior to execution.
 */
enum class VerificationOperation {
    GENERAL,
    SCENE_CREATION,
    SCENE_MANIPULATION,
    EFFECT_MODIFICATION,
    EXPORT,
}

/**
 * Outcome of the mandatory pre-flight `verify` step.
 */
data class VerificationResult(
    val valid: Boolean,
    val operation: VerificationOperation,
    val missingAssets: List<String> = emptyList(),
    val missingParameters: List<String> = emptyList(),
    val invalidParameters: List<String> = emptyList(),
    val questionsToAskUser: List<String> = emptyList(),
    val verifiedSummary: JsonObject = JsonObject(emptyMap()),
) {
    fun toJson(project: MotionProject): JsonObject = buildJsonObject {
        put("verified", valid)
        put("operation", operation.name.lowercase())
        put("policy", "All asset IDs, targets, and required parameters must pass verification before creation, manipulation, effect edits, or export.")
        put("missingAssets", buildJsonArray { missingAssets.distinct().forEach { add(JsonPrimitive(it)) } })
        put("missingParameters", buildJsonArray { missingParameters.distinct().forEach { add(JsonPrimitive(it)) } })
        put("invalidParameters", buildJsonArray { invalidParameters.distinct().forEach { add(JsonPrimitive(it)) } })
        put("questionsToAskUser", buildJsonArray { questionsToAskUser.distinct().forEach { add(JsonPrimitive(it)) } })
        put("verifiedSummary", verifiedSummary)
        put("availableAssets", buildAvailableAssetsJson(project))
    }

    companion object {
        internal fun buildAvailableAssetsJson(project: MotionProject): JsonObject = buildJsonObject {
            put("images", buildJsonArray {
                project.assets.filter { it.kind == AssetKind.IMAGE }.forEach { a ->
                    add(buildJsonObject {
                        put("id", a.id)
                        put("name", a.name)
                        put("category", a.category.name.lowercase())
                    })
                }
            })
            put("audio", buildJsonArray {
                project.assets.filter { it.kind == AssetKind.AUDIO }.forEach { a ->
                    add(buildJsonObject {
                        put("id", a.id)
                        put("name", a.name)
                    })
                }
            })
            put("characters", buildJsonArray {
                project.characters.forEach { ch ->
                    add(buildJsonObject {
                        put("id", ch.id)
                        put("name", ch.name)
                    })
                }
            })
            put("compositions", buildJsonArray {
                project.compositions.forEach { c ->
                    add(buildJsonObject {
                        put("id", c.id)
                        put("name", c.name)
                    })
                }
            })
        }
    }
}

/**
 * Structured HTTP response produced by core [McpServer] request handlers.
 */
data class EndpointResponse(
    val statusCode: Int,
    val statusText: String,
    val body: JsonObject,
)

/**
 * Centralized pre-flight verifier enforcing asset existence and parameter validity
 * across scene creation, scene manipulation (transforms, keyframes, camera, environment,
 * movie timeline, layers, sprites), effect modification, and video/PNG export.
 */
object McpRequestVerifier {
    private val VALID_INTERPOLATIONS = setOf("linear", "hold", "easein", "easeout", "easeinout", "ease_in", "ease_out", "ease_in_out")
    private val VALID_TRANSITIONS = setOf("cut", "crossfade", "crossdissolve", "cross_dissolve", "fade_black", "fadeblack")
    private val VALID_ASSET_CATEGORIES = setOf("character", "prop", "background", "effect")

    val SCENE_CREATION_ACTIONS = setOf(
        "create_composition",
        "add_sprite_layer",
        "add_character_layer",
        "add_layer",
        "add_audio_clip",
        "set_lyrics_cues",
        "remove_sprite_background",
        "import_sprite_base64",
    )

    val SCENE_MANIPULATION_ACTIONS = setOf(
        "set_active_composition",
        "add_movie_clip",
        "update_environment",
        "delete_layer",
        "update_layer_transform",
        "set_keyframe",
        "clear_layer_animation",
        "set_camera_keyframe",
        "undo_redo",
        "evaluate_frame",
    )

    val EFFECT_MODIFICATION_ACTIONS = setOf(
        "apply_effect_module",
        "apply_effect_combo",
        "apply_motion_preset",
        "validate_effect_stack",
    )

    fun verify(
        project: MotionProject,
        activeCompId: String,
        operation: VerificationOperation,
        params: JsonObject,
    ): VerificationResult {
        val missingAssets = mutableListOf<String>()
        val missingParams = mutableListOf<String>()
        val invalidParams = mutableListOf<String>()
        val questions = mutableListOf<String>()

        verifyReferencedAssets(project, params, missingAssets, questions)

        val requestedCompId = params["compositionId"]?.jsonPrimitive?.contentOrNull?.trim()
        val resolvedComp = if (!requestedCompId.isNullOrEmpty()) {
            val found = project.compositions.find { it.id == requestedCompId }
            if (found == null) {
                invalidParams.add("compositionId:$requestedCompId")
                questions.add("Composition '$requestedCompId' does not exist. Choose an existing compositionId from availableAssets.compositions.")
            }
            found
        } else {
            project.compositions.find { it.id == activeCompId } ?: project.compositions.firstOrNull()
        }

        when (operation) {
            VerificationOperation.GENERAL -> {
                val layerId = params["layerId"]?.jsonPrimitive?.contentOrNull?.trim()
                if (!layerId.isNullOrEmpty() && resolvedComp != null && resolvedComp.layers.none { it.id == layerId }) {
                    invalidParams.add("layerId:$layerId")
                    questions.add("Layer '$layerId' was not found in composition '${resolvedComp.id}'.")
                }
                val effectId = params["effectId"]?.jsonPrimitive?.contentOrNull?.trim()
                if (!effectId.isNullOrEmpty() && EffectsLibraryRegistry.builtinModules.none { it.id == effectId }) {
                    invalidParams.add("effectId:$effectId")
                    questions.add("Effect '$effectId' is not a registered effect module. Call list_effects to see valid IDs.")
                }
            }

            VerificationOperation.SCENE_CREATION -> {
                val action = params["action"]?.jsonPrimitive?.contentOrNull?.trim()?.lowercase() ?: "create_composition"
                verifySceneCreationAction(project, resolvedComp?.id, action, params, missingAssets, missingParams, invalidParams, questions)
            }

            VerificationOperation.SCENE_MANIPULATION -> {
                val action = params["action"]?.jsonPrimitive?.contentOrNull?.trim()?.lowercase()
                if (action.isNullOrEmpty()) {
                    missingParams.add("action")
                    questions.add("Specify a scene manipulation action (${SCENE_MANIPULATION_ACTIONS.joinToString()}).")
                } else {
                    verifySceneManipulationAction(project, resolvedComp?.id, action, params, missingParams, invalidParams, questions)
                }
            }

            VerificationOperation.EFFECT_MODIFICATION -> {
                verifyEffectModificationAction(project, resolvedComp?.id ?: activeCompId, params, missingParams, invalidParams, questions)
            }

            VerificationOperation.EXPORT -> {
                verifyExportAction(project, resolvedComp?.id, params, missingAssets, missingParams, invalidParams, questions)
            }
        }

        val isValid = missingAssets.isEmpty() && missingParams.isEmpty() && invalidParams.isEmpty()
        val summary = buildJsonObject {
            put("compositionId", resolvedComp?.id ?: "")
            put("imageAssetCount", project.assets.count { it.kind == AssetKind.IMAGE })
            put("audioAssetCount", project.assets.count { it.kind == AssetKind.AUDIO })
            put("characterCount", project.characters.size)
        }
        return VerificationResult(
            valid = isValid,
            operation = operation,
            missingAssets = missingAssets,
            missingParameters = missingParams,
            invalidParameters = invalidParams,
            questionsToAskUser = questions,
            verifiedSummary = summary,
        )
    }

    fun verifyToolCall(
        project: MotionProject,
        activeCompId: String,
        toolName: String,
        args: JsonObject,
    ): VerificationResult {
        val withAction = { actionName: String -> JsonObject(args + ("action" to JsonPrimitive(actionName))) }
        return when (toolName) {
            in SCENE_CREATION_ACTIONS ->
                verify(project, activeCompId, VerificationOperation.SCENE_CREATION, withAction(toolName))

            in SCENE_MANIPULATION_ACTIONS -> {
                val enriched = if (toolName == "undo_redo") {
                    val rawAct = args["action"]?.jsonPrimitive?.contentOrNull
                    JsonObject(args + ("action" to JsonPrimitive("undo_redo")) + ("historyAction" to JsonPrimitive(rawAct ?: "")))
                } else {
                    withAction(toolName)
                }
                verify(project, activeCompId, VerificationOperation.SCENE_MANIPULATION, enriched)
            }

            in EFFECT_MODIFICATION_ACTIONS ->
                verify(project, activeCompId, VerificationOperation.EFFECT_MODIFICATION, withAction(toolName))

            "render_video" ->
                verify(project, activeCompId, VerificationOperation.EXPORT, args)

            "render_frame_png" ->
                verify(project, activeCompId, VerificationOperation.EXPORT, JsonObject(args + ("format" to JsonPrimitive("png"))))

            else ->
                verify(project, activeCompId, VerificationOperation.GENERAL, args)
        }
    }

    private fun verifySceneCreationAction(
        project: MotionProject,
        resolvedCompId: String?,
        action: String,
        params: JsonObject,
        missingAssets: MutableList<String>,
        missingParams: MutableList<String>,
        invalidParams: MutableList<String>,
        questions: MutableList<String>,
    ) {
        val resolvedComp = project.compositions.find { it.id == resolvedCompId }
        when (action) {
            "create_composition" -> {
                val name = params["name"]?.jsonPrimitive?.contentOrNull?.trim()
                val width = params["width"]?.jsonPrimitive?.intOrNull
                val height = params["height"]?.jsonPrimitive?.intOrNull
                val dur = params["durationFrames"]?.jsonPrimitive?.intOrNull
                if (name.isNullOrEmpty()) {
                    missingParams.add("name")
                    questions.add("What name should be used for the new scene/composition?")
                }
                if (width == null) missingParams.add("width") else if (width !in 160..1920) invalidParams.add("width:$width (must be 160..1920)")
                if (height == null) missingParams.add("height") else if (height !in 160..1920) invalidParams.add("height:$height (must be 160..1920)")
                if (width == null || height == null) {
                    questions.add("What canvas resolution/aspect ratio should the video use (e.g. 1280x720, 1080x1920, 1080x1080)?")
                }
                if (dur == null) {
                    missingParams.add("durationFrames")
                    questions.add("How many frames (or seconds) long should this scene be?")
                } else if (dur !in 24..3600) {
                    invalidParams.add("durationFrames:$dur (must be 24..3600)")
                }
                val envPreset = params["environmentPreset"]?.jsonPrimitive?.contentOrNull?.trim()
                if (!envPreset.isNullOrEmpty() && SKY_ATMOSPHERE_PRESETS.none { it.id == envPreset }) {
                    invalidParams.add("environmentPreset:$envPreset")
                }
            }

            "add_sprite_layer" -> {
                val aid = params["assetId"]?.jsonPrimitive?.contentOrNull?.trim()
                if (aid.isNullOrEmpty()) {
                    missingParams.add("assetId")
                    questions.add("Which verified image assetId should be placed as a sprite? Provide an existing ID or import an image first.")
                } else if (project.assets.none { it.id == aid && it.kind == AssetKind.IMAGE }) {
                    missingAssets.add("imageAsset:$aid")
                    questions.add("Image asset '$aid' does not exist in the project. Import the sprite or choose from availableAssets.images.")
                }
            }

            "add_character_layer" -> {
                val cid = params["characterId"]?.jsonPrimitive?.contentOrNull?.trim()
                if (cid.isNullOrEmpty()) {
                    missingParams.add("characterId")
                    questions.add("Which verified characterId should be added to the scene?")
                } else {
                    val ch = project.characters.find { it.id == cid }
                    if (ch == null) {
                        missingAssets.add("character:$cid")
                        questions.add("Character '$cid' does not exist. Import a character sprite sheet or pick from availableAssets.characters.")
                    } else {
                        val clipId = params["clipId"]?.jsonPrimitive?.contentOrNull?.trim()
                        if (!clipId.isNullOrEmpty() && ch.clips.none { it.id == clipId }) {
                            invalidParams.add("clipId:$clipId")
                        }
                    }
                }
            }

            "add_layer" -> {
                val name = params["name"]?.jsonPrimitive?.contentOrNull?.trim()
                val kind = params["kind"]?.jsonPrimitive?.contentOrNull?.trim()?.lowercase()
                if (name.isNullOrEmpty()) missingParams.add("name")
                if (kind.isNullOrEmpty()) missingParams.add("kind")
                else if (kind !in setOf("text", "shape")) invalidParams.add("kind:$kind (use 'text' or 'shape')")
                if (kind == "text") {
                    val txt = params["text"]?.jsonPrimitive?.contentOrNull?.trim()
                    if (txt.isNullOrEmpty()) {
                        missingParams.add("text")
                        questions.add("Please provide the exact text content for this text layer—do not invent placeholder copy.")
                    }
                }
                if (kind == "shape") {
                    val shapeType = params["shapeType"]?.jsonPrimitive?.contentOrNull?.trim()?.lowercase()
                    if (!shapeType.isNullOrEmpty() && shapeType !in setOf("rect", "ellipse", "polygon", "star")) {
                        invalidParams.add("shapeType:$shapeType")
                    }
                }
            }

            "add_audio_clip" -> {
                val aid = params["assetId"]?.jsonPrimitive?.contentOrNull?.trim()
                if (aid.isNullOrEmpty()) {
                    missingParams.add("assetId")
                    questions.add("Which verified audio assetId should be attached to the timeline?")
                } else if (project.assets.none { it.id == aid && it.kind == AssetKind.AUDIO }) {
                    missingAssets.add("audioAsset:$aid")
                    questions.add("Audio asset '$aid' does not exist in the project. Ask the user to import audio or choose from availableAssets.audio.")
                }
            }

            "set_lyrics_cues" -> {
                val layerId = params["layerId"]?.jsonPrimitive?.contentOrNull?.trim()
                val text = params["text"]?.jsonPrimitive?.contentOrNull?.trim()
                if (layerId.isNullOrEmpty()) {
                    missingParams.add("layerId")
                } else if (resolvedComp != null) {
                    val layer = resolvedComp.layers.find { it.id == layerId }
                    if (layer == null) invalidParams.add("layerId:$layerId")
                    else if (layer.objects.none { it is SceneObjectDocument.TextObject }) {
                        invalidParams.add("layerId:$layerId (not a text layer)")
                    }
                }
                if (text.isNullOrEmpty()) {
                    missingParams.add("text")
                    questions.add("Please supply the exact lyrics text—do not invent lyrics.")
                }
            }

            "remove_sprite_background" -> {
                val aid = params["assetId"]?.jsonPrimitive?.contentOrNull?.trim()
                if (aid.isNullOrEmpty()) {
                    missingParams.add("assetId")
                } else if (project.assets.none { it.id == aid && it.kind == AssetKind.IMAGE }) {
                    missingAssets.add("imageAsset:$aid")
                    questions.add("Image asset '$aid' does not exist for background removal.")
                }
                params["tolerance"]?.jsonPrimitive?.intOrNull?.let { if (it !in 0..100) invalidParams.add("tolerance:$it (0..100)") }
                params["feather"]?.jsonPrimitive?.intOrNull?.let { if (it !in 0..40) invalidParams.add("feather:$it (0..40)") }
            }

            "import_sprite_base64" -> {
                val name = params["name"]?.jsonPrimitive?.contentOrNull?.trim()
                val b64 = params["base64Data"]?.jsonPrimitive?.contentOrNull?.trim()
                if (name.isNullOrEmpty()) missingParams.add("name")
                if (b64.isNullOrEmpty()) {
                    missingParams.add("base64Data")
                    questions.add("Please supply the user's actual image file (base64Data) before importing a sprite.")
                }
                val cat = params["category"]?.jsonPrimitive?.contentOrNull?.trim()?.lowercase()
                if (!cat.isNullOrEmpty() && cat !in VALID_ASSET_CATEGORIES) {
                    invalidParams.add("category:$cat")
                }
            }

            else -> invalidParams.add("action:$action")
        }
    }

    private fun verifySceneManipulationAction(
        project: MotionProject,
        resolvedCompId: String?,
        action: String,
        params: JsonObject,
        missingParams: MutableList<String>,
        invalidParams: MutableList<String>,
        questions: MutableList<String>,
    ) {
        val resolvedComp = project.compositions.find { it.id == resolvedCompId }

        fun requireLayer(): Boolean {
            val layerId = params["layerId"]?.jsonPrimitive?.contentOrNull?.trim()
            if (layerId.isNullOrEmpty()) {
                missingParams.add("layerId")
                questions.add("Which existing layerId should be modified?")
                return false
            }
            if (resolvedComp == null || resolvedComp.layers.none { it.id == layerId }) {
                invalidParams.add("layerId:$layerId")
                questions.add("Layer '$layerId' was not found in composition '${resolvedComp?.id ?: ""}'.")
                return false
            }
            return true
        }

        when (action) {
            "set_active_composition" -> {
                val cid = params["compositionId"]?.jsonPrimitive?.contentOrNull?.trim()
                if (cid.isNullOrEmpty()) {
                    missingParams.add("compositionId")
                    questions.add("Which existing compositionId should become active?")
                }
            }

            "add_movie_clip" -> {
                val cid = params["compositionId"]?.jsonPrimitive?.contentOrNull?.trim()
                if (cid.isNullOrEmpty()) {
                    missingParams.add("compositionId")
                    questions.add("Which existing compositionId should be appended to the Movie Timeline?")
                }
                val tr = params["transition"]?.jsonPrimitive?.contentOrNull?.trim()?.lowercase()
                if (!tr.isNullOrEmpty() && tr !in VALID_TRANSITIONS) {
                    invalidParams.add("transition:$tr (use cut, crossfade, or fade_black)")
                }
                params["transitionFrames"]?.jsonPrimitive?.intOrNull?.let { tf ->
                    if (tf !in 0..48) invalidParams.add("transitionFrames:$tf (must be 0..48)")
                }
            }

            "update_environment" -> {
                val presetId = params["presetId"]?.jsonPrimitive?.contentOrNull?.trim()
                if (presetId.isNullOrEmpty()) {
                    missingParams.add("presetId")
                    questions.add("Which atmosphere presetId should be applied (twilight, dawn, aurora, storm, daylight)?")
                } else if (SKY_ATMOSPHERE_PRESETS.none { it.id == presetId }) {
                    invalidParams.add("presetId:$presetId")
                }
            }

            "delete_layer" -> {
                requireLayer()
            }

            "update_layer_transform" -> {
                requireLayer()
                params["frame"]?.jsonPrimitive?.intOrNull?.let { f ->
                    if (f < 0) invalidParams.add("frame:$f (must be >= 0)")
                }
                params["opacity"]?.jsonPrimitive?.doubleOrNull?.let { op ->
                    if (op < 0.0 || op > 1.0) invalidParams.add("opacity:$op (must be 0.0..1.0)")
                }
                val hasTransformField = listOf("x", "y", "rotationDeg", "scaleX", "scaleY", "opacity").any { params.containsKey(it) }
                if (!hasTransformField) {
                    missingParams.add("transformField")
                    questions.add("Provide at least one transform value to update (x, y, rotationDeg, scaleX, scaleY, or opacity).")
                }
            }

            "set_keyframe" -> {
                requireLayer()
                val propStr = params["property"]?.jsonPrimitive?.contentOrNull?.trim()
                if (propStr.isNullOrEmpty()) {
                    missingParams.add("property")
                } else if (LayerAnimatableProp.fromKey(propStr) == null) {
                    invalidParams.add("property:$propStr (use x, y, rotation, scaleX, scaleY, or opacity)")
                }
                val frame = params["frame"]?.jsonPrimitive?.intOrNull
                if (frame == null) missingParams.add("frame") else if (frame < 0) invalidParams.add("frame:$frame (must be >= 0)")
                val value = params["value"]?.jsonPrimitive?.doubleOrNull
                if (value == null) missingParams.add("value")
                else if (propStr == "opacity" && (value < 0.0 || value > 1.0)) {
                    invalidParams.add("value:$value (opacity must be 0.0..1.0)")
                }
                val interp = params["interpolation"]?.jsonPrimitive?.contentOrNull?.trim()?.lowercase()
                if (!interp.isNullOrEmpty() && interp !in VALID_INTERPOLATIONS) {
                    invalidParams.add("interpolation:$interp")
                }
            }

            "clear_layer_animation" -> {
                requireLayer()
                val propStr = params["property"]?.jsonPrimitive?.contentOrNull?.trim()
                if (!propStr.isNullOrEmpty() && !propStr.equals("all", ignoreCase = true) && LayerAnimatableProp.fromKey(propStr) == null) {
                    invalidParams.add("property:$propStr (use 'all' or x/y/rotation/scaleX/scaleY/opacity)")
                }
            }

            "set_camera_keyframe" -> {
                val propStr = params["property"]?.jsonPrimitive?.contentOrNull?.trim()
                if (propStr.isNullOrEmpty()) {
                    missingParams.add("property")
                } else if (CameraAnimatableProp.fromKey(propStr) == null) {
                    invalidParams.add("property:$propStr (use x, y, or zoom)")
                }
                val frame = params["frame"]?.jsonPrimitive?.intOrNull
                if (frame == null) missingParams.add("frame") else if (frame < 0) invalidParams.add("frame:$frame (must be >= 0)")
                val value = params["value"]?.jsonPrimitive?.doubleOrNull
                if (value == null) missingParams.add("value")
                else if (propStr == "zoom" && (value <= 0.05 || value > 10.0)) {
                    invalidParams.add("value:$value (camera zoom must be 0.05..10.0)")
                }
                val interp = params["interpolation"]?.jsonPrimitive?.contentOrNull?.trim()?.lowercase()
                if (!interp.isNullOrEmpty() && interp !in VALID_INTERPOLATIONS) {
                    invalidParams.add("interpolation:$interp")
                }
            }

            "undo_redo" -> {
                val direction = params["historyAction"]?.jsonPrimitive?.contentOrNull?.trim()?.lowercase()
                    ?: params["mode"]?.jsonPrimitive?.contentOrNull?.trim()?.lowercase()
                    ?: params["action"]?.jsonPrimitive?.contentOrNull?.trim()?.lowercase()?.takeIf { it == "undo" || it == "redo" }
                if (direction.isNullOrEmpty()) {
                    missingParams.add("action")
                    questions.add("Specify 'undo' or 'redo'.")
                } else if (direction !in setOf("undo", "redo")) {
                    invalidParams.add("action:$direction (must be undo or redo)")
                }
            }

            "evaluate_frame" -> {
                val frame = params["frame"]?.jsonPrimitive?.intOrNull
                if (frame == null) missingParams.add("frame")
                else if (frame < 0) invalidParams.add("frame:$frame (must be >= 0)")
            }

            else -> invalidParams.add("action:$action")
        }
    }

    private fun verifyEffectModificationAction(
        project: MotionProject,
        resolvedCompId: String,
        params: JsonObject,
        missingParams: MutableList<String>,
        invalidParams: MutableList<String>,
        questions: MutableList<String>,
    ) {
        val resolvedComp = project.compositions.find { it.id == resolvedCompId }
        val action = params["action"]?.jsonPrimitive?.contentOrNull?.trim()?.lowercase() ?: "apply_effect_module"
        val layerId = params["layerId"]?.jsonPrimitive?.contentOrNull?.trim()
        if (layerId.isNullOrEmpty()) {
            missingParams.add("layerId")
            questions.add("Which existing layerId should the effect be applied to?")
        } else if (resolvedComp == null || resolvedComp.layers.none { it.id == layerId }) {
            invalidParams.add("layerId:$layerId")
            questions.add("Layer '$layerId' was not found in composition '$resolvedCompId'.")
        }

        val profileStr = params["profile"]?.jsonPrimitive?.contentOrNull?.trim()
        if (!profileStr.isNullOrEmpty() && DevicePerformanceProfile.entries.none { it.name.equals(profileStr, ignoreCase = true) }) {
            invalidParams.add("profile:$profileStr (use low_end, mid_range, or high_end)")
        }

        when (action) {
            "apply_effect_module" -> {
                val effectId = params["effectId"]?.jsonPrimitive?.contentOrNull?.trim()
                if (effectId.isNullOrEmpty()) {
                    missingParams.add("effectId")
                    questions.add("Which effectId should be applied? Call list_effects to inspect the 61 registered effects.")
                } else if (EffectsLibraryRegistry.builtinModules.none { it.id == effectId }) {
                    invalidParams.add("effectId:$effectId")
                    questions.add("Effect '$effectId' does not exist in EffectsLibraryRegistry.")
                }
                params["intensity"]?.jsonPrimitive?.doubleOrNull?.let { v ->
                    if (v < 0.0 || v > 5.0) invalidParams.add("intensity:$v (must be 0.0..5.0)")
                }
                params["speed"]?.jsonPrimitive?.doubleOrNull?.let { v ->
                    if (v <= 0.0 || v > 5.0) invalidParams.add("speed:$v (must be >0.0..5.0)")
                }
                params["startFrame"]?.jsonPrimitive?.intOrNull?.let { f ->
                    if (f < 0) invalidParams.add("startFrame:$f (must be >= 0)")
                }
            }

            "apply_effect_combo" -> {
                val comboId = params["comboId"]?.jsonPrimitive?.contentOrNull?.trim()
                if (comboId.isNullOrEmpty()) missingParams.add("comboId")
                else if (EffectsLibraryRegistry.allCombos().none { it.id == comboId }) invalidParams.add("comboId:$comboId")
            }

            "apply_motion_preset" -> {
                val presetId = params["presetId"]?.jsonPrimitive?.contentOrNull?.trim()
                if (presetId.isNullOrEmpty()) missingParams.add("presetId")
                else if (AnimationPresetRegistry.allPresets().none { it.id == presetId }) invalidParams.add("presetId:$presetId")
            }

            "validate_effect_stack" -> Unit

            else -> invalidParams.add("action:$action")
        }
    }

    private fun verifyExportAction(
        project: MotionProject,
        resolvedCompId: String?,
        params: JsonObject,
        missingAssets: MutableList<String>,
        missingParams: MutableList<String>,
        invalidParams: MutableList<String>,
        questions: MutableList<String>,
    ) {
        val resolvedComp = project.compositions.find { it.id == resolvedCompId }
        val format = params["format"]?.jsonPrimitive?.contentOrNull?.trim()?.lowercase()
        if (format.isNullOrEmpty()) {
            missingParams.add("format")
            questions.add("Which export format do you want ('mp4', 'gif', or 'png')?")
        } else if (format !in setOf("mp4", "gif", "png")) {
            invalidParams.add("format:$format (must be mp4, gif, or png)")
        }

        val scope = params["scope"]?.jsonPrimitive?.contentOrNull?.trim()?.lowercase() ?: "active_comp"
        if (scope !in setOf("active_comp", "full_movie")) {
            invalidParams.add("scope:$scope (must be active_comp or full_movie)")
        } else if (scope == "full_movie" && project.projectTimeline.isEmpty()) {
            missingAssets.add("movieTimelineClips")
            questions.add("The Movie Timeline has no clips yet. Append at least one composition with add_movie_clip or export with scope='active_comp'.")
        }

        if (format == "gif") {
            params["gifFps"]?.jsonPrimitive?.intOrNull?.let { fps ->
                if (fps !in 6..24) invalidParams.add("gifFps:$fps (must be 6..24)")
            }
            params["gifMaxDimension"]?.jsonPrimitive?.intOrNull?.let { dim ->
                if (dim !in 240..720) invalidParams.add("gifMaxDimension:$dim (must be 240..720)")
            }
        }
        if (format == "png") {
            val frame = params["frame"]?.jsonPrimitive?.intOrNull
            if (frame == null) {
                missingParams.add("frame")
                questions.add("Which timeline frame number should be rendered to PNG?")
            } else if (frame < 0) {
                invalidParams.add("frame:$frame (must be >= 0)")
            } else if (scope == "active_comp" && resolvedComp != null && frame >= resolvedComp.settings.durationFrames) {
                invalidParams.add("frame:$frame (exceeds composition duration ${resolvedComp.settings.durationFrames})")
            }
        }
    }

    private fun verifyReferencedAssets(
        project: MotionProject,
        params: JsonObject,
        missingAssets: MutableList<String>,
        questions: MutableList<String>,
    ) {
        val reqAssets = ((params["requiredAssetIds"] as? JsonArray) ?: (params["assetIds"] as? JsonArray))
            ?.mapNotNull { it.jsonPrimitive.contentOrNull?.trim() }
            .orEmpty()
        for (aid in reqAssets) {
            if (project.assets.none { it.id == aid }) {
                missingAssets.add("asset:$aid")
                questions.add("Asset '$aid' was not found in the project. Please import it or choose an existing ID.")
            }
        }
        val reqChars = ((params["requiredCharacterIds"] as? JsonArray) ?: (params["characterIds"] as? JsonArray))
            ?.mapNotNull { it.jsonPrimitive.contentOrNull?.trim() }
            .orEmpty()
        for (cid in reqChars) {
            if (project.characters.none { it.id == cid }) {
                missingAssets.add("character:$cid")
                questions.add("Character '$cid' was not found in the project. Please import it or choose an existing character ID.")
            }
        }
        val audioId = params["audioAssetId"]?.jsonPrimitive?.contentOrNull?.trim()
        if (!audioId.isNullOrEmpty() && project.assets.none { it.id == audioId && it.kind == AssetKind.AUDIO }) {
            missingAssets.add("audioAsset:$audioId")
            questions.add("Audio asset '$audioId' was not found in the project.")
        }
    }
}
