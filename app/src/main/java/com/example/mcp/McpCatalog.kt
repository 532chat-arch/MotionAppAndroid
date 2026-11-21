package com.example.mcp

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Central catalog of MCP server instructions, typed JSON-Schema tool definitions,
 * and Ask-First video-creation prompts for Motion Studio Pro.
 */
object McpCatalog {

    /**
     * Mandatory server-level instructions delivered during the MCP `initialize` handshake.
     * Enforces strict asset verification and prevents AI hosts (Antigravity, Cursor, etc.)
     * from inventing missing sprites, audio, scripts, lyrics, or video settings.
     */
    val SERVER_INSTRUCTIONS: String = """
        You are connected to Motion Studio Pro (Android 2D Animation & Video Studio).

        MANDATORY ASK-FIRST & NO-INVENTION RULES:
        1. ALWAYS inspect the real project first by calling `get_project_summary` and `list_assets` (or reading `motion://assets/list`) before creating or modifying anything.
        2. NEVER invent fake asset IDs, nonexistent character IDs, placeholder shapes for missing sprites/characters, made-up lyrics/scripts, or unconfirmed video settings.
        3. ALWAYS call `check_video_requirements` before starting a new video or scene if any required sprite, background, character sheet, audio track, lyrics text, aspect ratio/resolution, or duration has not been explicitly provided or confirmed by the user.
        4. If `check_video_requirements` returns `ready: false` or any required asset is missing from `list_assets`, STOP and ask the user to provide/import the missing assets (or supply local image files via `import_sprite_base64`) or confirm which existing project assets to use.
        5. When removing backgrounds from imported sprites, use `remove_sprite_background` on verified image `assetId`s.
        6. Respect Low-End Android tablet performance budgets by calling `validate_effect_stack` or checking `motion://effects/knowledge.md` when stacking effects.
        7. After building the video, you can call `render_video` (mp4 or gif) or `render_frame_png` to export and retrieve a direct download URL.
    """.trimIndent()

    private fun schema(
        properties: Map<String, JsonObject> = emptyMap(),
        required: List<String> = emptyList(),
    ): JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            for ((k, v) in properties) put(k, v)
        })
        if (required.isNotEmpty()) {
            put("required", buildJsonArray { required.forEach { add(JsonPrimitive(it)) } })
        }
    }

    private fun strProp(desc: String, enumVals: List<String>? = null): JsonObject = buildJsonObject {
        put("type", "string")
        put("description", desc)
        if (!enumVals.isNullOrEmpty()) {
            put("enum", buildJsonArray { enumVals.forEach { add(JsonPrimitive(it)) } })
        }
    }

    private fun numProp(desc: String): JsonObject = buildJsonObject {
        put("type", "number")
        put("description", desc)
    }

    private fun intProp(desc: String): JsonObject = buildJsonObject {
        put("type", "integer")
        put("description", desc)
    }

    private fun boolProp(desc: String): JsonObject = buildJsonObject {
        put("type", "boolean")
        put("description", desc)
    }

    private fun strArrayProp(desc: String): JsonObject = buildJsonObject {
        put("type", "array")
        put("description", desc)
        put("items", buildJsonObject { put("type", "string") })
    }

    private fun objProp(desc: String): JsonObject = buildJsonObject {
        put("type", "object")
        put("description", desc)
        put("additionalProperties", true)
    }

    val tools: List<McpToolInfo> = listOf(
        // ── Discovery & Pre-Flight Ask-First Guardrails ────────────────────────
        McpToolInfo(
            name = "get_project_summary",
            title = "Get Project Summary",
            description = "Returns active project metadata, FPS, active composition, counts of compositions, layers, assets, characters, and movie clips. Call this first.",
            sampleArgsJson = "{}",
            inputSchema = schema(),
        ),
        McpToolInfo(
            name = "list_assets",
            title = "List Project Assets & Characters",
            description = "Lists every real image sprite, audio track, and character sprite sheet currently in the project with exact IDs, dimensions, categories, and cutout status. Never guess asset IDs.",
            sampleArgsJson = """{"kind": "all"}""",
            inputSchema = schema(
                properties = mapOf(
                    "kind" to strProp("Filter by asset kind.", listOf("all", "image", "audio")),
                ),
            ),
        ),
        McpToolInfo(
            name = "list_compositions",
            title = "List Compositions",
            description = "Lists all compositions with dimensions, durationFrames, layers (with IDs, names, kinds, and modifier counts), and audio tracks.",
            sampleArgsJson = "{}",
            inputSchema = schema(),
        ),
        McpToolInfo(
            name = "check_video_requirements",
            title = "Check Video Requirements (Ask-First Gate)",
            description = "Audits required asset IDs, character IDs, asset slots (e.g. background, character, audio), resolution, duration, and lyrics against the real project. Returns ready=false and concrete questions to ask the user whenever anything is missing—never invent missing items.",
            sampleArgsJson = """{"requiredAssetSlots": ["background", "character"], "needsAudio": false, "needsLyrics": false}""",
            inputSchema = schema(
                properties = mapOf(
                    "requiredAssetIds" to strArrayProp("Exact asset IDs you plan to use."),
                    "requiredCharacterIds" to strArrayProp("Exact character IDs you plan to use."),
                    "requiredAssetSlots" to strArrayProp("Asset roles needed for the video (e.g., background, character, prop, audio)."),
                    "width" to intProp("Confirmed video width in pixels (e.g. 1280 or 1080)."),
                    "height" to intProp("Confirmed video height in pixels (e.g. 720 or 1920)."),
                    "durationFrames" to intProp("Confirmed duration in frames."),
                    "needsAudio" to boolProp("Whether the user wants music/voiceover audio."),
                    "needsLyrics" to boolProp("Whether the video requires timed lyrics."),
                    "lyricsText" to strProp("Exact user-provided lyrics text if needsLyrics is true."),
                ),
            ),
        ),
        McpToolInfo(
            name = "list_effects",
            title = "List Modular Effects & Combos",
            description = "Searches the 61 built-in lightweight effects across Phases 1–5 and multi-effect combos, with cost level and low-end compatibility.",
            sampleArgsJson = """{"category": "all", "profile": "low_end"}""",
            inputSchema = schema(
                properties = mapOf(
                    "category" to strProp("Filter by category.", listOf("all", "transform", "motion", "character", "visual", "particles", "typography", "transition", "audio")),
                    "phase" to intProp("Optional phase filter (1..5)."),
                    "profile" to strProp("Optional device profile filter.", listOf("low_end", "mid_range", "high_end")),
                    "query" to strProp("Optional search substring."),
                ),
            ),
        ),
        McpToolInfo(
            name = "evaluate_frame",
            title = "Evaluate Frame State",
            description = "Runs the deterministic frame evaluator on a composition at a specific frame and returns camera state and evaluated layer world transforms.",
            sampleArgsJson = """{"frame": 24}""",
            inputSchema = schema(
                properties = mapOf(
                    "compositionId" to strProp("Optional composition ID (defaults to active composition)."),
                    "frame" to intProp("Timeline frame index to evaluate."),
                ),
                required = listOf("frame"),
            ),
        ),

        // ── Compositions & Movie Sequencing ────────────────────────────────────
        McpToolInfo(
            name = "create_composition",
            title = "Create Composition",
            description = "Creates a new composition with explicit user-confirmed name, width, height, and durationFrames, plus optional sky/weather environment preset.",
            sampleArgsJson = """{"name": "Scene 1", "width": 1280, "height": 720, "durationFrames": 180}""",
            inputSchema = schema(
                properties = mapOf(
                    "name" to strProp("Scene/composition display name."),
                    "width" to intProp("Canvas width in pixels (160..1920)."),
                    "height" to intProp("Canvas height in pixels (160..1920)."),
                    "durationFrames" to intProp("Composition length in frames (24..3600)."),
                    "environmentPreset" to strProp("Optional atmosphere preset.", listOf("twilight", "dawn", "aurora", "storm", "daylight")),
                ),
                required = listOf("name", "width", "height", "durationFrames"),
            ),
        ),
        McpToolInfo(
            name = "set_active_composition",
            title = "Switch Active Composition",
            description = "Switches the studio's active composition to an existing compositionId.",
            sampleArgsJson = """{"compositionId": "comp_main"}""",
            inputSchema = schema(
                properties = mapOf("compositionId" to strProp("Existing composition ID.")),
                required = listOf("compositionId"),
            ),
        ),
        McpToolInfo(
            name = "add_movie_clip",
            title = "Append Scene to Movie Timeline",
            description = "Appends an existing composition to the full Movie Sequencer timeline with optional transition duration.",
            sampleArgsJson = """{"compositionId": "comp_main", "transition": "crossfade", "transitionFrames": 12}""",
            inputSchema = schema(
                properties = mapOf(
                    "compositionId" to strProp("Existing composition ID to append."),
                    "transition" to strProp("Transition into this scene.", listOf("cut", "crossfade", "fade_black")),
                    "transitionFrames" to intProp("Transition overlap in frames (0..48)."),
                ),
                required = listOf("compositionId"),
            ),
        ),
        McpToolInfo(
            name = "update_environment",
            title = "Update Sky & Weather",
            description = "Applies a sky/weather atmosphere preset to a composition.",
            sampleArgsJson = """{"presetId": "aurora"}""",
            inputSchema = schema(
                properties = mapOf(
                    "compositionId" to strProp("Optional composition ID."),
                    "presetId" to strProp("Atmosphere preset ID.", listOf("twilight", "dawn", "aurora", "storm", "daylight")),
                ),
                required = listOf("presetId"),
            ),
        ),

        // ── Layers, Sprites, Characters & Background Removal ───────────────────
        McpToolInfo(
            name = "add_sprite_layer",
            title = "Add Sprite Layer (Verified Asset)",
            description = "Places a verified existing image asset onto the composition stage as a sprite layer. Strictly rejects unknown assetIds—call list_assets first or ask the user to provide an image.",
            sampleArgsJson = """{"assetId": "asset_sakura_portrait", "name": "Hero Sprite", "x": 640, "y": 360}""",
            inputSchema = schema(
                properties = mapOf(
                    "compositionId" to strProp("Optional composition ID."),
                    "assetId" to strProp("Existing image asset ID from list_assets."),
                    "name" to strProp("Optional layer display name."),
                    "role" to strProp("Layer role.", listOf("background", "character", "prop", "effect")),
                    "x" to numProp("Horizontal stage position in px."),
                    "y" to numProp("Vertical stage position in px."),
                    "width" to numProp("Optional sprite draw width in px."),
                    "height" to numProp("Optional sprite draw height in px."),
                    "depth" to numProp("Parallax depth (-1.5 to 1.5)."),
                    "flipX" to boolProp("Flip horizontally."),
                    "flipY" to boolProp("Flip vertically."),
                ),
                required = listOf("assetId"),
            ),
        ),
        McpToolInfo(
            name = "add_character_layer",
            title = "Add Character Sprite-Sheet Layer",
            description = "Places a verified existing CharacterDefinition onto the composition stage. Strictly rejects unknown characterIds—call list_assets first.",
            sampleArgsJson = """{"characterId": "char_traveler", "x": 640, "y": 460}""",
            inputSchema = schema(
                properties = mapOf(
                    "compositionId" to strProp("Optional composition ID."),
                    "characterId" to strProp("Existing character ID from list_assets."),
                    "clipId" to strProp("Optional sprite animation clip ID."),
                    "name" to strProp("Optional layer display name."),
                    "x" to numProp("Horizontal stage position in px."),
                    "y" to numProp("Vertical stage position in px."),
                    "width" to numProp("Optional character draw width in px."),
                    "height" to numProp("Optional character draw height in px."),
                    "flipX" to boolProp("Flip horizontally."),
                ),
                required = listOf("characterId"),
            ),
        ),
        McpToolInfo(
            name = "add_layer",
            title = "Add Shape or Text Layer",
            description = "Adds an explicit shape (rect, ellipse, polygon, star) or text layer. For text layers, the exact user-provided text is required—never substitute invented copy or use shapes as fake sprites.",
            sampleArgsJson = """{"name": "Title", "kind": "text", "text": "Chapter One", "x": 640, "y": 180, "fontSize": 56, "color": "#F8FAFC"}""",
            inputSchema = schema(
                properties = mapOf(
                    "compositionId" to strProp("Optional composition ID."),
                    "name" to strProp("Layer display name."),
                    "kind" to strProp("Layer object kind.", listOf("text", "shape")),
                    "text" to strProp("Required exact text content when kind is 'text'."),
                    "fontSize" to numProp("Font size in px for text layers."),
                    "color" to strProp("Hex text color (e.g. #F8FAFC)."),
                    "shapeType" to strProp("Shape geometry when kind is 'shape'.", listOf("rect", "ellipse", "polygon", "star")),
                    "x" to numProp("Horizontal stage position in px."),
                    "y" to numProp("Vertical stage position in px."),
                    "width" to numProp("Width in px."),
                    "height" to numProp("Height in px."),
                    "fill" to strProp("Hex fill color for shapes."),
                    "stroke" to strProp("Hex stroke color for shapes."),
                    "strokeWidth" to numProp("Stroke width in px."),
                    "depth" to numProp("Parallax depth (-1.5 to 1.5)."),
                ),
                required = listOf("name", "kind"),
            ),
        ),
        McpToolInfo(
            name = "import_sprite_base64",
            title = "Import User Image Sprite (Base64)",
            description = "Imports a user-provided PNG/JPG image (encoded as base64) into the Motion Studio Asset Library and returns its new assetId. Only use with real image bytes provided by the user.",
            sampleArgsJson = """{"name": "User Sprite", "category": "character", "base64Data": ""}""",
            inputSchema = schema(
                properties = mapOf(
                    "name" to strProp("Asset display name."),
                    "base64Data" to strProp("Base64-encoded PNG/JPG/WebP image data (with or without data:image/... prefix)."),
                    "category" to strProp("Asset category.", listOf("character", "prop", "background", "effect")),
                    "mimeType" to strProp("Optional MIME type (defaults to image/png)."),
                ),
                required = listOf("name", "base64Data"),
            ),
        ),
        McpToolInfo(
            name = "remove_sprite_background",
            title = "Remove Sprite Background",
            description = "Removes the background from a verified image asset using Motion Studio's offline cutout engine (smart_edge, color_key, white_bg, black_bg), preserving the original for restore/undo.",
            sampleArgsJson = """{"assetId": "asset_sakura_portrait", "mode": "smart_edge", "tolerance": 28, "feather": 12}""",
            inputSchema = schema(
                properties = mapOf(
                    "assetId" to strProp("Existing image asset ID from list_assets."),
                    "mode" to strProp("Cutout mode.", listOf("smart_edge", "color_key", "white_bg", "black_bg")),
                    "tolerance" to intProp("Color tolerance (0..100, default 28)."),
                    "feather" to intProp("Edge smoothness (0..40, default 12)."),
                    "despill" to numProp("Fringe cleanup strength (0.0..1.0, default 0.35)."),
                    "edgeShrinkPx" to intProp("Optional matte choke in pixels (0..3)."),
                    "targetColorHex" to strProp("Optional explicit backdrop hex color (e.g. #00FF00); omit for auto border detection."),
                    "saveAsCopy" to boolProp("If true, creates a new '(No BG)' asset copy instead of updating in place."),
                ),
                required = listOf("assetId"),
            ),
        ),
        McpToolInfo(
            name = "delete_layer",
            title = "Delete Layer",
            description = "Deletes an existing layer from a composition (undoable).",
            sampleArgsJson = """{"layerId": "layer_id"}""",
            inputSchema = schema(
                properties = mapOf(
                    "compositionId" to strProp("Optional composition ID."),
                    "layerId" to strProp("Existing layer ID to delete."),
                ),
                required = listOf("layerId"),
            ),
        ),
        McpToolInfo(
            name = "add_audio_clip",
            title = "Add Audio Clip (Verified Asset)",
            description = "Places a verified existing audio asset from list_assets onto the composition's audio timeline. Strictly rejects unknown assetIds.",
            sampleArgsJson = """{"assetId": "asset_chimes", "startFrame": 0}""",
            inputSchema = schema(
                properties = mapOf(
                    "compositionId" to strProp("Optional composition ID."),
                    "assetId" to strProp("Existing audio asset ID from list_assets."),
                    "startFrame" to intProp("Start frame on timeline (default 0)."),
                    "durationFrames" to intProp("Optional clip duration in frames."),
                ),
                required = listOf("assetId"),
            ),
        ),

        // ── Transform, Keyframes, Camera, Effects & Lyrics ─────────────────────
        McpToolInfo(
            name = "update_layer_transform",
            title = "Update Layer Transform",
            description = "Updates x, y, rotationDeg, scaleX, scaleY, or opacity on an existing layer at a given frame.",
            sampleArgsJson = """{"layerId": "layer_title", "frame": 0, "y": 150, "opacity": 1}""",
            inputSchema = schema(
                properties = mapOf(
                    "compositionId" to strProp("Optional composition ID."),
                    "layerId" to strProp("Existing layer ID."),
                    "frame" to intProp("Target timeline frame (default 0)."),
                    "x" to numProp("X position in px."),
                    "y" to numProp("Y position in px."),
                    "rotationDeg" to numProp("Rotation in degrees."),
                    "scaleX" to numProp("Horizontal scale factor."),
                    "scaleY" to numProp("Vertical scale factor."),
                    "opacity" to numProp("Opacity in 0.0..1.0."),
                ),
                required = listOf("layerId"),
            ),
        ),
        McpToolInfo(
            name = "set_keyframe",
            title = "Set Layer Keyframe",
            description = "Sets a keyframe on x, y, rotation, scaleX, scaleY, or opacity with optional interpolation easing.",
            sampleArgsJson = """{"layerId": "layer_traveler", "property": "y", "frame": 60, "value": 510, "interpolation": "easeInOut"}""",
            inputSchema = schema(
                properties = mapOf(
                    "compositionId" to strProp("Optional composition ID."),
                    "layerId" to strProp("Existing layer ID."),
                    "property" to strProp("Animatable property.", listOf("x", "y", "rotation", "scaleX", "scaleY", "opacity")),
                    "frame" to intProp("Timeline frame index."),
                    "value" to numProp("Property value at this frame."),
                    "interpolation" to strProp("Easing curve.", listOf("linear", "easeIn", "easeOut", "easeInOut", "hold")),
                ),
                required = listOf("layerId", "property", "frame", "value"),
            ),
        ),
        McpToolInfo(
            name = "clear_layer_animation",
            title = "Clear Layer Animation",
            description = "Clears keyframes for a specific property or clears all animation (keyframes + effects) on a layer without deleting the object.",
            sampleArgsJson = """{"layerId": "layer_traveler", "property": "all"}""",
            inputSchema = schema(
                properties = mapOf(
                    "compositionId" to strProp("Optional composition ID."),
                    "layerId" to strProp("Existing layer ID."),
                    "property" to strProp("Property to clear, or 'all' to clear every channel and effect.", listOf("all", "x", "y", "rotation", "scaleX", "scaleY", "opacity")),
                ),
                required = listOf("layerId"),
            ),
        ),
        McpToolInfo(
            name = "set_camera_keyframe",
            title = "Set Camera Keyframe",
            description = "Sets a camera keyframe on x, y, or zoom with optional easing for cinematic pans and zooms.",
            sampleArgsJson = """{"property": "zoom", "frame": 60, "value": 1.18, "interpolation": "easeInOut"}""",
            inputSchema = schema(
                properties = mapOf(
                    "compositionId" to strProp("Optional composition ID."),
                    "property" to strProp("Camera property.", listOf("x", "y", "zoom")),
                    "frame" to intProp("Timeline frame index."),
                    "value" to numProp("Camera property value."),
                    "interpolation" to strProp("Easing curve.", listOf("linear", "easeIn", "easeOut", "easeInOut", "hold")),
                ),
                required = listOf("property", "frame", "value"),
            ),
        ),
        McpToolInfo(
            name = "apply_effect_module",
            title = "Apply Modular Effect",
            description = "Attaches any of the 61 modular effects across Phases 1–5 to an existing layer with optional parameter overrides and device-profile budget validation.",
            sampleArgsJson = """{"layerId": "layer_traveler", "effectId": "fx.motion.float", "profile": "low_end"}""",
            inputSchema = schema(
                properties = mapOf(
                    "compositionId" to strProp("Optional composition ID."),
                    "layerId" to strProp("Existing layer ID."),
                    "effectId" to strProp("Effect ID from list_effects (e.g. fx.motion.bounce, fx.character.hair_sway, fx.visual.glow, fx.particles.snow, fx.text.word_reveal)."),
                    "startFrame" to intProp("Start frame (default 0)."),
                    "intensity" to numProp("Optional intensity multiplier (default 1.0)."),
                    "speed" to numProp("Optional speed multiplier (default 1.0)."),
                    "loop" to boolProp("Whether the effect loops."),
                    "params" to objProp("Optional key/value numeric parameter overrides."),
                    "profile" to strProp("Target device profile for budget check.", listOf("low_end", "mid_range", "high_end")),
                ),
                required = listOf("layerId", "effectId"),
            ),
        ),
        McpToolInfo(
            name = "apply_effect_combo",
            title = "Apply Multi-Effect Combo",
            description = "Applies a curated multi-effect combination (e.g. combo.character-entrance, combo.wind-blown-hair, combo.cinematic-lyrics, combo.horror-scene, combo.floating-environment) to an existing layer.",
            sampleArgsJson = """{"layerId": "layer_traveler", "comboId": "combo.character-entrance", "startFrame": 0}""",
            inputSchema = schema(
                properties = mapOf(
                    "compositionId" to strProp("Optional composition ID."),
                    "layerId" to strProp("Existing layer ID."),
                    "comboId" to strProp("Combo preset ID from list_effects or motion://effects/combos."),
                    "startFrame" to intProp("Start frame (default 0)."),
                ),
                required = listOf("layerId", "comboId"),
            ),
        ),
        McpToolInfo(
            name = "apply_motion_preset",
            title = "Apply Motion Preset",
            description = "Attaches a procedural motion preset modifier (e.g. fx.float-hover, character.walk-bob) to an existing layer.",
            sampleArgsJson = """{"layerId": "layer_traveler", "presetId": "character.walk-bob"}""",
            inputSchema = schema(
                properties = mapOf(
                    "compositionId" to strProp("Optional composition ID."),
                    "layerId" to strProp("Existing layer ID."),
                    "presetId" to strProp("Motion preset ID."),
                ),
                required = listOf("layerId", "presetId"),
            ),
        ),
        McpToolInfo(
            name = "validate_effect_stack",
            title = "Validate Layer Effect Stack",
            description = "Validates a layer's combined effects against a target device performance profile (low_end, mid_range, high_end) and returns budget points and fallback suggestions.",
            sampleArgsJson = """{"layerId": "layer_traveler", "profile": "low_end"}""",
            inputSchema = schema(
                properties = mapOf(
                    "compositionId" to strProp("Optional composition ID."),
                    "layerId" to strProp("Existing layer ID."),
                    "profile" to strProp("Target device profile.", listOf("low_end", "mid_range", "high_end")),
                ),
                required = listOf("layerId"),
            ),
        ),
        McpToolInfo(
            name = "set_lyrics_cues",
            title = "Set Timed Lyrics",
            description = "Parses exact user-provided lyric lines into timed cues on an existing text layer. Strictly requires non-empty user-supplied text—never invent lyrics.",
            sampleArgsJson = """{"layerId": "layer_title", "style": "karaoke", "text": ""}""",
            inputSchema = schema(
                properties = mapOf(
                    "compositionId" to strProp("Optional composition ID."),
                    "layerId" to strProp("Existing text layer ID."),
                    "text" to strProp("Required exact newline-separated lyrics supplied by the user."),
                    "style" to strProp("Lyrics visual style.", listOf("karaoke", "fade", "slide", "typewriter", "pop")),
                ),
                required = listOf("layerId", "text"),
            ),
        ),
        McpToolInfo(
            name = "undo_redo",
            title = "Undo or Redo Last Action",
            description = "Triggers Undo or Redo on the studio's command history stack.",
            sampleArgsJson = """{"action": "undo"}""",
            inputSchema = schema(
                properties = mapOf("action" to strProp("History action.", listOf("undo", "redo"))),
                required = listOf("action"),
            ),
        ),

        // ── Video & Snapshot Export ────────────────────────────────────────────
        McpToolInfo(
            name = "render_video",
            title = "Render MP4 Video or Animated GIF",
            description = "Renders the active composition or full movie timeline on-device at full quality into MP4 or GIF and returns a direct HTTP download URL served by the tablet.",
            sampleArgsJson = """{"format": "mp4", "scope": "active_comp"}""",
            inputSchema = schema(
                properties = mapOf(
                    "format" to strProp("Output format.", listOf("mp4", "gif")),
                    "scope" to strProp("Render scope.", listOf("active_comp", "full_movie")),
                    "compositionId" to strProp("Optional composition ID when scope is active_comp."),
                    "gifFps" to intProp("GIF frame rate when format is gif (6..24, default 15)."),
                    "gifMaxDimension" to intProp("GIF max dimension in px when format is gif (240..720, default 480)."),
                ),
                required = listOf("format"),
            ),
        ),
        McpToolInfo(
            name = "render_frame_png",
            title = "Render Frame PNG Preview",
            description = "Renders a single timeline frame as a full-quality PNG and returns a direct HTTP download URL so the AI or user can visually inspect the frame.",
            sampleArgsJson = """{"frame": 0, "scope": "active_comp"}""",
            inputSchema = schema(
                properties = mapOf(
                    "frame" to intProp("Timeline frame index to render."),
                    "scope" to strProp("Render scope.", listOf("active_comp", "full_movie")),
                    "compositionId" to strProp("Optional composition ID."),
                ),
                required = listOf("frame"),
            ),
        ),
    )

    val prompts: List<McpPromptInfo> = listOf(
        McpPromptInfo(
            name = "create_animated_video",
            description = "Ask-first workflow for creating a complete multi-layer animated video without inventing missing assets or details.",
        ),
        McpPromptInfo(
            name = "create_lyric_video",
            description = "Ask-first workflow for building a timed karaoke/lyric video using only user-supplied audio, lyrics, and visuals.",
        ),
        McpPromptInfo(
            name = "animate_character_sprite",
            description = "Ask-first workflow for importing/selecting a real sprite, optionally removing its background, and animating it smoothly.",
        ),
        McpPromptInfo(
            name = "low_end_effect_composer",
            description = "Compose lightweight effects across Phases 1–5 while staying strictly within Low-End Android tablet budgets.",
        ),
    )

    fun getPromptMessages(promptName: String, args: JsonObject = JsonObject(emptyMap())): JsonArray? {
        val text = when (promptName) {
            "create_animated_video" -> """
                Follow this strict Ask-First workflow to create a video in Motion Studio Pro:
                Step 1 — AUDIT: Call `get_project_summary` and `list_assets` to see what real sprites, backgrounds, characters, and audio exist.
                Step 2 — VERIFY & ASK: Call `check_video_requirements`. Never invent fake assets, placeholder shapes, scripts, or dimensions. If any sprite, background, audio, aspect ratio (e.g. 16:9 / 9:16 / 1:1), duration, or text copy is missing, ask the user for it before building.
                Step 3 — BUILD: Once confirmed, create/configure the composition (`create_composition`), place verified sprites/characters (`add_sprite_layer`, `add_character_layer`, or `import_sprite_base64` + `remove_sprite_background`), animate via keyframes/camera (`set_keyframe`, `set_camera_keyframe`), and attach budget-validated effects (`apply_effect_module`, `apply_effect_combo`).
                Step 4 — EXPORT: Verify with `render_frame_png` or export the final video with `render_video` (mp4/gif) and share the download URL.
            """.trimIndent()

            "create_lyric_video" -> """
                Follow this strict Ask-First workflow to create a lyric video in Motion Studio Pro:
                Step 1 — AUDIT: Call `list_assets` to check available audio tracks and background images.
                Step 2 — ASK FOR REQUIRED INPUTS: Do NOT invent lyrics, audio, or background art. Ask the user for: (a) the exact lyrics text, (b) the audio track (existing audio assetId or prompt them to import), (c) background visual/atmosphere preference, and (d) aspect ratio & duration.
                Step 3 — BUILD: Create the composition, attach the verified audio clip (`add_audio_clip`), add the text layer (`add_layer`), apply exact user lyrics (`set_lyrics_cues`), and enhance with typography/audio effects (`combo.cinematic-lyrics`, `fx.audio.beat_pulse`).
                Step 4 — EXPORT: Call `render_video` (`format: "mp4"`) and provide the resulting download URL.
            """.trimIndent()

            "animate_character_sprite" -> """
                Follow this strict Ask-First workflow to animate a sprite or character in Motion Studio Pro:
                Step 1 — AUDIT: Call `list_assets` to inspect real sprites and character sheets.
                Step 2 — ASK BEFORE ACTING: Never fabricate a character or draw placeholder shapes in place of a sprite. If the user hasn't picked an existing sprite or provided an image file to upload via `import_sprite_base64`, ask them first. Also ask if background removal is needed and what animation style they want.
                Step 3 — PREPARE & ANIMATE: Run `remove_sprite_background` if requested, place the sprite (`add_sprite_layer`), apply Phase 1–2 effects (`fx.character.breathing`, `fx.character.hair_sway`, `fx.motion.float`, `fx.motion.bounce`), validate with `validate_effect_stack`, and export via `render_video`.
            """.trimIndent()

            "low_end_effect_composer" -> """
                Read `motion://effects/knowledge.md` or call `list_effects(profile="low_end")`.
                Always verify layers with `list_compositions` first, ask the user before adding any unrequested elements, keep stacked effects within the target profile's budget via `validate_effect_stack`, and prefer lightweight 2D matrix/sprite effects.
            """.trimIndent()

            else -> return null
        }

        return buildJsonArray {
            add(buildJsonObject {
                put("role", "user")
                put("content", buildJsonObject {
                    put("type", "text")
                    put("text", text)
                })
            })
        }
    }
}
