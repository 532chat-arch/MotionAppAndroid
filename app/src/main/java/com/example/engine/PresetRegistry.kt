package com.example.engine

import com.example.schema.EnvironmentDocument
import com.example.schema.LyricsStyle
import com.example.schema.MotionModifierBlendMode
import com.example.schema.MotionModifierDirection
import com.example.schema.MotionModifierInstance
import com.example.schema.PivotAnchorPreset
import com.example.schema.StudioJson
import com.example.schema.WeatherType
import com.example.schema.WindDirection
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlin.math.max
import kotlin.math.roundToInt

@Serializable
enum class PresetCategory(val label: String) {
    @SerialName("hair") HAIR("Hair"),
    @SerialName("clothing") CLOTHING("Clothing"),
    @SerialName("character") CHARACTER("Character"),
    @SerialName("camera") CAMERA("Camera"),
    @SerialName("environment") ENVIRONMENT("Environment"),
    @SerialName("lyrics") LYRICS("Lyrics"),
    @SerialName("effects") EFFECTS("Effects"),
}

@Serializable
enum class PresetTargetType {
    @SerialName("sprite") SPRITE,
    @SerialName("character") CHARACTER,
    @SerialName("shape") SHAPE,
    @SerialName("text") TEXT,
    @SerialName("image") IMAGE,
    @SerialName("layer") LAYER,
    @SerialName("camera") CAMERA,
    @SerialName("environment") ENVIRONMENT,
    @SerialName("nestedComposition") NESTED_COMPOSITION,
}

@Serializable
data class PresetParameterControl(
    val key: String,
    val label: String,
    val defaultValue: Double,
    val min: Double,
    val max: Double,
    val step: Double,
)

@Serializable
data class EnvironmentPatch(
    val presetId: String? = null,
    val skyTopColor: String? = null,
    val skyBottomColor: String? = null,
    val hazeColor: String? = null,
    val hazeIntensity: Double? = null,
    val weatherType: WeatherType? = null,
    val weatherDensity: Double? = null,
    val weatherSpeed: Double? = null,
    val weatherDirectionDeg: Double? = null,
    val weatherSize: Double? = null,
    val weatherOpacity: Double? = null,
    val fogOpacity: Double? = null,
    val fogSpeed: Double? = null,
    val fogColor: String? = null,
    val cloudDensity: Double? = null,
    val cloudSpeed: Double? = null,
    val grassDensity: Double? = null,
    val grassColor: String? = null,
    val windStrength: Double? = null,
    val windDirection: WindDirection? = null,
    val timeOfDayTint: String? = null,
    val tintOpacity: Double? = null,
)

fun EnvironmentDocument.applyPatch(patch: EnvironmentPatch): EnvironmentDocument = copy(
    presetId = patch.presetId ?: presetId,
    skyTopColor = patch.skyTopColor ?: skyTopColor,
    skyBottomColor = patch.skyBottomColor ?: skyBottomColor,
    hazeColor = patch.hazeColor ?: hazeColor,
    hazeIntensity = patch.hazeIntensity ?: hazeIntensity,
    weatherType = patch.weatherType ?: weatherType,
    weatherDensity = patch.weatherDensity ?: weatherDensity,
    weatherSpeed = patch.weatherSpeed ?: weatherSpeed,
    weatherDirectionDeg = patch.weatherDirectionDeg ?: weatherDirectionDeg,
    weatherSize = patch.weatherSize ?: weatherSize,
    weatherOpacity = patch.weatherOpacity ?: weatherOpacity,
    fogOpacity = patch.fogOpacity ?: fogOpacity,
    fogSpeed = patch.fogSpeed ?: fogSpeed,
    fogColor = patch.fogColor ?: fogColor,
    cloudDensity = patch.cloudDensity ?: cloudDensity,
    cloudSpeed = patch.cloudSpeed ?: cloudSpeed,
    grassDensity = patch.grassDensity ?: grassDensity,
    grassColor = patch.grassColor ?: grassColor,
    windStrength = patch.windStrength ?: windStrength,
    windDirection = patch.windDirection ?: windDirection,
    timeOfDayTint = patch.timeOfDayTint ?: timeOfDayTint,
    tintOpacity = patch.tintOpacity ?: tintOpacity,
)

@Serializable
data class MotionPresetDefinition(
    val id: String,
    val version: String = "1.0.0",
    val name: String,
    val category: PresetCategory,
    val description: String,
    val targetType: List<PresetTargetType>,
    val duration: Double = 3.0,
    val loop: Boolean = true,
    val playbackDirection: MotionModifierDirection = MotionModifierDirection.FORWARD,
    val blendMode: MotionModifierBlendMode = MotionModifierBlendMode.ADD,
    val defaultPivot: PivotAnchorPreset = PivotAnchorPreset.CENTER,
    val defaultIntensity: Double = 1.0,
    val basePresetId: String? = null,
    val isCustom: Boolean = false,
    val parameters: Map<String, Double> = emptyMap(),
    val parameterControls: List<PresetParameterControl> = emptyList(),
    val environmentPatch: EnvironmentPatch? = null,
    val lyricsStyle: LyricsStyle? = null,
)

data class SkyAtmospherePreset(
    val id: String,
    val label: String,
    val patch: EnvironmentPatch,
)

val SKY_ATMOSPHERE_PRESETS: List<SkyAtmospherePreset> = listOf(
    SkyAtmospherePreset(
        id = "twilight",
        label = "Twilight",
        patch = EnvironmentPatch(
            presetId = "twilight",
            skyTopColor = "#0B1120",
            skyBottomColor = "#1E3A5F",
            hazeColor = "#60A5FA",
            hazeIntensity = 0.24,
            weatherType = WeatherType.FIREFLIES,
            weatherDensity = 0.45,
            tintOpacity = 0.0,
        ),
    ),
    SkyAtmospherePreset(
        id = "dawn",
        label = "Golden Dawn",
        patch = EnvironmentPatch(
            presetId = "dawn",
            skyTopColor = "#31102F",
            skyBottomColor = "#EA580C",
            hazeColor = "#FDBA74",
            hazeIntensity = 0.32,
            weatherType = WeatherType.DUST,
            weatherDensity = 0.35,
            timeOfDayTint = "#F59E0B",
            tintOpacity = 0.08,
        ),
    ),
    SkyAtmospherePreset(
        id = "aurora",
        label = "Northern Aurora",
        patch = EnvironmentPatch(
            presetId = "aurora",
            skyTopColor = "#061523",
            skyBottomColor = "#134E4A",
            hazeColor = "#2DD4BF",
            hazeIntensity = 0.28,
            weatherType = WeatherType.SNOW,
            weatherDensity = 0.4,
            timeOfDayTint = "#042F2E",
            tintOpacity = 0.08,
        ),
    ),
    SkyAtmospherePreset(
        id = "storm",
        label = "Stormy Night",
        patch = EnvironmentPatch(
            presetId = "storm",
            skyTopColor = "#090D16",
            skyBottomColor = "#1E293B",
            hazeColor = "#64748B",
            hazeIntensity = 0.36,
            weatherType = WeatherType.RAIN,
            weatherDensity = 0.65,
            timeOfDayTint = "#0F172A",
            tintOpacity = 0.14,
        ),
    ),
    SkyAtmospherePreset(
        id = "daylight",
        label = "Clear Daylight",
        patch = EnvironmentPatch(
            presetId = "daylight",
            skyTopColor = "#1D4ED8",
            skyBottomColor = "#93C5FD",
            hazeColor = "#E0F2FE",
            hazeIntensity = 0.18,
            weatherType = WeatherType.NONE,
            weatherDensity = 0.0,
            tintOpacity = 0.0,
        ),
    ),
)

private val LAYER_TARGETS = listOf(
    PresetTargetType.SPRITE,
    PresetTargetType.CHARACTER,
    PresetTargetType.SHAPE,
    PresetTargetType.TEXT,
    PresetTargetType.IMAGE,
    PresetTargetType.LAYER,
    PresetTargetType.NESTED_COMPOSITION,
)

val BUILTIN_PRESETS: List<MotionPresetDefinition> = listOf(
    // HAIR (5)
    MotionPresetDefinition(
        id = "hair.soft-wind",
        name = "Soft Hair Breeze",
        category = PresetCategory.HAIR,
        description = "Flowing multi-strand breeze pinned at the scalp with independent lock phase lag and tip lift.",
        targetType = listOf(PresetTargetType.SPRITE, PresetTargetType.IMAGE, PresetTargetType.LAYER),
        duration = 3.0,
        defaultPivot = PivotAnchorPreset.TOP,
        parameters = mapOf("waveAmplitude" to 11.0, "waveFrequency" to 1.65, "strandSeparation" to 0.72, "turbulence" to 0.36, "rotationStrength" to 2.0, "speed" to 1.0),
        parameterControls = listOf(
            PresetParameterControl("waveAmplitude", "Flow Amp (px)", 11.0, 0.0, 40.0, 1.0),
            PresetParameterControl("waveFrequency", "Wave Freq", 1.65, 0.3, 5.0, 0.1),
            PresetParameterControl("strandSeparation", "Strand Flow", 0.72, 0.0, 1.5, 0.05),
            PresetParameterControl("turbulence", "Tip Flutter", 0.36, 0.0, 1.5, 0.05),
        ),
    ),
    MotionPresetDefinition(
        id = "hair.strong-wind",
        name = "Strong Hair Gust",
        category = PresetCategory.HAIR,
        description = "Energetic gusts whipping individual hair strands downwind with organic flutter and curl.",
        targetType = listOf(PresetTargetType.SPRITE, PresetTargetType.IMAGE, PresetTargetType.LAYER),
        duration = 2.0,
        defaultPivot = PivotAnchorPreset.TOP,
        parameters = mapOf("waveAmplitude" to 24.0, "waveFrequency" to 2.35, "strandSeparation" to 0.90, "turbulence" to 0.68, "rotationStrength" to 6.0, "windDir" to 1.0, "speed" to 1.35),
        parameterControls = listOf(
            PresetParameterControl("waveAmplitude", "Gust Amp (px)", 24.0, 0.0, 60.0, 1.0),
            PresetParameterControl("waveFrequency", "Ripple Freq", 2.35, 0.4, 6.0, 0.15),
            PresetParameterControl("strandSeparation", "Strand Flow", 0.90, 0.0, 1.5, 0.05),
            PresetParameterControl("turbulence", "Tip Flutter", 0.68, 0.0, 1.5, 0.05),
            PresetParameterControl("windDir", "Wind Dir (-1/+1)", 1.0, -1.0, 1.0, 2.0),
        ),
    ),
    MotionPresetDefinition(
        id = "hair.gentle-sway",
        name = "Gentle Pendulum Sway",
        category = PresetCategory.HAIR,
        description = "Subtle scalp-anchored idle hair sway with soft secondary lag across strands.",
        targetType = listOf(PresetTargetType.SPRITE, PresetTargetType.IMAGE, PresetTargetType.LAYER),
        duration = 4.0,
        defaultPivot = PivotAnchorPreset.TOP,
        parameters = mapOf("rotationStrength" to 3.5, "waveAmplitude" to 7.5, "strandSeparation" to 0.58, "speed" to 0.9),
        parameterControls = listOf(
            PresetParameterControl("waveAmplitude", "Tip Drift (px)", 7.5, 0.0, 30.0, 1.0),
            PresetParameterControl("strandSeparation", "Strand Flow", 0.58, 0.0, 1.5, 0.05),
            PresetParameterControl("rotationStrength", "Root Sway (°)", 3.5, 0.0, 20.0, 0.5),
        ),
    ),
    MotionPresetDefinition(
        id = "hair.floating-strand",
        name = "Zero-G Floating Hair",
        category = PresetCategory.HAIR,
        description = "Dreamy weightless multi-strand drift with independent undulating curls.",
        targetType = listOf(PresetTargetType.SPRITE, PresetTargetType.IMAGE, PresetTargetType.LAYER),
        duration = 4.5,
        defaultPivot = PivotAnchorPreset.TOP,
        parameters = mapOf("waveAmplitude" to 16.0, "waveFrequency" to 1.35, "strandSeparation" to 1.0, "movementStrength" to 8.0, "speed" to 0.8),
        parameterControls = listOf(
            PresetParameterControl("waveAmplitude", "Wave Drift (px)", 16.0, 0.0, 40.0, 1.0),
            PresetParameterControl("strandSeparation", "Strand Flow", 1.0, 0.0, 1.5, 0.05),
            PresetParameterControl("movementStrength", "Lift (px)", 8.0, 0.0, 30.0, 1.0),
        ),
    ),
    MotionPresetDefinition(
        id = "hair.bounce",
        name = "Hair Stride Bounce",
        category = PresetCategory.HAIR,
        description = "Rhythmic vertical follow-through bounce with trailing strand ripple.",
        targetType = listOf(PresetTargetType.SPRITE, PresetTargetType.IMAGE, PresetTargetType.LAYER),
        duration = 1.0,
        defaultPivot = PivotAnchorPreset.TOP,
        parameters = mapOf("movementStrength" to 10.0, "scaleStrength" to 0.04, "speed" to 1.0),
        parameterControls = listOf(
            PresetParameterControl("movementStrength", "Bounce (px)", 10.0, 0.0, 35.0, 1.0),
            PresetParameterControl("scaleStrength", "Squash/Stretch", 0.04, 0.0, 0.2, 0.01),
        ),
    ),

    // CLOTHING (5)
    MotionPresetDefinition(
        id = "cloth.cape-flutter",
        name = "Cape Wind Flutter",
        category = PresetCategory.CLOTHING,
        description = "Heroic rippling wave trailing from collar anchor across cape fabric.",
        targetType = listOf(PresetTargetType.SPRITE, PresetTargetType.IMAGE, PresetTargetType.LAYER),
        duration = 2.2,
        defaultPivot = PivotAnchorPreset.TOP_LEFT,
        parameters = mapOf("waveAmplitude" to 18.0, "waveFrequency" to 2.4, "rotationStrength" to 6.0, "speed" to 1.2),
        parameterControls = listOf(
            PresetParameterControl("waveAmplitude", "Flutter Amp (px)", 18.0, 0.0, 55.0, 1.0),
            PresetParameterControl("waveFrequency", "Ripples", 2.4, 0.5, 6.0, 0.1),
            PresetParameterControl("rotationStrength", "Lift (°)", 6.0, 0.0, 25.0, 0.5),
        ),
    ),
    MotionPresetDefinition(
        id = "cloth.dress-sway",
        name = "Dress Hem Sway",
        category = PresetCategory.CLOTHING,
        description = "Graceful pendulum sway anchored at waist for flowing skirts and robes.",
        targetType = listOf(PresetTargetType.SPRITE, PresetTargetType.IMAGE, PresetTargetType.LAYER),
        duration = 3.2,
        defaultPivot = PivotAnchorPreset.TOP,
        parameters = mapOf("waveAmplitude" to 12.0, "waveFrequency" to 1.5, "rotationStrength" to 5.0, "speed" to 1.0),
        parameterControls = listOf(
            PresetParameterControl("waveAmplitude", "Hem Wave (px)", 12.0, 0.0, 40.0, 1.0),
            PresetParameterControl("rotationStrength", "Swing (°)", 5.0, 0.0, 20.0, 0.5),
        ),
    ),
    MotionPresetDefinition(
        id = "cloth.scarf-trail",
        name = "Scarf Wind Stream",
        category = PresetCategory.CLOTHING,
        description = "Traveling ribbon sine wave streaming horizontally in steady breeze.",
        targetType = listOf(PresetTargetType.SPRITE, PresetTargetType.IMAGE, PresetTargetType.LAYER),
        duration = 1.8,
        defaultPivot = PivotAnchorPreset.LEFT,
        parameters = mapOf("waveAmplitude" to 16.0, "waveFrequency" to 2.6, "rotationStrength" to 4.0, "speed" to 1.3),
        parameterControls = listOf(
            PresetParameterControl("waveAmplitude", "Ripple Amp (px)", 16.0, 0.0, 50.0, 1.0),
            PresetParameterControl("waveFrequency", "Wave Freq", 2.6, 0.5, 6.0, 0.1),
            PresetParameterControl("rotationStrength", "Whip (°)", 4.0, 0.0, 20.0, 0.5),
        ),
    ),
    MotionPresetDefinition(
        id = "cloth.sleeve-drift",
        name = "Sleeve Soft Drift",
        category = PresetCategory.CLOTHING,
        description = "Subtle secondary air draft motion for loose kimono or coat sleeves.",
        targetType = listOf(PresetTargetType.SPRITE, PresetTargetType.IMAGE, PresetTargetType.LAYER),
        duration = 3.5,
        defaultPivot = PivotAnchorPreset.TOP,
        parameters = mapOf("waveAmplitude" to 7.0, "rotationStrength" to 3.0, "speed" to 0.9),
        parameterControls = listOf(
            PresetParameterControl("waveAmplitude", "Drift (px)", 7.0, 0.0, 25.0, 1.0),
            PresetParameterControl("rotationStrength", "Sway (°)", 3.0, 0.0, 15.0, 0.5),
        ),
    ),
    MotionPresetDefinition(
        id = "cloth.flag-wave",
        name = "Banner / Flag Wave",
        category = PresetCategory.CLOTHING,
        description = "Strong periodic traveling wave pinned cleanly at the flagpole edge.",
        targetType = listOf(PresetTargetType.SPRITE, PresetTargetType.IMAGE, PresetTargetType.LAYER),
        duration = 2.0,
        defaultPivot = PivotAnchorPreset.LEFT,
        parameters = mapOf("waveAmplitude" to 24.0, "waveFrequency" to 3.0, "speed" to 1.5),
        parameterControls = listOf(
            PresetParameterControl("waveAmplitude", "Wave Amp (px)", 24.0, 0.0, 60.0, 1.0),
            PresetParameterControl("waveFrequency", "Wave Cycles", 3.0, 1.0, 8.0, 0.2),
        ),
    ),

    // CHARACTER (5)
    MotionPresetDefinition(
        id = "character.idle-breathing",
        name = "Idle Breathing",
        category = PresetCategory.CHARACTER,
        description = "Natural chest rise and subtle scale pulse anchored at character feet.",
        targetType = listOf(PresetTargetType.CHARACTER, PresetTargetType.SPRITE, PresetTargetType.LAYER),
        duration = 3.4,
        defaultPivot = PivotAnchorPreset.BOTTOM,
        parameters = mapOf("scaleStrength" to 0.028, "movementStrength" to 4.0, "speed" to 1.0),
        parameterControls = listOf(
            PresetParameterControl("scaleStrength", "Breath Scale", 0.028, 0.0, 0.12, 0.005),
            PresetParameterControl("movementStrength", "Chest Rise (px)", 4.0, 0.0, 20.0, 0.5),
        ),
    ),
    MotionPresetDefinition(
        id = "character.head-bob",
        name = "Rhythmic Head Bob",
        category = PresetCategory.CHARACTER,
        description = "Expressive beat-friendly head nod and dip for music videos.",
        targetType = listOf(PresetTargetType.CHARACTER, PresetTargetType.SPRITE, PresetTargetType.LAYER),
        duration = 1.0,
        defaultPivot = PivotAnchorPreset.BOTTOM,
        parameters = mapOf("movementStrength" to 9.0, "rotationStrength" to 4.0, "speed" to 1.0),
        parameterControls = listOf(
            PresetParameterControl("movementStrength", "Bob Depth (px)", 9.0, 0.0, 30.0, 1.0),
            PresetParameterControl("rotationStrength", "Nod (°)", 4.0, 0.0, 20.0, 0.5),
        ),
    ),
    MotionPresetDefinition(
        id = "character.subtle-sway",
        name = "Weight Shift Sway",
        category = PresetCategory.CHARACTER,
        description = "Gentle side-to-side stance shift to keep standing characters alive.",
        targetType = listOf(PresetTargetType.CHARACTER, PresetTargetType.SPRITE, PresetTargetType.LAYER),
        duration = 4.2,
        defaultPivot = PivotAnchorPreset.BOTTOM,
        parameters = mapOf("movementStrength" to 8.0, "rotationStrength" to 2.5, "speed" to 1.0),
        parameterControls = listOf(
            PresetParameterControl("movementStrength", "Shift X (px)", 8.0, 0.0, 35.0, 1.0),
            PresetParameterControl("rotationStrength", "Lean (°)", 2.5, 0.0, 12.0, 0.5),
        ),
    ),
    MotionPresetDefinition(
        id = "character.walk-bob",
        name = "Walk Cycle Bob",
        category = PresetCategory.CHARACTER,
        description = "Crisp double-bounce vertical stride + forward lean for walking sprites.",
        targetType = listOf(PresetTargetType.CHARACTER, PresetTargetType.SPRITE, PresetTargetType.LAYER),
        duration = 0.85,
        defaultPivot = PivotAnchorPreset.BOTTOM,
        parameters = mapOf("movementStrength" to 12.0, "rotationStrength" to 3.5, "speed" to 1.0),
        parameterControls = listOf(
            PresetParameterControl("movementStrength", "Step Lift (px)", 12.0, 0.0, 36.0, 1.0),
            PresetParameterControl("rotationStrength", "Stride Rock (°)", 3.5, 0.0, 15.0, 0.5),
        ),
    ),
    MotionPresetDefinition(
        id = "character.talk-pulse",
        name = "Dialogue Emphasis Pulse",
        category = PresetCategory.CHARACTER,
        description = "Subtle squash-and-stretch gesture cadence for talking or singing shots.",
        targetType = listOf(PresetTargetType.CHARACTER, PresetTargetType.SPRITE, PresetTargetType.LAYER),
        duration = 1.4,
        defaultPivot = PivotAnchorPreset.BOTTOM,
        parameters = mapOf("scaleStrength" to 0.045, "movementStrength" to 5.0, "speed" to 1.2),
        parameterControls = listOf(
            PresetParameterControl("scaleStrength", "Pulse Scale", 0.045, 0.0, 0.15, 0.005),
            PresetParameterControl("movementStrength", "Lift (px)", 5.0, 0.0, 20.0, 0.5),
        ),
    ),

    // CAMERA (7)
    MotionPresetDefinition(
        id = "camera.slow-zoom-in",
        name = "Cinematic Slow Zoom In",
        category = PresetCategory.CAMERA,
        description = "Smooth Ken Burns push-in for dramatic portraits and reveals.",
        targetType = listOf(PresetTargetType.CAMERA),
        duration = 8.0,
        loop = false,
        parameters = mapOf("zoomAmount" to 0.18, "speed" to 1.0),
        parameterControls = listOf(
            PresetParameterControl("zoomAmount", "Zoom Delta", 0.18, 0.02, 1.2, 0.02),
        ),
    ),
    MotionPresetDefinition(
        id = "camera.slow-zoom-out",
        name = "Establishing Zoom Out",
        category = PresetCategory.CAMERA,
        description = "Gradual pull-back revealing the full layered parallax world.",
        targetType = listOf(PresetTargetType.CAMERA),
        duration = 8.0,
        loop = false,
        parameters = mapOf("zoomAmount" to 0.18, "speed" to 1.0),
        parameterControls = listOf(
            PresetParameterControl("zoomAmount", "Pull-out Delta", 0.18, 0.02, 0.8, 0.02),
        ),
    ),
    MotionPresetDefinition(
        id = "camera.horizontal-pan",
        name = "Horizontal Pan",
        category = PresetCategory.CAMERA,
        description = "Steady horizontal dolly track activating multi-plane depth parallax.",
        targetType = listOf(PresetTargetType.CAMERA),
        duration = 8.0,
        loop = false,
        parameters = mapOf("panDistance" to 180.0, "speed" to 1.0),
        parameterControls = listOf(
            PresetParameterControl("panDistance", "Pan Distance (px)", 180.0, -600.0, 600.0, 10.0),
        ),
    ),
    MotionPresetDefinition(
        id = "camera.vertical-pan",
        name = "Vertical Crane Pan",
        category = PresetCategory.CAMERA,
        description = "Smooth vertical tilt/crane reveal across towering landscapes.",
        targetType = listOf(PresetTargetType.CAMERA),
        duration = 8.0,
        loop = false,
        parameters = mapOf("panDistance" to -120.0, "speed" to 1.0),
        parameterControls = listOf(
            PresetParameterControl("panDistance", "Crane Distance (px)", -120.0, -500.0, 500.0, 10.0),
        ),
    ),
    MotionPresetDefinition(
        id = "camera.shake",
        name = "Handheld / Impact Shake",
        category = PresetCategory.CAMERA,
        description = "Deterministic multi-octave organic camera shake for action or live vibe.",
        targetType = listOf(PresetTargetType.CAMERA),
        duration = 3.0,
        loop = true,
        parameters = mapOf("amplitude" to 10.0, "rotationStrength" to 0.6, "speed" to 2.2),
        parameterControls = listOf(
            PresetParameterControl("amplitude", "Shake (px)", 10.0, 1.0, 45.0, 1.0),
            PresetParameterControl("rotationStrength", "Roll (°)", 0.6, 0.0, 5.0, 0.1),
        ),
    ),
    MotionPresetDefinition(
        id = "camera.parallax-drift",
        name = "Ambient Parallax Drift",
        category = PresetCategory.CAMERA,
        description = "Figure-8 floating camera weave showcasing depth separation seamlessly.",
        targetType = listOf(PresetTargetType.CAMERA),
        duration = 6.0,
        loop = true,
        parameters = mapOf("amplitudeX" to 42.0, "amplitudeY" to 18.0, "zoomPulse" to 0.025, "speed" to 1.0),
        parameterControls = listOf(
            PresetParameterControl("amplitudeX", "Drift X (px)", 42.0, 0.0, 160.0, 2.0),
            PresetParameterControl("amplitudeY", "Drift Y (px)", 18.0, 0.0, 100.0, 2.0),
            PresetParameterControl("zoomPulse", "Breathing Zoom", 0.025, 0.0, 0.15, 0.005),
        ),
    ),
    MotionPresetDefinition(
        id = "camera.follow-object",
        name = "Smooth Target Follow",
        category = PresetCategory.CAMERA,
        description = "Tracks the composition's Follow Target Layer with configurable damping.",
        targetType = listOf(PresetTargetType.CAMERA),
        duration = 10.0,
        loop = true,
        parameters = mapOf("followStrength" to 0.65, "offsetX" to 0.0, "offsetY" to 0.0, "speed" to 1.0),
        parameterControls = listOf(
            PresetParameterControl("followStrength", "Track Weight", 0.65, 0.0, 1.0, 0.05),
            PresetParameterControl("offsetX", "Lead X (px)", 0.0, -300.0, 300.0, 10.0),
            PresetParameterControl("offsetY", "Lead Y (px)", 0.0, -200.0, 200.0, 10.0),
        ),
    ),

    // ENVIRONMENT (8)
    MotionPresetDefinition(
        id = "env.gentle-rain",
        name = "Gentle Rain",
        category = PresetCategory.ENVIRONMENT,
        description = "Slanted twilight rainfall with soft atmospheric haze.",
        targetType = listOf(PresetTargetType.ENVIRONMENT),
        environmentPatch = EnvironmentPatch(
            weatherType = WeatherType.RAIN,
            weatherDensity = 0.55,
            weatherSpeed = 1.6,
            weatherDirectionDeg = 15.0,
            weatherSize = 1.0,
            weatherOpacity = 0.78,
            hazeIntensity = 0.30,
        ),
    ),
    MotionPresetDefinition(
        id = "env.soft-snow",
        name = "Soft Snowfall",
        category = PresetCategory.ENVIRONMENT,
        description = "Drifting winter flakes floating calmly across the viewport.",
        targetType = listOf(PresetTargetType.ENVIRONMENT),
        environmentPatch = EnvironmentPatch(
            weatherType = WeatherType.SNOW,
            weatherDensity = 0.50,
            weatherSpeed = 0.8,
            weatherDirectionDeg = 10.0,
            weatherSize = 1.15,
            weatherOpacity = 0.88,
        ),
    ),
    MotionPresetDefinition(
        id = "env.firefly-meadow",
        name = "Firefly Meadow",
        category = PresetCategory.ENVIRONMENT,
        description = "Glowing golden fireflies pulsing gently over dark dusk horizons.",
        targetType = listOf(PresetTargetType.ENVIRONMENT),
        environmentPatch = EnvironmentPatch(
            weatherType = WeatherType.FIREFLIES,
            weatherDensity = 0.55,
            weatherSpeed = 0.9,
            weatherSize = 1.2,
            weatherOpacity = 0.92,
        ),
    ),
    MotionPresetDefinition(
        id = "env.sakura-leaves",
        name = "Sakura Falling Leaves",
        category = PresetCategory.ENVIRONMENT,
        description = "Tumbling blossom petals carried on a soft spring breeze.",
        targetType = listOf(PresetTargetType.ENVIRONMENT),
        environmentPatch = EnvironmentPatch(
            weatherType = WeatherType.LEAVES,
            weatherDensity = 0.52,
            weatherSpeed = 1.1,
            weatherDirectionDeg = 28.0,
            weatherSize = 1.3,
            weatherOpacity = 0.90,
            windStrength = 1.4,
            windDirection = WindDirection.RIGHT,
        ),
    ),
    MotionPresetDefinition(
        id = "env.floating-dust",
        name = "Sunbeam Dust Motes",
        category = PresetCategory.ENVIRONMENT,
        description = "Fine luminous dust motes suspended in warm studio air.",
        targetType = listOf(PresetTargetType.ENVIRONMENT),
        environmentPatch = EnvironmentPatch(
            weatherType = WeatherType.DUST,
            weatherDensity = 0.45,
            weatherSpeed = 0.6,
            weatherSize = 0.9,
            weatherOpacity = 0.75,
        ),
    ),
    MotionPresetDefinition(
        id = "env.rolling-fog",
        name = "Rolling Valley Fog",
        category = PresetCategory.ENVIRONMENT,
        description = "Layered translucent fog banks drifting slowly along the ground plane.",
        targetType = listOf(PresetTargetType.ENVIRONMENT),
        environmentPatch = EnvironmentPatch(
            fogOpacity = 0.48,
            fogSpeed = 0.8,
            fogColor = "#CBD5E1",
            hazeIntensity = 0.34,
        ),
    ),
    MotionPresetDefinition(
        id = "env.drifting-clouds",
        name = "Drifting Sky Clouds",
        category = PresetCategory.ENVIRONMENT,
        description = "Soft volumetric cloud silhouettes cruising steadily across the sky.",
        targetType = listOf(PresetTargetType.ENVIRONMENT),
        environmentPatch = EnvironmentPatch(
            cloudDensity = 0.65,
            cloudSpeed = 0.75,
        ),
    ),
    MotionPresetDefinition(
        id = "env.wind-grass",
        name = "Wind-Blown Grass",
        category = PresetCategory.ENVIRONMENT,
        description = "Animated foreground meadow silhouettes bending smoothly in the wind.",
        targetType = listOf(PresetTargetType.ENVIRONMENT),
        environmentPatch = EnvironmentPatch(
            grassDensity = 0.70,
            grassColor = "#14532D",
            windStrength = 1.3,
            windDirection = WindDirection.RIGHT,
        ),
    ),

    // LYRICS (9)
    MotionPresetDefinition(
        id = "lyrics.karaoke",
        name = "Karaoke Word Sweep",
        category = PresetCategory.LYRICS,
        description = "Progressive highlight sweep synchronized with word-level lyric cues.",
        targetType = listOf(PresetTargetType.TEXT),
        lyricsStyle = LyricsStyle.KARAOKE,
    ),
    MotionPresetDefinition(
        id = "lyrics.fade",
        name = "Cinematic Lyric Fade",
        category = PresetCategory.LYRICS,
        description = "Smooth cross-fade in and out for ballad and storytelling captions.",
        targetType = listOf(PresetTargetType.TEXT),
        lyricsStyle = LyricsStyle.FADE,
    ),
    MotionPresetDefinition(
        id = "lyrics.slide-up",
        name = "Slide Up Reveal",
        category = PresetCategory.LYRICS,
        description = "Clean upward motion slide easing into place at each cue start.",
        targetType = listOf(PresetTargetType.TEXT),
        lyricsStyle = LyricsStyle.SLIDE_UP,
    ),
    MotionPresetDefinition(
        id = "lyrics.scale-reveal",
        name = "Pop Scale Reveal",
        category = PresetCategory.LYRICS,
        description = "Punchy elastic scale-in reveal tailored for chorus drops.",
        targetType = listOf(PresetTargetType.TEXT),
        lyricsStyle = LyricsStyle.SCALE_REVEAL,
    ),
    MotionPresetDefinition(
        id = "lyrics.typewriter",
        name = "Typewriter Terminal",
        category = PresetCategory.LYRICS,
        description = "Character-by-character sequential reveal with crisp cadence.",
        targetType = listOf(PresetTargetType.TEXT),
        lyricsStyle = LyricsStyle.TYPEWRITER,
    ),
    MotionPresetDefinition(
        id = "lyrics.word-reveal",
        name = "Word-by-Word Pop",
        category = PresetCategory.LYRICS,
        description = "Each word illuminates sequentially following cue timings.",
        targetType = listOf(PresetTargetType.TEXT),
        lyricsStyle = LyricsStyle.WORD_REVEAL,
    ),
    MotionPresetDefinition(
        id = "lyrics.char-reveal",
        name = "Letter Wave Reveal",
        category = PresetCategory.LYRICS,
        description = "Individual letters cascade smoothly into view across each line.",
        targetType = listOf(PresetTargetType.TEXT),
        lyricsStyle = LyricsStyle.CHAR_REVEAL,
    ),
    MotionPresetDefinition(
        id = "lyrics.stagger",
        name = "Stagger Bounce Line",
        category = PresetCategory.LYRICS,
        description = "Offset staggered word entrance with lively vertical bounce.",
        targetType = listOf(PresetTargetType.TEXT),
        lyricsStyle = LyricsStyle.STAGGER,
    ),
    MotionPresetDefinition(
        id = "lyrics.beat-pulse",
        name = "BPM Beat Pulse",
        category = PresetCategory.LYRICS,
        description = "Tempo-synced rhythmic pulse and glow locked to your song BPM.",
        targetType = listOf(PresetTargetType.TEXT),
        lyricsStyle = LyricsStyle.BEAT_PULSE,
    ),

    // EFFECTS (3)
    MotionPresetDefinition(
        id = "fx.glow-pulse",
        name = "Beat Shimmer Pulse",
        category = PresetCategory.EFFECTS,
        description = "Rhythmic opacity and scale pulse synchronized with music tempo.",
        targetType = LAYER_TARGETS,
        duration = 1.0,
        parameters = mapOf("scaleStrength" to 0.06, "opacityPulse" to 0.25, "speed" to 1.0),
        parameterControls = listOf(
            PresetParameterControl("scaleStrength", "Scale Pulse", 0.06, 0.0, 0.35, 0.01),
            PresetParameterControl("opacityPulse", "Opacity Dip", 0.25, 0.0, 0.9, 0.05),
        ),
    ),
    MotionPresetDefinition(
        id = "fx.float-hover",
        name = "Ethereal Hover",
        category = PresetCategory.EFFECTS,
        description = "Smooth vertical levitation for lanterns, orbs, titles, and magical props.",
        targetType = LAYER_TARGETS,
        duration = 3.0,
        parameters = mapOf("movementStrength" to 12.0, "rotationStrength" to 2.0, "speed" to 1.0),
        parameterControls = listOf(
            PresetParameterControl("movementStrength", "Float Height (px)", 12.0, 0.0, 60.0, 1.0),
            PresetParameterControl("rotationStrength", "Tilt (°)", 2.0, 0.0, 20.0, 0.5),
        ),
    ),
    MotionPresetDefinition(
        id = "fx.glitch-jitter",
        name = "Electronic Jitter",
        category = PresetCategory.EFFECTS,
        description = "Deterministic micro-jitter burst for cyber or energetic music cuts.",
        targetType = LAYER_TARGETS,
        duration = 1.2,
        parameters = mapOf("movementStrength" to 6.0, "rotationStrength" to 1.5, "speed" to 2.0),
        parameterControls = listOf(
            PresetParameterControl("movementStrength", "Jitter (px)", 6.0, 0.0, 40.0, 1.0),
            PresetParameterControl("rotationStrength", "Snap (°)", 1.5, 0.0, 15.0, 0.5),
        ),
    ),
)

object AnimationPresetRegistry {
    private val customPresets = mutableListOf<MotionPresetDefinition>()

    fun allPresets(): List<MotionPresetDefinition> = BUILTIN_PRESETS + customPresets

    fun getCustomPresets(): List<MotionPresetDefinition> = customPresets.toList()

    fun setCustomPresets(list: List<MotionPresetDefinition>) {
        customPresets.clear()
        customPresets.addAll(list.map { it.copy(isCustom = true) })
    }

    fun getPresetById(id: String): MotionPresetDefinition? =
        customPresets.find { it.id == id } ?: BUILTIN_PRESETS.find { it.id == id }

    fun getAvailablePresets(
        category: PresetCategory? = null,
        targetType: PresetTargetType? = null,
    ): List<MotionPresetDefinition> = allPresets().filter { p ->
        (category == null || p.category == category) &&
            (targetType == null || targetType in p.targetType)
    }

    fun createModifierFromPreset(
        presetId: String,
        fps: Double,
        modifierId: String,
        startFrame: Int = 0,
        overrides: ((MotionModifierInstance) -> MotionModifierInstance)? = null,
    ): MotionModifierInstance? {
        val def = getPresetById(presetId) ?: return null
        val durFrames = max(1, (def.duration * max(1.0, fps)).roundToInt())
        val base = MotionModifierInstance(
            id = modifierId,
            presetId = def.id,
            basePresetId = def.basePresetId,
            label = def.name,
            enabled = true,
            startFrame = startFrame,
            durationFrames = durFrames,
            delayFrames = 0,
            loop = def.loop,
            direction = def.playbackDirection,
            blendMode = def.blendMode,
            intensity = def.defaultIntensity,
            speed = def.parameters["speed"] ?: 1.0,
            phaseOffset = 0.0,
            pivotAnchor = def.defaultPivot,
            params = def.parameters,
        )
        return overrides?.invoke(base) ?: base
    }

    fun saveCustomPresetFromModifier(
        modifier: MotionModifierInstance,
        name: String,
        fps: Double,
        description: String = "Custom preset derived from ${modifier.label}",
    ): MotionPresetDefinition {
        val base = getPresetById(modifier.basePresetId ?: modifier.presetId)
        val newId = "custom.${name.lowercase().replace(Regex("[^a-z0-9]+"), "-")}.${System.currentTimeMillis() % 10000}"
        val preset = MotionPresetDefinition(
            id = newId,
            name = name.trim().ifEmpty { modifier.label },
            category = base?.category ?: PresetCategory.EFFECTS,
            description = description,
            targetType = base?.targetType ?: LAYER_TARGETS,
            duration = max(0.25, modifier.durationFrames.toDouble() / max(1.0, fps)),
            loop = modifier.loop,
            playbackDirection = modifier.direction,
            blendMode = modifier.blendMode,
            defaultPivot = modifier.pivotAnchor,
            defaultIntensity = modifier.intensity,
            basePresetId = modifier.basePresetId ?: modifier.presetId,
            isCustom = true,
            parameters = modifier.params,
            parameterControls = base?.parameterControls ?: emptyList(),
        )
        customPresets.add(0, preset)
        return preset
    }

    fun deleteCustomPreset(id: String): Boolean = customPresets.removeAll { it.id == id }

    fun exportCustomPresetsJson(): String = StudioJson.encodeToString(customPresets.toList())

    fun importCustomPresetsJson(json: String): Int {
        val parsed = StudioJson.decodeFromString<List<MotionPresetDefinition>>(json)
        var added = 0
        for (p in parsed) {
            if (customPresets.none { it.id == p.id } && BUILTIN_PRESETS.none { it.id == p.id }) {
                customPresets.add(p.copy(isCustom = true))
                added++
            }
        }
        return added
    }
}
