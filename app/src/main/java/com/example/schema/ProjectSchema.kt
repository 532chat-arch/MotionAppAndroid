package com.example.schema

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

@Serializable
data class RationalFps(
    val num: Int = 24,
    val den: Int = 1,
)

fun fpsToDouble(fps: RationalFps): Double {
    val den = if (fps.den <= 0) 1 else fps.den
    val num = if (fps.num <= 0) 24 else fps.num
    return num.toDouble() / den.toDouble()
}

fun formatTimecode(frame: Int, fps: Double): String {
    val safeFps = max(1, fps.roundToInt())
    val safeFrame = max(0, frame)
    val totalSeconds = safeFrame / safeFps
    val mins = totalSeconds / 60
    val secs = totalSeconds % 60
    val remFrames = safeFrame % safeFps
    return "%02d:%02d:%02d".format(mins, secs, remFrames)
}

@Serializable
enum class AssetCategory {
    @SerialName("character") CHARACTER,
    @SerialName("prop") PROP,
    @SerialName("background") BACKGROUND,
    @SerialName("effect") EFFECT,
    @SerialName("audio") AUDIO,
    @SerialName("other") OTHER,
}

@Serializable
enum class AssetKind {
    @SerialName("image") IMAGE,
    @SerialName("audio") AUDIO,
}

@Serializable
data class AssetRecord(
    val id: String,
    val name: String,
    val kind: AssetKind,
    val category: AssetCategory = AssetCategory.PROP,
    val mimeType: String = "image/png",
    val hash: String = "",
    val dataUrl: String,
    val width: Int? = null,
    val height: Int? = null,
    val durationSec: Double? = null,
    val waveformPeaks: List<Float> = emptyList(),
    val createdAt: String = "",
    /** Preserved pre-cutout source URI/path so background removal can be restored anytime. */
    val originalDataUrl: String? = null,
)

@Serializable
data class SpriteGridConfig(
    val rows: Int = 1,
    val cols: Int = 1,
    val cellWidth: Int = 128,
    val cellHeight: Int = 128,
    val marginX: Int = 0,
    val marginY: Int = 0,
    val spacingX: Int = 0,
    val spacingY: Int = 0,
)

@Serializable
data class SpriteAnimationClip(
    val id: String,
    val name: String,
    val frameIndices: List<Int> = listOf(0),
    val fps: Double = 10.0,
    val loop: Boolean = true,
)

@Serializable
data class CharacterDefinition(
    val id: String,
    val name: String,
    val assetId: String,
    val grid: SpriteGridConfig = SpriteGridConfig(),
    val clips: List<SpriteAnimationClip> = emptyList(),
    val defaultClipId: String = "",
)

@Serializable
enum class InterpolationType {
    @SerialName("linear") LINEAR,
    @SerialName("hold") HOLD,
    @SerialName("easeIn") EASE_IN,
    @SerialName("easeOut") EASE_OUT,
    @SerialName("easeInOut") EASE_IN_OUT,
}

@Serializable
data class Keyframe(
    val id: String,
    val frame: Int,
    val value: Double,
    val interpolation: InterpolationType = InterpolationType.EASE_IN_OUT,
)

@Serializable
data class LayerKeyframes(
    val x: List<Keyframe> = emptyList(),
    val y: List<Keyframe> = emptyList(),
    val rotation: List<Keyframe> = emptyList(),
    val scaleX: List<Keyframe> = emptyList(),
    val scaleY: List<Keyframe> = emptyList(),
    val opacity: List<Keyframe> = emptyList(),
)

enum class LayerAnimatableProp(val key: String) {
    X("x"),
    Y("y"),
    ROTATION("rotation"),
    SCALE_X("scaleX"),
    SCALE_Y("scaleY"),
    OPACITY("opacity");

    companion object {
        fun fromKey(key: String): LayerAnimatableProp? = entries.find { it.key == key }
    }
}

enum class CameraAnimatableProp(val key: String) {
    X("x"),
    Y("y"),
    ZOOM("zoom");

    companion object {
        fun fromKey(key: String): CameraAnimatableProp? = entries.find { it.key == key }
    }
}

@Serializable
data class Transform2D(
    val x: Double = 0.0,
    val y: Double = 0.0,
    val scaleX: Double = 1.0,
    val scaleY: Double = 1.0,
    val rotationDeg: Double = 0.0,
    val anchorX: Double = 0.5,
    val anchorY: Double = 0.5,
)

@Serializable
enum class BlendModeType {
    @SerialName("normal") NORMAL,
    @SerialName("multiply") MULTIPLY,
    @SerialName("screen") SCREEN,
    @SerialName("overlay") OVERLAY,
    @SerialName("add") ADD,
    @SerialName("darken") DARKEN,
    @SerialName("lighten") LIGHTEN,
}

@Serializable
enum class LayerRole {
    @SerialName("foreground") FOREGROUND,
    @SerialName("character") CHARACTER,
    @SerialName("midground") MIDGROUND,
    @SerialName("prop") PROP,
    @SerialName("background") BACKGROUND,
    @SerialName("effect") EFFECT,
    @SerialName("control") CONTROL,
}

@Serializable
enum class MotionModifierDirection {
    @SerialName("forward") FORWARD,
    @SerialName("reverse") REVERSE,
    @SerialName("pingpong") PINGPONG,
}

@Serializable
enum class MotionModifierBlendMode {
    @SerialName("add") ADD,
    @SerialName("multiply") MULTIPLY,
    @SerialName("override") OVERRIDE,
}

@Serializable
enum class PivotAnchorPreset {
    @SerialName("center") CENTER,
    @SerialName("top") TOP,
    @SerialName("bottom") BOTTOM,
    @SerialName("left") LEFT,
    @SerialName("right") RIGHT,
    @SerialName("topLeft") TOP_LEFT,
    @SerialName("topRight") TOP_RIGHT,
    @SerialName("bottomLeft") BOTTOM_LEFT,
    @SerialName("bottomRight") BOTTOM_RIGHT,
}

@Serializable
data class MotionModifierInstance(
    val id: String,
    val presetId: String,
    val basePresetId: String? = null,
    val label: String,
    val enabled: Boolean = true,
    val startFrame: Int = 0,
    val durationFrames: Int = 72,
    val delayFrames: Int = 0,
    val loop: Boolean = true,
    val direction: MotionModifierDirection = MotionModifierDirection.FORWARD,
    val blendMode: MotionModifierBlendMode = MotionModifierBlendMode.ADD,
    val intensity: Double = 1.0,
    val speed: Double = 1.0,
    val phaseOffset: Double = 0.0,
    val pivotAnchor: PivotAnchorPreset = PivotAnchorPreset.CENTER,
    val params: Map<String, Double> = emptyMap(),
)

@Serializable
enum class LyricsStyle {
    @SerialName("static") STATIC,
    @SerialName("karaoke") KARAOKE,
    @SerialName("fade") FADE,
    @SerialName("slideUp") SLIDE_UP,
    @SerialName("scaleReveal") SCALE_REVEAL,
    @SerialName("typewriter") TYPEWRITER,
    @SerialName("wordReveal") WORD_REVEAL,
    @SerialName("charReveal") CHAR_REVEAL,
    @SerialName("stagger") STAGGER,
    @SerialName("beatPulse") BEAT_PULSE,
}

@Serializable
enum class LyricsAlign {
    @SerialName("left") LEFT,
    @SerialName("center") CENTER,
    @SerialName("right") RIGHT,
}

@Serializable
data class LyricsWordTiming(
    val text: String,
    val startFrame: Int,
    val endFrame: Int,
)

@Serializable
data class LyricsCue(
    val id: String,
    val startFrame: Int,
    val endFrame: Int,
    val text: String,
    val words: List<LyricsWordTiming> = emptyList(),
)

@Serializable
data class LyricsConfig(
    val enabled: Boolean = false,
    val style: LyricsStyle = LyricsStyle.KARAOKE,
    val highlightColor: String = "#F59E0B",
    val glowColor: String = "#38BDF8",
    val glowStrength: Double = 12.0,
    val shadowColor: String = "#000000",
    val shadowBlur: Double = 8.0,
    val shadowOffsetY: Double = 3.0,
    val align: LyricsAlign = LyricsAlign.CENTER,
    val maxWidth: Double = 960.0,
    val lineHeight: Double = 1.25,
    val transitionFrames: Int = 8,
    val staggerFrames: Int = 2,
    val bpm: Double = 120.0,
    val beatPulseStrength: Double = 0.08,
    val cues: List<LyricsCue> = emptyList(),
)

@Serializable
enum class ShapeType {
    @SerialName("rect") RECT,
    @SerialName("ellipse") ELLIPSE,
    @SerialName("polygon") POLYGON,
    @SerialName("star") STAR,
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("kind")
sealed interface SceneObjectDocument {
    val id: String
    val name: String
    val width: Double
    val height: Double

    @Serializable
    @SerialName("sprite")
    data class SpriteObject(
        override val id: String,
        override val name: String,
        val assetId: String,
        override val width: Double = 320.0,
        override val height: Double = 320.0,
        val flipX: Boolean = false,
        val flipY: Boolean = false,
    ) : SceneObjectDocument

    @Serializable
    @SerialName("character")
    data class CharacterObject(
        override val id: String,
        override val name: String,
        val characterId: String,
        val activeClipId: String,
        val clipStartFrame: Int = 0,
        val playbackRate: Double = 1.0,
        val flipX: Boolean = false,
        override val width: Double = 220.0,
        override val height: Double = 220.0,
    ) : SceneObjectDocument

    @Serializable
    @SerialName("shape")
    data class ShapeObject(
        override val id: String,
        override val name: String,
        val shapeType: ShapeType = ShapeType.RECT,
        override val width: Double = 200.0,
        override val height: Double = 200.0,
        val fill: String = "#3B82F6",
        val stroke: String = "#93C5FD",
        val strokeWidth: Double = 3.0,
        val cornerRadius: Double = 16.0,
    ) : SceneObjectDocument

    @Serializable
    @SerialName("text")
    data class TextObject(
        override val id: String,
        override val name: String,
        val text: String = "Title",
        val fontSize: Double = 48.0,
        val color: String = "#F8FAFC",
        val fontWeight: Int = 700,
        override val width: Double = 600.0,
        override val height: Double = 100.0,
        val lyrics: LyricsConfig? = null,
    ) : SceneObjectDocument

    @Serializable
    @SerialName("nestedComposition")
    data class NestedCompositionObject(
        override val id: String,
        override val name: String,
        val compositionId: String,
        override val width: Double = 1280.0,
        override val height: Double = 720.0,
        val timeOffsetFrames: Int = 0,
        val playbackRate: Double = 1.0,
        val loop: Boolean = true,
    ) : SceneObjectDocument
}

@Serializable
data class LayerDocument(
    val id: String,
    val name: String,
    val role: LayerRole = LayerRole.PROP,
    val visible: Boolean = true,
    val locked: Boolean = false,
    val solo: Boolean = false,
    val opacity: Double = 1.0,
    val blendMode: BlendModeType = BlendModeType.NORMAL,
    val depth: Double = 0.0,
    val parentId: String? = null,
    val inFrame: Int = 0,
    val outFrame: Int = 240,
    val transform: Transform2D = Transform2D(),
    val objects: List<SceneObjectDocument> = emptyList(),
    val keyframes: LayerKeyframes = LayerKeyframes(),
    val modifiers: List<MotionModifierInstance> = emptyList(),
)

@Serializable
data class CameraKeyframes(
    val x: List<Keyframe> = emptyList(),
    val y: List<Keyframe> = emptyList(),
    val zoom: List<Keyframe> = emptyList(),
    val rotation: List<Keyframe> = emptyList(),
)

@Serializable
data class CameraDocument(
    val x: Double = 0.0,
    val y: Double = 0.0,
    val zoom: Double = 1.0,
    val rotationDeg: Double = 0.0,
    val parallaxStrength: Double = 1.0,
    val followLayerId: String? = null,
    val keyframes: CameraKeyframes = CameraKeyframes(),
    val modifiers: List<MotionModifierInstance> = emptyList(),
)

@Serializable
enum class WeatherType {
    @SerialName("none") NONE,
    @SerialName("rain") RAIN,
    @SerialName("snow") SNOW,
    @SerialName("fireflies") FIREFLIES,
    @SerialName("dust") DUST,
    @SerialName("leaves") LEAVES,
}

@Serializable
enum class WindDirection {
    @SerialName("left") LEFT,
    @SerialName("right") RIGHT,
}

@Serializable
data class EnvironmentDocument(
    val presetId: String = "twilight",
    val skyTopColor: String = "#0B1120",
    val skyBottomColor: String = "#1E3A5F",
    val hazeColor: String = "#60A5FA",
    val hazeIntensity: Double = 0.22,
    val weatherType: WeatherType = WeatherType.NONE,
    val weatherDensity: Double = 0.4,
    val weatherSpeed: Double = 1.0,
    val weatherDirectionDeg: Double = 0.0,
    val weatherSize: Double = 1.0,
    val weatherOpacity: Double = 0.85,
    val weatherLoopFrames: Int = 240,
    val fogOpacity: Double = 0.0,
    val fogSpeed: Double = 0.6,
    val fogColor: String = "#CBD5E1",
    val cloudDensity: Double = 0.0,
    val cloudSpeed: Double = 0.5,
    val grassDensity: Double = 0.0,
    val grassColor: String = "#14532D",
    val windStrength: Double = 1.0,
    val windDirection: WindDirection = WindDirection.RIGHT,
    val timeOfDayTint: String = "#000000",
    val tintOpacity: Double = 0.0,
)

@Serializable
data class AudioClipDocument(
    val id: String,
    val assetId: String,
    val name: String,
    val startFrame: Int = 0,
    val inFrame: Int = 0,
    val durationFrames: Int = 120,
    val volume: Double = 1.0,
    val fadeInFrames: Int = 6,
    val fadeOutFrames: Int = 12,
    val loop: Boolean = false,
)

@Serializable
data class AudioTrackDocument(
    val id: String,
    val name: String,
    val muted: Boolean = false,
    val solo: Boolean = false,
    val volume: Double = 1.0,
    val clips: List<AudioClipDocument> = emptyList(),
)

@Serializable
data class TimelineMarker(
    val id: String,
    val frame: Int,
    val label: String,
    val color: String = "#F59E0B",
)

@Serializable
data class CompositionSettings(
    val width: Int = 1280,
    val height: Int = 720,
    val durationFrames: Int = 240,
    val fpsOverride: RationalFps? = null,
)

@Serializable
data class CompositionRecord(
    val id: String,
    val name: String,
    val settings: CompositionSettings = CompositionSettings(),
    val camera: CameraDocument = CameraDocument(),
    val environment: EnvironmentDocument = EnvironmentDocument(),
    val layers: List<LayerDocument> = emptyList(),
    val audioTracks: List<AudioTrackDocument> = emptyList(),
    val markers: List<TimelineMarker> = emptyList(),
)

@Serializable
enum class TransitionType {
    @SerialName("cut") CUT,
    @SerialName("crossDissolve") CROSS_DISSOLVE,
    @SerialName("fadeBlack") FADE_BLACK,
}

@Serializable
data class ProjectTimelineClip(
    val id: String,
    val compositionId: String,
    val startFrame: Int = 0,
    val durationFrames: Int = 240,
    val inOffsetFrames: Int = 0,
    val transitionIn: TransitionType = TransitionType.CUT,
    val transitionDurationFrames: Int = 0,
)

@Serializable
data class ExportSettings(
    val format: String = "mp4",
    val width: Int = 1280,
    val height: Int = 720,
    val includeAudio: Boolean = true,
    val quality: String = "high",
)

@Serializable
data class MotionProject(
    val schemaVersion: Int = 1,
    val id: String,
    val name: String,
    val createdAt: String,
    val updatedAt: String,
    val fps: RationalFps = RationalFps(24, 1),
    val defaultWidth: Int = 1280,
    val defaultHeight: Int = 720,
    val assets: List<AssetRecord> = emptyList(),
    val characters: List<CharacterDefinition> = emptyList(),
    val compositions: List<CompositionRecord> = emptyList(),
    val projectTimeline: List<ProjectTimelineClip> = emptyList(),
    val exportSettings: ExportSettings = ExportSettings(),
)

@Serializable
data class MotionXArchive(
    val format: String = "motionx-project-archive",
    val archiveVersion: Int = 1,
    val exportedAt: String = "",
    val project: MotionProject,
)

val StudioJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    prettyPrint = true
    isLenient = true
    coerceInputValues = true
}

fun parseHexColor(hex: String, fallback: Int = 0xFFFFFFFF.toInt()): Int {
    val clean = hex.trim()
    if (clean.equals("transparent", ignoreCase = true) || clean.isEmpty()) return 0x00000000
    return try {
        val stripped = clean.removePrefix("#")
        when (stripped.length) {
            3 -> {
                val r = "${stripped[0]}${stripped[0]}".toInt(16)
                val g = "${stripped[1]}${stripped[1]}".toInt(16)
                val b = "${stripped[2]}${stripped[2]}".toInt(16)
                (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
            6 -> (0xFF000000L or stripped.toLong(16)).toInt()
            8 -> {
                // Support CSS #RRGGBBAA or Android #AARRGGBB gracefully
                val v = stripped.toLong(16)
                v.toInt()
            }
            else -> fallback
        }
    } catch (_: Exception) {
        fallback
    }
}

fun getActiveCompFps(project: MotionProject, compId: String): Double {
    val comp = project.compositions.find { it.id == compId }
    return fpsToDouble(comp?.settings?.fpsOverride ?: project.fps)
}
