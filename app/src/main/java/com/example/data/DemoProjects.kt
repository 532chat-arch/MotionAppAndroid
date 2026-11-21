package com.example.data

import com.example.assets.ProceduralAudioSynth
import com.example.commands.nowIso
import com.example.commands.uid
import com.example.engine.AnimationPresetRegistry
import com.example.engine.splitWordsWithTiming
import com.example.schema.AssetCategory
import com.example.schema.AssetKind
import com.example.schema.AssetRecord
import com.example.schema.AudioClipDocument
import com.example.schema.AudioTrackDocument
import com.example.schema.BlendModeType
import com.example.schema.CameraDocument
import com.example.schema.CameraKeyframes
import com.example.schema.CharacterDefinition
import com.example.schema.CompositionRecord
import com.example.schema.CompositionSettings
import com.example.schema.EnvironmentDocument
import com.example.schema.InterpolationType
import com.example.schema.Keyframe
import com.example.schema.LayerDocument
import com.example.schema.LayerKeyframes
import com.example.schema.LayerRole
import com.example.schema.LyricsAlign
import com.example.schema.LyricsConfig
import com.example.schema.LyricsCue
import com.example.schema.LyricsStyle
import com.example.schema.MotionProject
import com.example.schema.ProjectTimelineClip
import com.example.schema.RationalFps
import com.example.schema.SceneObjectDocument
import com.example.schema.ShapeType
import com.example.schema.SpriteAnimationClip
import com.example.schema.SpriteGridConfig
import com.example.schema.Transform2D
import com.example.schema.TransitionType
import com.example.schema.WeatherType
import com.example.schema.WindDirection

data class DemoProjectInfo(
    val id: String,
    val title: String,
    val badge: String,
    val description: String,
    val builder: () -> MotionProject,
)

val DEMO_PROJECTS: List<DemoProjectInfo> = listOf(
    DemoProjectInfo(
        id = "demo_lost_kingdom",
        title = "The Lost Kingdom",
        badge = "Parallax & Sprite",
        description = "Multi-plane parallax dusk valley, 8-frame Lantern Traveler walk cycle, camera dolly, fireflies, twilight chimes, and cross-dissolve movie edit.",
        builder = ::createLostKingdomProject,
    ),
    DemoProjectInfo(
        id = "demo_sakura_wind",
        title = "Sakura Wind & Cloth",
        badge = "Hair & Cloth Presets",
        description = "Procedural hair breeze, flowing scarf wave deformation, idle breathing, slow zoom-in camera, drifting clouds, valley fog, wind grass, and sakura petals.",
        builder = ::createSakuraWindProject,
    ),
    DemoProjectInfo(
        id = "demo_midnight_lyrics",
        title = "Midnight Lyric Stage",
        badge = "Karaoke & BPM",
        description = "Synchronized word-by-word karaoke lyrics with BPM beat pulse, neon perspective horizon, ambient parallax drift, fireflies, and synth chimes.",
        builder = ::createMidnightLyricsProject,
    ),
)

fun createBlankComposition(
    name: String = "Scene 1",
    width: Int = 1280,
    height: Int = 720,
    durationFrames: Int = 240,
): CompositionRecord = CompositionRecord(
    id = uid("comp"),
    name = name,
    settings = CompositionSettings(width, height, durationFrames),
    environment = EnvironmentDocument(
        presetId = "twilight",
        skyTopColor = "#0B1120",
        skyBottomColor = "#1E3A5F",
        hazeColor = "#60A5FA",
        hazeIntensity = 0.2,
    ),
    layers = listOf(
        LayerDocument(
            id = uid("layer"),
            name = "Title",
            role = LayerRole.FOREGROUND,
            outFrame = durationFrames,
            transform = Transform2D(x = width / 2.0, y = height / 2.0),
            objects = listOf(
                SceneObjectDocument.TextObject(
                    id = uid("obj"),
                    name = "Title",
                    text = name,
                    fontSize = 54.0,
                    color = "#F8FAFC",
                )
            ),
        )
    ),
)

fun createBlankProject(
    name: String,
    width: Int = 1280,
    height: Int = 720,
    fpsNum: Int = 24,
    durationFrames: Int = 240,
): MotionProject {
    val now = nowIso()
    val comp = createBlankComposition("Scene 1", width, height, durationFrames)
    return MotionProject(
        id = uid("proj"),
        name = name.trim().ifEmpty { "Untitled Studio Project" },
        createdAt = now,
        updatedAt = now,
        fps = RationalFps(fpsNum, 1),
        defaultWidth = width,
        defaultHeight = height,
        compositions = listOf(comp),
        projectTimeline = listOf(
            ProjectTimelineClip(
                id = uid("mclip"),
                compositionId = comp.id,
                startFrame = 0,
                durationFrames = durationFrames,
            )
        ),
    )
}

fun createLostKingdomProject(): MotionProject {
    val now = nowIso()
    val chimePeaks = ProceduralAudioSynth.getWaveformPeaks("procedural://audio_twilight_chimes")

    val assets = listOf(
        AssetRecord("asset_mountains", "Distant Peaks", AssetKind.IMAGE, AssetCategory.BACKGROUND, "image/png", "proc_mtn", "procedural://mountains_far", 1600, 640, createdAt = now),
        AssetRecord("asset_forest", "Pine Ridges", AssetKind.IMAGE, AssetCategory.BACKGROUND, "image/png", "proc_for", "procedural://forest_mid", 1600, 480, createdAt = now),
        AssetRecord("asset_archway", "Ancient Rune Gate", AssetKind.IMAGE, AssetCategory.PROP, "image/png", "proc_arc", "procedural://archway_fg", 1400, 760, createdAt = now),
        AssetRecord("asset_traveler", "Lantern Traveler Sheet", AssetKind.IMAGE, AssetCategory.CHARACTER, "image/png", "proc_trv", "procedural://traveler_sheet", 512, 256, createdAt = now),
        AssetRecord("asset_chimes", "Twilight Chimes", AssetKind.AUDIO, AssetCategory.AUDIO, "audio/wav", "proc_chm", "procedural://audio_twilight_chimes", durationSec = 10.0, waveformPeaks = chimePeaks, createdAt = now),
    )

    val traveler = CharacterDefinition(
        id = "char_traveler",
        name = "Lantern Traveler",
        assetId = "asset_traveler",
        grid = SpriteGridConfig(rows = 2, cols = 4, cellWidth = 128, cellHeight = 128),
        clips = listOf(
            SpriteAnimationClip("clip_walk", "Lantern Walk", listOf(0, 1, 2, 3, 4, 5, 6, 7), fps = 10.0, loop = true),
            SpriteAnimationClip("clip_idle", "Quiet Vigil", listOf(0, 1, 0, 7), fps = 5.0, loop = true),
        ),
        defaultClipId = "clip_walk",
    )

    val comp1 = CompositionRecord(
        id = "comp_valley",
        name = "Act I · Dusk Valley",
        settings = CompositionSettings(1280, 720, 240),
        camera = CameraDocument(
            x = -60.0,
            y = 0.0,
            zoom = 1.04,
            parallaxStrength = 1.15,
            keyframes = CameraKeyframes(
                x = listOf(
                    Keyframe("ckf_x1", 0, -90.0, InterpolationType.EASE_IN_OUT),
                    Keyframe("ckf_x2", 239, 110.0, InterpolationType.EASE_IN_OUT),
                ),
                zoom = listOf(
                    Keyframe("ckf_z1", 0, 1.0, InterpolationType.EASE_IN_OUT),
                    Keyframe("ckf_z2", 239, 1.16, InterpolationType.EASE_IN_OUT),
                ),
            ),
        ),
        environment = EnvironmentDocument(
            presetId = "twilight",
            skyTopColor = "#0B1120",
            skyBottomColor = "#1E3A5F",
            hazeColor = "#60A5FA",
            hazeIntensity = 0.28,
            weatherType = WeatherType.FIREFLIES,
            weatherDensity = 0.55,
            weatherSpeed = 0.9,
            fogOpacity = 0.25,
            cloudDensity = 0.4,
            grassDensity = 0.35,
            grassColor = "#0F2922",
        ),
        layers = listOf(
            LayerDocument(
                id = "layer_mountains",
                name = "Distant Mountains",
                role = LayerRole.BACKGROUND,
                depth = -0.75,
                outFrame = 240,
                transform = Transform2D(x = 640.0, y = 390.0),
                objects = listOf(SceneObjectDocument.SpriteObject("obj_mtn", "Distant Peaks", "asset_mountains", 1600.0, 640.0)),
            ),
            LayerDocument(
                id = "layer_forest",
                name = "Midground Pines",
                role = LayerRole.MIDGROUND,
                depth = -0.32,
                outFrame = 240,
                transform = Transform2D(x = 640.0, y = 510.0),
                objects = listOf(SceneObjectDocument.SpriteObject("obj_for", "Pine Ridges", "asset_forest", 1600.0, 480.0)),
            ),
            LayerDocument(
                id = "layer_traveler",
                name = "Lantern Traveler",
                role = LayerRole.CHARACTER,
                depth = 0.0,
                outFrame = 240,
                transform = Transform2D(x = 240.0, y = 550.0, scaleX = 1.25, scaleY = 1.25),
                objects = listOf(
                    SceneObjectDocument.CharacterObject(
                        id = "obj_traveler",
                        name = "Lantern Traveler",
                        characterId = "char_traveler",
                        activeClipId = "clip_walk",
                        width = 190.0,
                        height = 190.0,
                    )
                ),
                keyframes = LayerKeyframes(
                    x = listOf(
                        Keyframe("kf_tx1", 0, 220.0, InterpolationType.EASE_IN_OUT),
                        Keyframe("kf_tx2", 120, 640.0, InterpolationType.EASE_IN_OUT),
                        Keyframe("kf_tx3", 239, 1020.0, InterpolationType.EASE_IN_OUT),
                    ),
                    y = listOf(
                        Keyframe("kf_ty1", 0, 560.0, InterpolationType.EASE_IN_OUT),
                        Keyframe("kf_ty2", 120, 540.0, InterpolationType.EASE_IN_OUT),
                        Keyframe("kf_ty3", 239, 555.0, InterpolationType.EASE_IN_OUT),
                    ),
                ),
            ),
            LayerDocument(
                id = "layer_archway",
                name = "Ancient Rune Gate",
                role = LayerRole.FOREGROUND,
                depth = 0.45,
                outFrame = 240,
                transform = Transform2D(x = 640.0, y = 370.0),
                objects = listOf(SceneObjectDocument.SpriteObject("obj_arc", "Ancient Rune Gate", "asset_archway", 1400.0, 760.0)),
            ),
            LayerDocument(
                id = "layer_title",
                name = "Chapter Title",
                role = LayerRole.FOREGROUND,
                depth = 0.15,
                outFrame = 130,
                transform = Transform2D(x = 640.0, y = 165.0),
                objects = listOf(
                    SceneObjectDocument.TextObject(
                        id = "obj_title",
                        name = "Title",
                        text = "THE LOST KINGDOM",
                        fontSize = 54.0,
                        color = "#F8FAFC",
                    )
                ),
                keyframes = LayerKeyframes(
                    opacity = listOf(
                        Keyframe("kf_to1", 0, 0.0, InterpolationType.EASE_OUT),
                        Keyframe("kf_to2", 24, 1.0, InterpolationType.HOLD),
                        Keyframe("kf_to3", 96, 1.0, InterpolationType.EASE_IN),
                        Keyframe("kf_to4", 126, 0.0, InterpolationType.LINEAR),
                    ),
                ),
            ),
        ),
        audioTracks = listOf(
            AudioTrackDocument(
                id = "atrack_ambience",
                name = "Ambience & Chimes",
                volume = 0.85,
                clips = listOf(
                    AudioClipDocument(
                        id = "aclip_chimes",
                        assetId = "asset_chimes",
                        name = "Twilight Chimes",
                        startFrame = 0,
                        durationFrames = 240,
                        loop = true,
                    )
                ),
            )
        ),
    )

    val comp2 = CompositionRecord(
        id = "comp_aurora",
        name = "Act II · Northern Shrine",
        settings = CompositionSettings(1280, 720, 180),
        camera = CameraDocument(
            zoom = 1.08,
            parallaxStrength = 1.1,
            modifiers = listOfNotNull(
                AnimationPresetRegistry.createModifierFromPreset("camera.parallax-drift", 24.0, "mod_cam_drift")
            ),
        ),
        environment = EnvironmentDocument(
            presetId = "aurora",
            skyTopColor = "#061523",
            skyBottomColor = "#134E4A",
            hazeColor = "#2DD4BF",
            hazeIntensity = 0.32,
            weatherType = WeatherType.SNOW,
            weatherDensity = 0.48,
            weatherSpeed = 0.85,
            timeOfDayTint = "#042F2E",
            tintOpacity = 0.08,
        ),
        layers = listOf(
            LayerDocument(
                id = "layer_mtn2",
                name = "Northern Ridge",
                role = LayerRole.BACKGROUND,
                depth = -0.65,
                outFrame = 180,
                transform = Transform2D(x = 640.0, y = 400.0),
                objects = listOf(SceneObjectDocument.SpriteObject("obj_mtn2", "Distant Peaks", "asset_mountains", 1600.0, 640.0)),
            ),
            LayerDocument(
                id = "layer_orb",
                name = "Shrine Beacon",
                role = LayerRole.EFFECT,
                blendMode = BlendModeType.ADD,
                depth = 0.1,
                outFrame = 180,
                transform = Transform2D(x = 640.0, y = 340.0),
                objects = listOf(
                    SceneObjectDocument.ShapeObject("obj_orb", "Beacon", ShapeType.STAR, 140.0, 140.0, "#2DD4BF", "#CCFBF1", 4.0)
                ),
                modifiers = listOfNotNull(
                    AnimationPresetRegistry.createModifierFromPreset("fx.float-hover", 24.0, "mod_orb_float"),
                    AnimationPresetRegistry.createModifierFromPreset("fx.glow-pulse", 24.0, "mod_orb_pulse"),
                ),
            ),
            LayerDocument(
                id = "layer_act2_title",
                name = "Act II Caption",
                role = LayerRole.FOREGROUND,
                outFrame = 180,
                transform = Transform2D(x = 640.0, y = 560.0),
                objects = listOf(
                    SceneObjectDocument.TextObject("obj_act2", "Act II", "Beyond the Rune Gate", 42.0, "#CCFBF1")
                ),
            ),
        ),
    )

    return MotionProject(
        id = "proj_lost_kingdom",
        name = "The Lost Kingdom",
        createdAt = now,
        updatedAt = now,
        fps = RationalFps(24, 1),
        defaultWidth = 1280,
        defaultHeight = 720,
        assets = assets,
        characters = listOf(traveler),
        compositions = listOf(comp1, comp2),
        projectTimeline = listOf(
            ProjectTimelineClip("mclip_1", comp1.id, startFrame = 0, durationFrames = 240, transitionIn = TransitionType.CUT, transitionDurationFrames = 0),
            ProjectTimelineClip("mclip_2", comp2.id, startFrame = 216, durationFrames = 180, transitionIn = TransitionType.CROSS_DISSOLVE, transitionDurationFrames = 24),
        ),
    )
}

fun createSakuraWindProject(): MotionProject {
    val now = nowIso()
    val assets = listOf(
        AssetRecord("asset_sakura_portrait", "Sakura Portrait", AssetKind.IMAGE, AssetCategory.CHARACTER, "image/png", "proc_sp", "procedural://sakura_portrait", 420, 520, createdAt = now),
        AssetRecord("asset_sakura_hair", "Flowing Hair Locks", AssetKind.IMAGE, AssetCategory.CHARACTER, "image/png", "proc_sh", "procedural://sakura_hair", 360, 420, createdAt = now),
        AssetRecord("asset_sakura_scarf", "Crimson Scarf", AssetKind.IMAGE, AssetCategory.PROP, "image/png", "proc_ss", "procedural://sakura_scarf", 440, 180, createdAt = now),
        AssetRecord("asset_sakura_bg", "Distant Peaks", AssetKind.IMAGE, AssetCategory.BACKGROUND, "image/png", "proc_mtn", "procedural://mountains_far", 1600, 640, createdAt = now),
    )

    val comp = CompositionRecord(
        id = "comp_sakura",
        name = "Sakura Portrait · Wind & Cloth",
        settings = CompositionSettings(1280, 720, 240),
        camera = CameraDocument(
            zoom = 1.0,
            parallaxStrength = 1.0,
            modifiers = listOfNotNull(
                AnimationPresetRegistry.createModifierFromPreset("camera.slow-zoom-in", 24.0, "mod_sakura_zoom")
            ),
        ),
        environment = EnvironmentDocument(
            presetId = "dawn",
            skyTopColor = "#31102F",
            skyBottomColor = "#EA580C",
            hazeColor = "#FDBA74",
            hazeIntensity = 0.3,
            weatherType = WeatherType.LEAVES,
            weatherDensity = 0.58,
            weatherSpeed = 1.15,
            weatherDirectionDeg = 28.0,
            weatherSize = 1.3,
            fogOpacity = 0.3,
            cloudDensity = 0.55,
            grassDensity = 0.65,
            grassColor = "#1F2937",
            windStrength = 1.4,
            windDirection = WindDirection.RIGHT,
        ),
        layers = listOf(
            LayerDocument(
                id = "layer_sakura_bg",
                name = "Sunset Mountains",
                role = LayerRole.BACKGROUND,
                depth = -0.6,
                outFrame = 240,
                transform = Transform2D(x = 640.0, y = 420.0),
                objects = listOf(SceneObjectDocument.SpriteObject("obj_sbg", "Sunset Peaks", "asset_sakura_bg", 1600.0, 640.0)),
            ),
            LayerDocument(
                id = "layer_sakura_hair",
                name = "Hair Strands (Wind)",
                role = LayerRole.CHARACTER,
                depth = 0.0,
                outFrame = 240,
                transform = Transform2D(x = 610.0, y = 395.0),
                objects = listOf(SceneObjectDocument.SpriteObject("obj_shair", "Flowing Hair", "asset_sakura_hair", 360.0, 420.0)),
                modifiers = listOfNotNull(
                    AnimationPresetRegistry.createModifierFromPreset("hair.soft-wind", 24.0, "mod_hair_breeze")
                ),
            ),
            LayerDocument(
                id = "layer_sakura_body",
                name = "Portrait Subject",
                role = LayerRole.CHARACTER,
                depth = 0.05,
                outFrame = 240,
                transform = Transform2D(x = 640.0, y = 445.0),
                objects = listOf(SceneObjectDocument.SpriteObject("obj_sbody", "Portrait", "asset_sakura_portrait", 420.0, 520.0)),
                modifiers = listOfNotNull(
                    AnimationPresetRegistry.createModifierFromPreset("character.idle-breathing", 24.0, "mod_idle_breath")
                ),
            ),
            LayerDocument(
                id = "layer_sakura_scarf",
                name = "Crimson Scarf (Cloth)",
                role = LayerRole.PROP,
                depth = 0.12,
                outFrame = 240,
                transform = Transform2D(x = 795.0, y = 492.0),
                objects = listOf(SceneObjectDocument.SpriteObject("obj_sscarf", "Crimson Scarf", "asset_sakura_scarf", 420.0, 170.0)),
                modifiers = listOfNotNull(
                    AnimationPresetRegistry.createModifierFromPreset("cloth.scarf-trail", 24.0, "mod_scarf_wave")
                ),
            ),
            LayerDocument(
                id = "layer_sakura_title",
                name = "Scene Caption",
                role = LayerRole.FOREGROUND,
                depth = 0.2,
                outFrame = 240,
                transform = Transform2D(x = 300.0, y = 140.0),
                objects = listOf(
                    SceneObjectDocument.TextObject("obj_stitle", "Title", "Spring Wind Study", 44.0, "#FFF7ED")
                ),
            ),
        ),
    )

    return MotionProject(
        id = "proj_sakura_wind",
        name = "Sakura Wind & Cloth",
        createdAt = now,
        updatedAt = now,
        fps = RationalFps(24, 1),
        defaultWidth = 1280,
        defaultHeight = 720,
        assets = assets,
        compositions = listOf(comp),
        projectTimeline = listOf(ProjectTimelineClip("mclip_sakura", comp.id, 0, 240)),
    )
}

fun createMidnightLyricsProject(): MotionProject {
    val now = nowIso()
    val padPeaks = ProceduralAudioSynth.getWaveformPeaks("procedural://audio_midnight_pad")
    val assets = listOf(
        AssetRecord("asset_stage", "Neon Horizon Grid", AssetKind.IMAGE, AssetCategory.BACKGROUND, "image/png", "proc_stg", "procedural://stage_horizon", 1400, 520, createdAt = now),
        AssetRecord("asset_pad", "Midnight Synth Pad", AssetKind.AUDIO, AssetCategory.AUDIO, "audio/wav", "proc_pad", "procedural://audio_midnight_pad", durationSec = 12.0, waveformPeaks = padPeaks, createdAt = now),
    )

    val cues = listOf(
        LyricsCue("cue_1", 12, 76, "City lights drift across the midnight sky", splitWordsWithTiming("City lights drift across the midnight sky", 12, 76)),
        LyricsCue("cue_2", 82, 148, "Every frame hums in golden stereo time", splitWordsWithTiming("Every frame hums in golden stereo time", 82, 148)),
        LyricsCue("cue_3", 154, 224, "Hold the beat until the horizon shines", splitWordsWithTiming("Hold the beat until the horizon shines", 154, 224)),
        LyricsCue("cue_4", 230, 282, "Motion Studio · Live Lyric Stage", splitWordsWithTiming("Motion Studio · Live Lyric Stage", 230, 282)),
    )

    val comp = CompositionRecord(
        id = "comp_lyric_stage",
        name = "Midnight Lyric Stage",
        settings = CompositionSettings(1280, 720, 288),
        camera = CameraDocument(
            parallaxStrength = 1.2,
            modifiers = listOfNotNull(
                AnimationPresetRegistry.createModifierFromPreset("camera.parallax-drift", 24.0, "mod_lyric_drift")
            ),
        ),
        environment = EnvironmentDocument(
            presetId = "twilight",
            skyTopColor = "#070B19",
            skyBottomColor = "#1E1B4B",
            hazeColor = "#38BDF8",
            hazeIntensity = 0.26,
            weatherType = WeatherType.FIREFLIES,
            weatherDensity = 0.62,
            weatherSpeed = 0.85,
        ),
        layers = listOf(
            LayerDocument(
                id = "layer_stage_grid",
                name = "Neon Horizon",
                role = LayerRole.BACKGROUND,
                depth = -0.45,
                outFrame = 288,
                transform = Transform2D(x = 640.0, y = 470.0),
                objects = listOf(SceneObjectDocument.SpriteObject("obj_stg", "Stage Horizon", "asset_stage", 1400.0, 520.0)),
            ),
            LayerDocument(
                id = "layer_pulse_halo",
                name = "Beat Halo Ring",
                role = LayerRole.EFFECT,
                blendMode = BlendModeType.ADD,
                depth = -0.1,
                outFrame = 288,
                transform = Transform2D(x = 640.0, y = 310.0),
                objects = listOf(
                    SceneObjectDocument.ShapeObject("obj_halo", "Halo", ShapeType.ELLIPSE, 260.0, 260.0, "#1D4ED8", "#38BDF8", 6.0)
                ),
                opacity = 0.55,
                modifiers = listOfNotNull(
                    AnimationPresetRegistry.createModifierFromPreset("fx.glow-pulse", 24.0, "mod_halo_pulse")
                ),
            ),
            LayerDocument(
                id = "layer_karaoke_lyrics",
                name = "Lead Vocals (Karaoke)",
                role = LayerRole.FOREGROUND,
                depth = 0.18,
                outFrame = 288,
                transform = Transform2D(x = 640.0, y = 360.0),
                objects = listOf(
                    SceneObjectDocument.TextObject(
                        id = "obj_lyrics",
                        name = "Timed Lyrics",
                        text = cues.first().text,
                        fontSize = 48.0,
                        color = "#E2E8F0",
                        width = 1020.0,
                        height = 140.0,
                        lyrics = LyricsConfig(
                            enabled = true,
                            style = LyricsStyle.KARAOKE,
                            highlightColor = "#F59E0B",
                            glowColor = "#38BDF8",
                            glowStrength = 16.0,
                            align = LyricsAlign.CENTER,
                            maxWidth = 1020.0,
                            bpm = 120.0,
                            beatPulseStrength = 0.08,
                            cues = cues,
                        ),
                    )
                ),
            ),
        ),
        audioTracks = listOf(
            AudioTrackDocument(
                id = "atrack_synth",
                name = "Midnight Synth Pad",
                volume = 0.9,
                clips = listOf(
                    AudioClipDocument("aclip_pad", "asset_pad", "Midnight Synth Pad", 0, 0, 288, loop = true)
                ),
            )
        ),
    )

    return MotionProject(
        id = "proj_midnight_lyrics",
        name = "Midnight Lyric Stage",
        createdAt = now,
        updatedAt = now,
        fps = RationalFps(24, 1),
        defaultWidth = 1280,
        defaultHeight = 720,
        assets = assets,
        compositions = listOf(comp),
        projectTimeline = listOf(ProjectTimelineClip("mclip_lyrics", comp.id, 0, 288)),
    )
}
