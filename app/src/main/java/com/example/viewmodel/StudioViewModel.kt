package com.example.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.assets.BackgroundRemovalOptions
import com.example.assets.ProceduralAssetRenderer
import com.example.assets.SpriteBackgroundRemover
import com.example.audio.StudioAudioEngine
import java.io.ByteArrayOutputStream
import com.example.commands.CommandManager
import com.example.commands.ProjectCommand
import com.example.commands.ProjectCommands
import com.example.commands.nowIso
import com.example.commands.uid
import com.example.data.createBlankProject
import com.example.data.createLostKingdomProject
import com.example.effects.AdaptivePreviewMonitor
import com.example.effects.AdaptivePreviewState
import com.example.effects.DevicePerformanceProfile
import com.example.effects.EffectBudgetValidator
import com.example.effects.EffectComboPreset
import com.example.effects.EffectsLibraryRegistry
import com.example.effects.PreviewQualityMode
import com.example.engine.AnimationPresetRegistry
import com.example.engine.MotionPresetDefinition
import com.example.engine.evaluateCompositionAtFrame
import com.example.engine.getProjectMovieDurationFrames
import com.example.export.ExportFormat
import com.example.export.ExportProgress
import com.example.export.ExportScope
import com.example.export.ExportedArtifact
import com.example.export.StudioExporter
import com.example.mcp.EmbeddedMcpHttpServer
import com.example.mcp.ExternalMcpServer
import com.example.mcp.LocalMcpEngine
import com.example.mcp.McpActivityLogEntry
import com.example.mcp.McpCatalog
import com.example.mcp.McpExportedFileEntry
import com.example.mcp.McpPromptInfo
import com.example.mcp.McpResourceInfo
import com.example.mcp.McpServer
import com.example.mcp.McpServerBridge
import com.example.mcp.McpToolInfo
import com.example.mcp.RemoteMcpClient
import com.example.mcp.StudioMcpAsyncExecutor
import com.example.schema.AssetCategory
import com.example.schema.AssetKind
import com.example.schema.AssetRecord
import com.example.schema.AudioClipDocument
import com.example.schema.AudioTrackDocument
import com.example.schema.CameraAnimatableProp
import com.example.schema.CharacterDefinition
import com.example.schema.CompositionRecord
import com.example.schema.InterpolationType
import com.example.schema.LayerAnimatableProp
import com.example.schema.LayerDocument
import com.example.schema.LayerRole
import com.example.schema.MotionModifierInstance
import com.example.schema.MotionProject
import com.example.schema.SceneObjectDocument
import com.example.schema.ShapeType
import com.example.schema.StudioJson
import com.example.schema.Transform2D
import com.example.schema.getActiveCompFps
import com.example.storage.ProjectStorage
import com.example.storage.SavedProjectSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.roundToInt

enum class WorkspaceTab {
    HOME,
    COMPOSITION,
    MOVIE_TIMELINE,
    ASSETS,
    CHARACTERS,
    EXPORT,
    MCP,
}

enum class StageTool {
    SELECT,
    PAN,
}

enum class InspectorTarget {
    LAYER,
    PRESETS,
    CAMERA,
    WORLD,
}

enum class SaveStatus {
    SAVED,
    SAVING,
    UNSAVED,
}

data class FeedbackBanner(
    val isError: Boolean,
    val message: String,
)

data class OnionSkinSettings(
    val enabled: Boolean = false,
    val prevFrames: Int = 2,
    val nextFrames: Int = 2,
    val frameStep: Int = 1,
    val opacity: Float = 0.35f,
    val selectedLayerOnly: Boolean = false,
)

enum class KeyframeOwnerType {
    LAYER,
    CAMERA,
}

data class SelectedKeyframeRef(
    val ownerType: KeyframeOwnerType,
    val layerId: String? = null,
    val frame: Int,
    val layerProp: LayerAnimatableProp? = null,
    val cameraProp: CameraAnimatableProp? = null,
    val fromMotionPath: Boolean = false,
)

data class StudioUiState(
    val project: MotionProject,
    val workspace: WorkspaceTab = WorkspaceTab.HOME,
    val activeCompositionId: String,
    val selectedLayerId: String? = null,
    val selectedLayerIds: Set<String> = emptySet(),
    val selectedKeyframe: SelectedKeyframeRef? = null,
    val inspectorTarget: InspectorTarget = InspectorTarget.LAYER,
    val stageTool: StageTool = StageTool.SELECT,
    val showGuides: Boolean = false,
    val showMotionPath: Boolean = true,
    val reducedQuality: Boolean = false,
    val onionSkin: OnionSkinSettings = OnionSkinSettings(),
    val currentFrame: Int = 0,
    val movieFrame: Int = 0,
    val isPlaying: Boolean = false,
    val isMoviePlaying: Boolean = false,
    val loopPlayback: Boolean = true,
    val masterMuted: Boolean = false,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val undoLabel: String? = null,
    val redoLabel: String? = null,
    val saveStatus: SaveStatus = SaveStatus.SAVED,
    val savedProjects: List<SavedProjectSummary> = emptyList(),
    val copiedKeyframes: Map<LayerAnimatableProp, Pair<Double, InterpolationType>> = emptyMap(),
    val customPresets: List<MotionPresetDefinition> = emptyList(),
    val customEffectCombos: List<EffectComboPreset> = emptyList(),
    val adaptivePreview: AdaptivePreviewState = AdaptivePreviewState(),
    val feedback: FeedbackBanner? = null,
    val exportProgress: ExportProgress? = null,
    val isExporting: Boolean = false,
    val lastExportedArtifact: ExportedArtifact? = null,
    val bgRemovalAssetId: String? = null,
    val isRemovingBackground: Boolean = false,
    val externalMcpServers: List<ExternalMcpServer> = emptyList(),
    val mcpLogs: List<McpActivityLogEntry> = emptyList(),
    val mcpServerRunning: Boolean = false,
    val mcpServerPort: Int = EmbeddedMcpHttpServer.DEFAULT_PORT,
    val mcpServerHostIp: String = EmbeddedMcpHttpServer.detectLocalIpv4(),
    val mcpServerRequestCount: Int = 0,
    val mcpServerSessionCount: Int = 0,
    val mcpServerLastError: String? = null,
    val mcpRecentExports: List<McpExportedFileEntry> = emptyList(),
) {
    val activeComposition: CompositionRecord
        get() = project.compositions.find { it.id == activeCompositionId } ?: project.compositions.first()
}

class StudioViewModel(app: Application) : AndroidViewModel(app) {
    private val storage = ProjectStorage(app)
    private val audioEngine = StudioAudioEngine(app)
    private val commandManager = CommandManager()
    private val previewMonitor = AdaptivePreviewMonitor()
    private var lastFrameReportNs: Long = 0L

    private var playbackJob: Job? = null
    private var moviePlaybackJob: Job? = null
    private var autosaveJob: Job? = null
    private var exportJob: Job? = null
    private var feedbackJob: Job? = null

    private val mcpExportRegistry = ConcurrentHashMap<String, McpExportedFileEntry>()
    private val mcpAsyncExecutor: StudioMcpAsyncExecutor by lazy {
        StudioMcpAsyncExecutor(
            app = getApplication(),
            storage = storage,
            getState = { _uiState.value },
            dispatchCommandOnMain = { cmd -> withContext(Dispatchers.Main) { dispatchCommand(cmd) } },
            setActiveCompositionOnMain = { compId -> withContext(Dispatchers.Main) { setActiveComposition(compId) } },
            undoOnMain = {
                withContext(Dispatchers.Main) {
                    val before = _uiState.value.canUndo
                    if (before) undo()
                    before
                }
            },
            redoOnMain = {
                withContext(Dispatchers.Main) {
                    val before = _uiState.value.canRedo
                    if (before) redo()
                    before
                }
            },
            onExportStateChanged = { exporting, progress ->
                _uiState.update { it.copy(isExporting = exporting, exportProgress = progress) }
            },
            onRegisterExport = { entry ->
                mcpExportRegistry[entry.fileName] = entry
                _uiState.update { s ->
                    s.copy(mcpRecentExports = (listOf(entry) + s.mcpRecentExports.filterNot { it.fileName == entry.fileName }).take(12))
                }
            },
        )
    }
    private val mcpHttpServer: McpServer by lazy {
        McpServer(
            bridge = createMcpBridge(),
            projectProvider = { _uiState.value.project },
            activeCompIdProvider = { _uiState.value.activeCompositionId },
        )
    }

    private val initialProfile = DevicePerformanceProfile.detectRecommended(app)
    private val initialProject = createLostKingdomProject()
    private val _uiState = MutableStateFlow(
        StudioUiState(
            project = initialProject,
            activeCompositionId = initialProject.compositions.first().id,
            selectedLayerId = initialProject.compositions.first().layers.find { it.id == "layer_traveler" }?.id
                ?: initialProject.compositions.first().layers.firstOrNull()?.id,
            adaptivePreview = AdaptivePreviewState(
                deviceProfile = initialProfile,
                qualityMode = PreviewQualityMode.AUTOMATIC,
                adaptiveTier = if (initialProfile == DevicePerformanceProfile.LOW_END) 1 else 2,
            ),
        )
    )
    val uiState: StateFlow<StudioUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val loadedPresets = storage.loadCustomPresets()
            AnimationPresetRegistry.setCustomPresets(loadedPresets)
            val loadedCombos = storage.loadCustomEffectCombos()
            EffectsLibraryRegistry.setCustomCombos(loadedCombos)

            val savedList = storage.listSavedProjects()
            val lastId = storage.getLastOpenedProjectId()
            val restored = lastId?.let { storage.loadProject(it) }
            if (restored != null && restored.compositions.isNotEmpty()) {
                val firstComp = restored.compositions.first()
                _uiState.update {
                    it.copy(
                        project = restored,
                        activeCompositionId = firstComp.id,
                        selectedLayerId = firstComp.layers.firstOrNull()?.id,
                        savedProjects = savedList,
                        customPresets = loadedPresets,
                        customEffectCombos = loadedCombos,
                        saveStatus = SaveStatus.SAVED,
                    )
                }
            } else {
                storage.saveProject(initialProject)
                _uiState.update {
                    it.copy(
                        savedProjects = storage.listSavedProjects(),
                        customPresets = loadedPresets,
                        customEffectCombos = loadedCombos,
                    )
                }
            }
        }
    }

    fun dispatchCommand(command: ProjectCommand): Boolean {
        val current = _uiState.value.project
        val (next, err) = commandManager.execute(current, command)
        if (err != null) {
            showFeedback(true, err)
            return false
        }
        if (next == current) return true

        _uiState.update { s ->
            val validCompId = if (next.compositions.any { it.id == s.activeCompositionId }) {
                s.activeCompositionId
            } else next.compositions.first().id
            val activeComp = next.compositions.find { it.id == validCompId } ?: next.compositions.first()
            val validLayerId = if (activeComp.layers.any { it.id == s.selectedLayerId }) s.selectedLayerId else null
            val validKf = validateSelectedKeyframe(activeComp, s.selectedKeyframe)
            s.copy(
                project = next,
                activeCompositionId = validCompId,
                selectedLayerId = validLayerId,
                selectedLayerIds = s.selectedLayerIds.filter { id -> activeComp.layers.any { it.id == id } }.toSet(),
                selectedKeyframe = validKf,
                currentFrame = s.currentFrame.coerceIn(0, max(0, activeComp.settings.durationFrames - 1)),
                canUndo = commandManager.canUndo(),
                canRedo = commandManager.canRedo(),
                undoLabel = commandManager.undoLabel(),
                redoLabel = commandManager.redoLabel(),
                saveStatus = SaveStatus.UNSAVED,
            )
        }
        scheduleAutosave()
        return true
    }

    fun undo() {
        val res = commandManager.undo() ?: return
        _uiState.update { s ->
            val next = res.first
            val compId = if (next.compositions.any { it.id == s.activeCompositionId }) s.activeCompositionId else next.compositions.first().id
            val activeComp = next.compositions.find { it.id == compId } ?: next.compositions.first()
            val validLayerId = if (activeComp.layers.any { it.id == s.selectedLayerId }) s.selectedLayerId else activeComp.layers.firstOrNull()?.id
            s.copy(
                project = next,
                activeCompositionId = compId,
                selectedLayerId = validLayerId,
                selectedKeyframe = validateSelectedKeyframe(activeComp, s.selectedKeyframe),
                canUndo = commandManager.canUndo(),
                canRedo = commandManager.canRedo(),
                undoLabel = commandManager.undoLabel(),
                redoLabel = commandManager.redoLabel(),
                saveStatus = SaveStatus.UNSAVED,
            )
        }
        showFeedback(false, "Undid: ${res.second}")
        scheduleAutosave()
    }

    fun redo() {
        val res = commandManager.redo() ?: return
        _uiState.update { s ->
            val next = res.first
            val compId = if (next.compositions.any { it.id == s.activeCompositionId }) s.activeCompositionId else next.compositions.first().id
            val activeComp = next.compositions.find { it.id == compId } ?: next.compositions.first()
            val validLayerId = if (activeComp.layers.any { it.id == s.selectedLayerId }) s.selectedLayerId else activeComp.layers.firstOrNull()?.id
            s.copy(
                project = next,
                activeCompositionId = compId,
                selectedLayerId = validLayerId,
                selectedKeyframe = validateSelectedKeyframe(activeComp, s.selectedKeyframe),
                canUndo = commandManager.canUndo(),
                canRedo = commandManager.canRedo(),
                undoLabel = commandManager.undoLabel(),
                redoLabel = commandManager.redoLabel(),
                saveStatus = SaveStatus.UNSAVED,
            )
        }
        showFeedback(false, "Redid: ${res.second}")
        scheduleAutosave()
    }

    private fun validateSelectedKeyframe(comp: CompositionRecord, ref: SelectedKeyframeRef?): SelectedKeyframeRef? {
        if (ref == null) return null
        return when (ref.ownerType) {
            KeyframeOwnerType.LAYER -> {
                val layer = comp.layers.find { it.id == ref.layerId } ?: return null
                val exists = when {
                    ref.layerProp != null -> layerChannelList(layer, ref.layerProp).any { it.frame == ref.frame }
                    ref.fromMotionPath -> layer.keyframes.x.any { it.frame == ref.frame } || layer.keyframes.y.any { it.frame == ref.frame }
                    else -> LayerAnimatableProp.entries.any { p -> layerChannelList(layer, p).any { it.frame == ref.frame } }
                }
                if (exists) ref else null
            }
            KeyframeOwnerType.CAMERA -> {
                val ckf = comp.camera.keyframes
                val exists = when (ref.cameraProp) {
                    CameraAnimatableProp.X -> ckf.x.any { it.frame == ref.frame }
                    CameraAnimatableProp.Y -> ckf.y.any { it.frame == ref.frame }
                    CameraAnimatableProp.ZOOM -> ckf.zoom.any { it.frame == ref.frame }
                    null -> ckf.x.any { it.frame == ref.frame } || ckf.y.any { it.frame == ref.frame } || ckf.zoom.any { it.frame == ref.frame }
                }
                if (exists) ref else null
            }
        }
    }

    private fun layerChannelList(layer: LayerDocument, prop: LayerAnimatableProp) = when (prop) {
        LayerAnimatableProp.X -> layer.keyframes.x
        LayerAnimatableProp.Y -> layer.keyframes.y
        LayerAnimatableProp.ROTATION -> layer.keyframes.rotation
        LayerAnimatableProp.SCALE_X -> layer.keyframes.scaleX
        LayerAnimatableProp.SCALE_Y -> layer.keyframes.scaleY
        LayerAnimatableProp.OPACITY -> layer.keyframes.opacity
    }

    private fun scheduleAutosave() {
        autosaveJob?.cancel()
        autosaveJob = viewModelScope.launch(Dispatchers.IO) {
            delay(500)
            _uiState.update { it.copy(saveStatus = SaveStatus.SAVING) }
            val p = _uiState.value.project
            runCatching { storage.saveProject(p) }
            val list = storage.listSavedProjects()
            _uiState.update { it.copy(saveStatus = SaveStatus.SAVED, savedProjects = list) }
        }
    }

    fun showFeedback(isError: Boolean, message: String) {
        feedbackJob?.cancel()
        _uiState.update { it.copy(feedback = FeedbackBanner(isError, message)) }
        feedbackJob = viewModelScope.launch {
            delay(3200)
            _uiState.update { it.copy(feedback = null) }
        }
    }

    fun clearFeedback() {
        feedbackJob?.cancel()
        _uiState.update { it.copy(feedback = null) }
    }

    fun setWorkspace(tab: WorkspaceTab) {
        if (tab != WorkspaceTab.COMPOSITION) stopPlayback()
        if (tab != WorkspaceTab.MOVIE_TIMELINE) stopMoviePlayback()
        _uiState.update { it.copy(workspace = tab) }
    }

    fun setActiveComposition(compId: String) {
        stopPlayback()
        val comp = _uiState.value.project.compositions.find { it.id == compId } ?: return
        _uiState.update {
            it.copy(
                activeCompositionId = comp.id,
                selectedLayerId = comp.layers.firstOrNull()?.id,
                selectedLayerIds = emptySet(),
                selectedKeyframe = null,
                currentFrame = 0,
            )
        }
    }

    fun selectLayer(layerId: String?, multiToggle: Boolean = false) {
        _uiState.update { s ->
            val keepKf = s.selectedKeyframe?.takeIf {
                it.ownerType == KeyframeOwnerType.LAYER && it.layerId == layerId
            }
            if (multiToggle && layerId != null) {
                val nextSet = if (layerId in s.selectedLayerIds) s.selectedLayerIds - layerId else s.selectedLayerIds + layerId
                s.copy(selectedLayerId = layerId, selectedLayerIds = nextSet, selectedKeyframe = keepKf)
            } else {
                s.copy(
                    selectedLayerId = layerId,
                    selectedLayerIds = if (layerId != null) setOf(layerId) else emptySet(),
                    selectedKeyframe = keepKf,
                )
            }
        }
    }

    fun selectKeyframe(ref: SelectedKeyframeRef?, movePlayhead: Boolean = true) {
        if (ref == null) {
            _uiState.update { it.copy(selectedKeyframe = null) }
            return
        }
        if (movePlayhead) stopPlayback()
        _uiState.update { s ->
            val comp = s.activeComposition
            val maxF = max(0, comp.settings.durationFrames - 1)
            val clampedFrame = ref.frame.coerceIn(0, maxF)
            val normalizedRef = ref.copy(frame = clampedFrame)
            val validRef = validateSelectedKeyframe(comp, normalizedRef)
            if (validRef == null) {
                s.copy(
                    currentFrame = if (movePlayhead) clampedFrame else s.currentFrame,
                    selectedKeyframe = null,
                )
            } else {
                when (validRef.ownerType) {
                    KeyframeOwnerType.LAYER -> s.copy(
                        selectedLayerId = validRef.layerId ?: s.selectedLayerId,
                        selectedLayerIds = validRef.layerId?.let { setOf(it) } ?: s.selectedLayerIds,
                        inspectorTarget = InspectorTarget.LAYER,
                        currentFrame = if (movePlayhead) clampedFrame else s.currentFrame,
                        selectedKeyframe = validRef,
                    )
                    KeyframeOwnerType.CAMERA -> s.copy(
                        inspectorTarget = InspectorTarget.CAMERA,
                        currentFrame = if (movePlayhead) clampedFrame else s.currentFrame,
                        selectedKeyframe = validRef,
                    )
                }
            }
        }
    }

    fun clearKeyframeSelection() {
        _uiState.update { it.copy(selectedKeyframe = null) }
    }

    fun deleteSelectedKeyframe() {
        val s = _uiState.value
        val ref = s.selectedKeyframe ?: return
        val compId = s.activeCompositionId
        val ok = when (ref.ownerType) {
            KeyframeOwnerType.LAYER -> {
                val layerId = ref.layerId ?: return
                val props = targetLayerPropsForRef(ref)
                dispatchCommand(ProjectCommands.removeLayerKeyframesAtFrame(compId, layerId, ref.frame, props))
            }
            KeyframeOwnerType.CAMERA -> {
                val props = ref.cameraProp?.let { setOf(it) } ?: CameraAnimatableProp.entries.toSet()
                dispatchCommand(ProjectCommands.removeCameraKeyframesAtFrame(compId, ref.frame, props))
            }
        }
        if (ok) {
            clearKeyframeSelection()
            showFeedback(false, "Deleted keyframe at F${ref.frame}")
        }
    }

    fun duplicateSelectedKeyframe(offsetFrames: Int = 10) {
        val s = _uiState.value
        val ref = s.selectedKeyframe ?: return
        val comp = s.activeComposition
        val maxF = max(0, comp.settings.durationFrames - 1)
        val occupiedFrames: Set<Int> = when (ref.ownerType) {
            KeyframeOwnerType.LAYER -> {
                val layer = comp.layers.find { it.id == ref.layerId } ?: return
                targetLayerPropsForRef(ref).flatMap { p -> layerChannelList(layer, p).map { it.frame } }.toSet()
            }
            KeyframeOwnerType.CAMERA -> {
                val ckf = comp.camera.keyframes
                val props = ref.cameraProp?.let { setOf(it) } ?: CameraAnimatableProp.entries.toSet()
                buildSet {
                    if (CameraAnimatableProp.X in props) addAll(ckf.x.map { it.frame })
                    if (CameraAnimatableProp.Y in props) addAll(ckf.y.map { it.frame })
                    if (CameraAnimatableProp.ZOOM in props) addAll(ckf.zoom.map { it.frame })
                }
            }
        }
        val preferred = (ref.frame + offsetFrames).coerceIn(0, maxF)
        val targetFrame = if (preferred != ref.frame && preferred !in occupiedFrames) {
            preferred
        } else {
            ((ref.frame + 1)..maxF).firstOrNull { it !in occupiedFrames }
                ?: ((ref.frame - 1) downTo 0).firstOrNull { it !in occupiedFrames }
                ?: preferred
        }
        if (targetFrame == ref.frame) {
            showFeedback(true, "No free frame available to duplicate keyframe")
            return
        }
        val ok = when (ref.ownerType) {
            KeyframeOwnerType.LAYER -> {
                val layerId = ref.layerId ?: return
                dispatchCommand(
                    ProjectCommands.duplicateLayerKeyframes(
                        comp.id,
                        layerId,
                        ref.frame,
                        targetFrame,
                        targetLayerPropsForRef(ref),
                    )
                )
            }
            KeyframeOwnerType.CAMERA -> {
                val props = ref.cameraProp?.let { setOf(it) } ?: CameraAnimatableProp.entries.toSet()
                dispatchCommand(ProjectCommands.duplicateCameraKeyframes(comp.id, ref.frame, targetFrame, props))
            }
        }
        if (ok) {
            selectKeyframe(ref.copy(frame = targetFrame), movePlayhead = true)
            showFeedback(false, "Duplicated keyframe to F$targetFrame")
        }
    }

    fun setSelectedKeyframeInterpolation(interpolation: InterpolationType) {
        val s = _uiState.value
        val ref = s.selectedKeyframe ?: return
        val compId = s.activeCompositionId
        val ok = when (ref.ownerType) {
            KeyframeOwnerType.LAYER -> {
                val layerId = ref.layerId ?: return
                dispatchCommand(
                    ProjectCommands.updateLayerKeyframesAtFrame(
                        compId = compId,
                        layerId = layerId,
                        sourceFrame = ref.frame,
                        props = targetLayerPropsForRef(ref),
                        newInterpolation = interpolation,
                    )
                )
            }
            KeyframeOwnerType.CAMERA -> {
                val props = ref.cameraProp?.let { setOf(it) } ?: CameraAnimatableProp.entries.toSet()
                dispatchCommand(
                    ProjectCommands.updateCameraKeyframesAtFrame(
                        compId = compId,
                        sourceFrame = ref.frame,
                        props = props,
                        newInterpolation = interpolation,
                    )
                )
            }
        }
        if (ok) {
            showFeedback(false, "Set easing to ${interpolation.name.replace('_', ' ')}")
        }
    }

    fun editSelectedKeyframe(
        newFrame: Int,
        newLayerValues: Map<LayerAnimatableProp, Double> = emptyMap(),
        newCameraValues: Map<CameraAnimatableProp, Double> = emptyMap(),
        newInterpolation: InterpolationType? = null,
    ) {
        val s = _uiState.value
        val ref = s.selectedKeyframe ?: return
        val comp = s.activeComposition
        val clampedFrame = newFrame.coerceIn(0, max(0, comp.settings.durationFrames - 1))
        val ok = when (ref.ownerType) {
            KeyframeOwnerType.LAYER -> {
                val layerId = ref.layerId ?: return
                dispatchCommand(
                    ProjectCommands.updateLayerKeyframesAtFrame(
                        compId = comp.id,
                        layerId = layerId,
                        sourceFrame = ref.frame,
                        props = targetLayerPropsForRef(ref),
                        newFrame = clampedFrame,
                        newValues = newLayerValues,
                        newInterpolation = newInterpolation,
                    )
                )
            }
            KeyframeOwnerType.CAMERA -> {
                val props = ref.cameraProp?.let { setOf(it) } ?: CameraAnimatableProp.entries.toSet()
                dispatchCommand(
                    ProjectCommands.updateCameraKeyframesAtFrame(
                        compId = comp.id,
                        sourceFrame = ref.frame,
                        props = props,
                        newFrame = clampedFrame,
                        newValues = newCameraValues,
                        newInterpolation = newInterpolation,
                    )
                )
            }
        }
        if (ok) {
            selectKeyframe(ref.copy(frame = clampedFrame), movePlayhead = true)
            showFeedback(false, "Updated keyframe at F$clampedFrame")
        }
    }

    fun clearLayerPropertyAnimation(layerId: String, prop: LayerAnimatableProp) {
        val s = _uiState.value
        val comp = s.activeComposition
        val layer = comp.layers.find { it.id == layerId } ?: return
        val evaluated = evaluateCompositionAtFrame(s.project, comp.id, s.currentFrame)
        val lt = evaluated?.layers?.find { it.layer.id == layerId }?.localTransform
        val bakeVal = when (prop) {
            LayerAnimatableProp.X -> lt?.x ?: layer.transform.x
            LayerAnimatableProp.Y -> lt?.y ?: layer.transform.y
            LayerAnimatableProp.ROTATION -> lt?.rotationDeg ?: layer.transform.rotationDeg
            LayerAnimatableProp.SCALE_X -> lt?.scaleX ?: layer.transform.scaleX
            LayerAnimatableProp.SCALE_Y -> lt?.scaleY ?: layer.transform.scaleY
            LayerAnimatableProp.OPACITY -> lt?.opacity ?: layer.opacity
        }
        if (dispatchCommand(ProjectCommands.clearLayerKeyframeChannel(comp.id, layerId, prop, bakeVal))) {
            showFeedback(false, "Cleared ${prop.key} animation on \"${layer.name}\"")
        }
    }

    fun clearAllLayerAnimation(layerId: String) {
        val s = _uiState.value
        val comp = s.activeComposition
        val layer = comp.layers.find { it.id == layerId } ?: return
        val evaluated = evaluateCompositionAtFrame(s.project, comp.id, s.currentFrame)
        val lt = evaluated?.layers?.find { it.layer.id == layerId }?.localTransform
        val bakeTransform = if (lt != null) {
            layer.transform.copy(
                x = lt.x,
                y = lt.y,
                scaleX = lt.scaleX,
                scaleY = lt.scaleY,
                rotationDeg = lt.rotationDeg,
            )
        } else layer.transform
        val bakeOpacity = lt?.opacity ?: layer.opacity
        if (dispatchCommand(ProjectCommands.clearAllLayerAnimation(comp.id, layerId, bakeTransform, bakeOpacity))) {
            clearKeyframeSelection()
            showFeedback(false, "Cleared all animation on \"${layer.name}\"")
        }
    }

    fun clearCameraPropertyAnimation(prop: CameraAnimatableProp) {
        val s = _uiState.value
        val comp = s.activeComposition
        val evalCam = evaluateCompositionAtFrame(s.project, comp.id, s.currentFrame)?.camera
        val bakeVal = when (prop) {
            CameraAnimatableProp.X -> evalCam?.x ?: comp.camera.x
            CameraAnimatableProp.Y -> evalCam?.y ?: comp.camera.y
            CameraAnimatableProp.ZOOM -> evalCam?.zoom ?: comp.camera.zoom
        }
        if (dispatchCommand(ProjectCommands.clearCameraKeyframeChannel(comp.id, prop, bakeVal))) {
            showFeedback(false, "Cleared camera ${prop.key} animation")
        }
    }

    fun clearAllCameraAnimation() {
        val s = _uiState.value
        val comp = s.activeComposition
        val evalCam = evaluateCompositionAtFrame(s.project, comp.id, s.currentFrame)?.camera
        if (dispatchCommand(
                ProjectCommands.clearAllCameraAnimation(
                    comp.id,
                    bakeX = evalCam?.x ?: comp.camera.x,
                    bakeY = evalCam?.y ?: comp.camera.y,
                    bakeZoom = evalCam?.zoom ?: comp.camera.zoom,
                )
            )
        ) {
            clearKeyframeSelection()
            showFeedback(false, "Cleared all camera animation")
        }
    }

    private fun targetLayerPropsForRef(ref: SelectedKeyframeRef): Set<LayerAnimatableProp> = when {
        ref.layerProp != null -> setOf(ref.layerProp)
        ref.fromMotionPath -> setOf(LayerAnimatableProp.X, LayerAnimatableProp.Y)
        else -> LayerAnimatableProp.entries.toSet()
    }

    fun setInspectorTarget(target: InspectorTarget) {
        _uiState.update { it.copy(inspectorTarget = target) }
    }

    fun setStageTool(tool: StageTool) {
        _uiState.update { it.copy(stageTool = tool) }
    }

    fun toggleGuides() = _uiState.update { it.copy(showGuides = !it.showGuides) }
    fun toggleMotionPath() = _uiState.update { it.copy(showMotionPath = !it.showMotionPath) }
    fun toggleReducedQuality() = _uiState.update { s ->
        val nextReduced = !s.reducedQuality
        val nextMode = if (nextReduced) PreviewQualityMode.PERFORMANCE else PreviewQualityMode.AUTOMATIC
        s.copy(
            reducedQuality = nextReduced,
            adaptivePreview = s.adaptivePreview.copy(qualityMode = nextMode),
        )
    }

    fun setDeviceProfile(profile: DevicePerformanceProfile) {
        previewMonitor.reset()
        _uiState.update { s ->
            val maxTier = if (profile == DevicePerformanceProfile.LOW_END) 1 else 2
            s.copy(
                adaptivePreview = s.adaptivePreview.copy(
                    deviceProfile = profile,
                    adaptiveTier = maxTier,
                    isDegradedByPerformance = false,
                )
            )
        }
        showFeedback(false, "Target profile: ${profile.displayName}")
    }

    fun setPreviewQualityMode(mode: PreviewQualityMode) {
        previewMonitor.reset()
        _uiState.update { s ->
            val nextAdaptive = s.adaptivePreview.copy(
                qualityMode = mode,
                isDegradedByPerformance = false,
            )
            s.copy(
                reducedQuality = mode == PreviewQualityMode.PERFORMANCE,
                adaptivePreview = nextAdaptive,
            )
        }
        showFeedback(false, "Preview quality: ${mode.displayName} (Export unaffected)")
    }

    fun reportPreviewFrameRenderMs(renderMs: Float) {
        val now = System.nanoTime()
        if (now - lastFrameReportNs < 220_000_000L) return // throttle UI telemetry updates (~4.5 Hz max)
        lastFrameReportNs = now
        val comp = _uiState.value.activeComposition
        val activeFx = comp.layers.sumOf { l -> l.modifiers.count { it.enabled } } +
            comp.camera.modifiers.count { it.enabled }
        val rt = Runtime.getRuntime()
        val usedRatio = (rt.totalMemory() - rt.freeMemory()).toFloat() / max(1L, rt.maxMemory()).toFloat()
        val memHigh = usedRatio > 0.82f

        _uiState.update { s ->
            val next = previewMonitor.recordFrame(
                current = s.adaptivePreview,
                renderTimeMs = renderMs,
                activeEffectCount = activeFx,
                memoryPressureHigh = memHigh,
            )
            s.copy(adaptivePreview = next)
        }
    }
    fun toggleLoopPlayback() = _uiState.update { it.copy(loopPlayback = !it.loopPlayback) }

    fun toggleOnionSkin() = _uiState.update {
        it.copy(onionSkin = it.onionSkin.copy(enabled = !it.onionSkin.enabled))
    }

    fun updateOnionSkin(transform: (OnionSkinSettings) -> OnionSkinSettings) = _uiState.update {
        val next = transform(it.onionSkin)
        it.copy(
            onionSkin = next.copy(
                prevFrames = next.prevFrames.coerceIn(0, 5),
                nextFrames = next.nextFrames.coerceIn(0, 5),
                frameStep = next.frameStep.coerceIn(1, 6),
                opacity = next.opacity.coerceIn(0.10f, 0.70f),
            )
        )
    }

    fun toggleMasterMute() {
        val nextMuted = !_uiState.value.masterMuted
        _uiState.update { it.copy(masterMuted = nextMuted) }
        if (nextMuted) audioEngine.stopAll()
    }

    fun setCurrentFrame(frame: Int) {
        val comp = _uiState.value.activeComposition
        val clamped = frame.coerceIn(0, max(0, comp.settings.durationFrames - 1))
        _uiState.update { it.copy(currentFrame = clamped) }
    }

    fun stepFrame(delta: Int) {
        stopPlayback()
        setCurrentFrame(_uiState.value.currentFrame + delta)
    }

    fun togglePlayback() {
        if (_uiState.value.isPlaying) stopPlayback() else startPlayback()
    }

    fun startPlayback() {
        stopPlayback()
        val state = _uiState.value
        val comp = state.activeComposition
        val fps = getActiveCompFps(state.project, comp.id)
        val duration = max(1, comp.settings.durationFrames)
        val startFrame = if (state.currentFrame >= duration - 1) 0 else state.currentFrame

        _uiState.update { it.copy(isPlaying = true, currentFrame = startFrame) }
        audioEngine.startPlayback(comp.audioTracks, state.project.assets, startFrame, duration, fps, state.masterMuted)

        playbackJob = viewModelScope.launch {
            val startTimeNs = System.nanoTime()
            val frameDurationMs = (1000.0 / max(1.0, fps)).toLong().coerceAtLeast(16L)
            while (isActive && _uiState.value.isPlaying) {
                val elapsedSec = (System.nanoTime() - startTimeNs) / 1_000_000_000.0
                val rawFrame = startFrame + (elapsedSec * fps).toInt()
                val s = _uiState.value
                val maxDur = max(1, s.activeComposition.settings.durationFrames)
                if (rawFrame >= maxDur) {
                    if (s.loopPlayback) {
                        startPlayback()
                        return@launch
                    } else {
                        _uiState.update { it.copy(currentFrame = maxDur - 1, isPlaying = false) }
                        audioEngine.stopAll()
                        return@launch
                    }
                } else {
                    if (s.currentFrame != rawFrame) {
                        _uiState.update { it.copy(currentFrame = rawFrame) }
                    }
                }
                delay(frameDurationMs / 2)
            }
        }
    }

    fun stopPlayback() {
        playbackJob?.cancel()
        playbackJob = null
        audioEngine.stopAll()
        if (_uiState.value.isPlaying) {
            _uiState.update { it.copy(isPlaying = false) }
        }
    }

    fun setMovieFrame(frame: Int) {
        val dur = getProjectMovieDurationFrames(_uiState.value.project)
        _uiState.update { it.copy(movieFrame = frame.coerceIn(0, max(0, dur - 1))) }
    }

    fun toggleMoviePlayback() {
        if (_uiState.value.isMoviePlaying) stopMoviePlayback() else startMoviePlayback()
    }

    private fun startMoviePlayback() {
        stopMoviePlayback()
        val p = _uiState.value.project
        val fps = com.example.schema.fpsToDouble(p.fps)
        val dur = getProjectMovieDurationFrames(p)
        val startF = if (_uiState.value.movieFrame >= dur - 1) 0 else _uiState.value.movieFrame
        _uiState.update { it.copy(isMoviePlaying = true, movieFrame = startF) }

        moviePlaybackJob = viewModelScope.launch {
            val t0 = System.nanoTime()
            while (isActive && _uiState.value.isMoviePlaying) {
                val elapsed = (System.nanoTime() - t0) / 1_000_000_000.0
                val nextF = startF + (elapsed * fps).toInt()
                val total = getProjectMovieDurationFrames(_uiState.value.project)
                if (nextF >= total) {
                    _uiState.update { it.copy(movieFrame = 0, isMoviePlaying = false) }
                    return@launch
                } else {
                    _uiState.update { it.copy(movieFrame = nextF) }
                }
                delay(16L)
            }
        }
    }

    fun stopMoviePlayback() {
        moviePlaybackJob?.cancel()
        moviePlaybackJob = null
        if (_uiState.value.isMoviePlaying) {
            _uiState.update { it.copy(isMoviePlaying = false) }
        }
    }

    fun loadProjectObject(project: MotionProject, switchToEditor: Boolean = true) {
        stopPlayback()
        stopMoviePlayback()
        commandManager.clear()
        val comp = project.compositions.first()
        _uiState.update {
            it.copy(
                project = project,
                activeCompositionId = comp.id,
                selectedLayerId = comp.layers.firstOrNull()?.id,
                selectedLayerIds = emptySet(),
                selectedKeyframe = null,
                currentFrame = 0,
                movieFrame = 0,
                canUndo = false,
                canRedo = false,
                undoLabel = null,
                redoLabel = null,
                workspace = if (switchToEditor) WorkspaceTab.COMPOSITION else it.workspace,
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            storage.saveProject(project)
            _uiState.update { it.copy(savedProjects = storage.listSavedProjects(), saveStatus = SaveStatus.SAVED) }
        }
    }

    fun openSavedProject(projectId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val loaded = storage.loadProject(projectId)
            if (loaded != null) {
                loadProjectObject(loaded, switchToEditor = true)
                showFeedback(false, "Opened \"${loaded.name}\"")
            } else {
                showFeedback(true, "Could not load saved project")
            }
        }
    }

    fun deleteSavedProject(projectId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            storage.deleteProject(projectId)
            val list = storage.listSavedProjects()
            _uiState.update { it.copy(savedProjects = list) }
            showFeedback(false, "Project deleted")
        }
    }

    fun duplicateSavedProject(projectId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val source = storage.loadProject(projectId)
            if (source == null) {
                showFeedback(true, "Could not duplicate project")
                return@launch
            }
            val now = nowIso()
            val copy = source.copy(
                id = uid("proj"),
                name = "${source.name} Copy",
                createdAt = now,
                updatedAt = now,
            )
            storage.saveProject(copy)
            val list = storage.listSavedProjects()
            _uiState.update { it.copy(savedProjects = list) }
            showFeedback(false, "Duplicated \"${source.name}\"")
        }
    }

    fun createNewProject(name: String, width: Int, height: Int, fps: Int = 24, durationFrames: Int = 240) {
        val proj = createBlankProject(name, width, height, fps, durationFrames)
        loadProjectObject(proj, switchToEditor = true)
        showFeedback(false, "Created \"${proj.name}\"")
    }

    fun addQuickShapeLayer(shapeType: ShapeType = ShapeType.RECT) {
        val comp = _uiState.value.activeComposition
        val layer = LayerDocument(
            id = uid("layer"),
            name = "${shapeType.name.lowercase().replaceFirstChar { it.uppercase() }} ${comp.layers.size + 1}",
            role = LayerRole.PROP,
            outFrame = comp.settings.durationFrames,
            transform = Transform2D(comp.settings.width / 2.0, comp.settings.height / 2.0),
            objects = listOf(
                SceneObjectDocument.ShapeObject(
                    id = uid("obj"),
                    name = shapeType.name,
                    shapeType = shapeType,
                    width = 180.0,
                    height = 180.0,
                    fill = "#3B82F6",
                    stroke = "#93C5FD",
                    strokeWidth = 3.0,
                )
            ),
        )
        if (dispatchCommand(ProjectCommands.addLayer(comp.id, layer))) {
            selectLayer(layer.id)
        }
    }

    fun addQuickTextLayer(initialText: String = "New Title") {
        val comp = _uiState.value.activeComposition
        val layer = LayerDocument(
            id = uid("layer"),
            name = "Text ${comp.layers.size + 1}",
            role = LayerRole.FOREGROUND,
            outFrame = comp.settings.durationFrames,
            transform = Transform2D(comp.settings.width / 2.0, comp.settings.height / 2.0),
            objects = listOf(
                SceneObjectDocument.TextObject(
                    id = uid("obj"),
                    name = "Text",
                    text = initialText,
                    fontSize = 50.0,
                    color = "#F8FAFC",
                )
            ),
        )
        if (dispatchCommand(ProjectCommands.addLayer(comp.id, layer))) {
            selectLayer(layer.id)
        }
    }

    fun copyKeyframesAtCurrentFrame() {
        val state = _uiState.value
        val layer = state.activeComposition.layers.find { it.id == state.selectedLayerId } ?: return
        val f = state.currentFrame
        val map = mutableMapOf<LayerAnimatableProp, Pair<Double, InterpolationType>>()
        layer.keyframes.x.find { it.frame == f }?.let { map[LayerAnimatableProp.X] = it.value to it.interpolation }
        layer.keyframes.y.find { it.frame == f }?.let { map[LayerAnimatableProp.Y] = it.value to it.interpolation }
        layer.keyframes.rotation.find { it.frame == f }?.let { map[LayerAnimatableProp.ROTATION] = it.value to it.interpolation }
        layer.keyframes.scaleX.find { it.frame == f }?.let { map[LayerAnimatableProp.SCALE_X] = it.value to it.interpolation }
        layer.keyframes.scaleY.find { it.frame == f }?.let { map[LayerAnimatableProp.SCALE_Y] = it.value to it.interpolation }
        layer.keyframes.opacity.find { it.frame == f }?.let { map[LayerAnimatableProp.OPACITY] = it.value to it.interpolation }
        if (map.isEmpty()) {
            showFeedback(true, "No keyframes at frame $f to copy")
        } else {
            _uiState.update { it.copy(copiedKeyframes = map) }
            showFeedback(false, "Copied ${map.size} keyframe channel(s) from F$f")
        }
    }

    fun pasteKeyframesAtCurrentFrame() {
        val state = _uiState.value
        val layerId = state.selectedLayerId ?: return
        if (state.copiedKeyframes.isEmpty()) {
            showFeedback(true, "Clipboard has no copied keyframes")
            return
        }
        if (dispatchCommand(ProjectCommands.pasteLayerKeyframes(state.activeCompositionId, layerId, state.currentFrame, state.copiedKeyframes))) {
            showFeedback(false, "Pasted keyframes at F${state.currentFrame}")
        }
    }

    fun saveCustomPresetFromModifier(modifier: MotionModifierInstance, name: String) {
        val fps = getActiveCompFps(_uiState.value.project, _uiState.value.activeCompositionId)
        val created = AnimationPresetRegistry.saveCustomPresetFromModifier(modifier, name, fps)
        val list = AnimationPresetRegistry.getCustomPresets()
        _uiState.update { it.copy(customPresets = list) }
        viewModelScope.launch(Dispatchers.IO) { storage.saveCustomPresets(list) }
        showFeedback(false, "Saved custom preset \"${created.name}\"")
    }

    fun deleteCustomPreset(presetId: String) {
        AnimationPresetRegistry.deleteCustomPreset(presetId)
        val list = AnimationPresetRegistry.getCustomPresets()
        _uiState.update { it.copy(customPresets = list) }
        viewModelScope.launch(Dispatchers.IO) { storage.saveCustomPresets(list) }
    }

    fun addEffectModuleToLayer(layerId: String, moduleId: String) {
        val state = _uiState.value
        val comp = state.activeComposition
        val layer = comp.layers.find { it.id == layerId } ?: return
        val fps = getActiveCompFps(state.project, comp.id)
        val instance = EffectsLibraryRegistry.createInstanceFromModule(
            moduleId = moduleId,
            fps = fps,
            instanceId = uid("fx"),
            startFrame = state.currentFrame,
        ) ?: return

        val projected = layer.modifiers + instance
        val validation = EffectBudgetValidator.validateLayerStack(projected, state.adaptivePreview.deviceProfile)
        if (dispatchCommand(ProjectCommands.addLayerModifier(comp.id, layerId, instance))) {
            val warn = validation.warnings.firstOrNull()
            if (warn != null) {
                showFeedback(false, "Added ${instance.label} — $warn")
            } else {
                showFeedback(false, "Added ${instance.label} to ${layer.name}")
            }
        }
    }

    fun applyEffectComboToLayer(layerId: String, combo: EffectComboPreset) {
        val state = _uiState.value
        val comp = state.activeComposition
        val layer = comp.layers.find { it.id == layerId } ?: return
        val fps = getActiveCompFps(state.project, comp.id)
        val instances = EffectsLibraryRegistry.instantiateCombo(
            combo = combo,
            fps = fps,
            startFrame = state.currentFrame,
            idGenerator = { uid("fx") },
        )
        if (instances.isEmpty()) return

        val projected = layer.modifiers + instances
        val validation = EffectBudgetValidator.validateLayerStack(projected, state.adaptivePreview.deviceProfile)
        if (dispatchCommand(ProjectCommands.addLayerModifiersBatch(comp.id, layerId, instances, combo.name))) {
            val warn = validation.warnings.firstOrNull()
            if (warn != null) {
                showFeedback(false, "Applied ${combo.name} — $warn")
            } else {
                showFeedback(false, "Applied combo \"${combo.name}\" (${instances.size} effects)")
            }
        }
    }

    fun saveLayerStackAsEffectCombo(layerId: String, name: String, description: String = "") {
        val state = _uiState.value
        val comp = state.activeComposition
        val layer = comp.layers.find { it.id == layerId } ?: return
        if (layer.modifiers.isEmpty()) {
            showFeedback(true, "Add at least one effect before saving a combo preset")
            return
        }
        val fps = getActiveCompFps(state.project, comp.id)
        val created = EffectsLibraryRegistry.saveCustomComboFromStack(name, description, layer.modifiers, fps)
        val list = EffectsLibraryRegistry.getCustomCombos()
        _uiState.update { it.copy(customEffectCombos = list) }
        viewModelScope.launch(Dispatchers.IO) { storage.saveCustomEffectCombos(list) }
        showFeedback(false, "Saved effect combo \"${created.name}\"")
    }

    fun deleteCustomEffectCombo(comboId: String) {
        EffectsLibraryRegistry.deleteCustomCombo(comboId)
        val list = EffectsLibraryRegistry.getCustomCombos()
        _uiState.update { it.copy(customEffectCombos = list) }
        viewModelScope.launch(Dispatchers.IO) { storage.saveCustomEffectCombos(list) }
    }

    fun importImageUris(
        uris: List<Uri>,
        category: AssetCategory = AssetCategory.PROP,
        replaceAssetId: String? = null,
        onImportedAsset: ((AssetRecord) -> Unit)? = null,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val ctx = getApplication<Application>()
            var importedCount = 0
            for (uri in uris) {
                val displayName = resolveDisplayName(ctx, uri) ?: "Image_${System.currentTimeMillis() % 10000}"
                val ext = displayName.substringAfterLast('.', "png")
                val copied = storage.importMediaFile(uri, ext) ?: continue
                val (file, hash) = copied

                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, bounds)
                val w = bounds.outWidth.takeIf { it > 0 } ?: 512
                val h = bounds.outHeight.takeIf { it > 0 } ?: 512

                if (replaceAssetId != null) {
                    withContext(Dispatchers.Main) {
                        dispatchCommand(ProjectCommands.replaceAsset(replaceAssetId) { a ->
                            a.copy(
                                dataUrl = file.absolutePath,
                                hash = hash,
                                width = w,
                                height = h,
                            )
                        })
                    }
                    importedCount++
                    continue
                }

                val existing = _uiState.value.project.assets.find { it.hash == hash && hash.isNotEmpty() }
                if (existing != null) {
                    withContext(Dispatchers.Main) { onImportedAsset?.invoke(existing) }
                    continue
                }

                val record = AssetRecord(
                    id = uid("asset"),
                    name = displayName.substringBeforeLast('.'),
                    kind = AssetKind.IMAGE,
                    category = category,
                    mimeType = ctx.contentResolver.getType(uri) ?: "image/png",
                    hash = hash,
                    dataUrl = file.absolutePath,
                    width = w,
                    height = h,
                    createdAt = nowIso(),
                )
                withContext(Dispatchers.Main) {
                    dispatchCommand(ProjectCommands.addAsset(record))
                    onImportedAsset?.invoke(record)
                }
                importedCount++
            }
            if (importedCount > 0) {
                withContext(Dispatchers.Main) { showFeedback(false, "Imported $importedCount image asset(s)") }
            }
        }
    }

    fun openBackgroundRemoval(assetId: String) {
        val asset = _uiState.value.project.assets.find { it.id == assetId && it.kind == AssetKind.IMAGE }
        if (asset == null) {
            showFeedback(true, "Select an image sprite asset first")
            return
        }
        _uiState.update { it.copy(bgRemovalAssetId = asset.id, isRemovingBackground = false) }
    }

    fun closeBackgroundRemoval() {
        _uiState.update { it.copy(bgRemovalAssetId = null, isRemovingBackground = false) }
    }

    fun applySpriteBackgroundRemoval(
        assetId: String,
        options: BackgroundRemovalOptions,
        useOriginalSource: Boolean = true,
        saveAsCopy: Boolean = false,
    ) {
        val asset = _uiState.value.project.assets.find { it.id == assetId && it.kind == AssetKind.IMAGE } ?: return
        _uiState.update { it.copy(isRemovingBackground = true) }

        viewModelScope.launch(Dispatchers.Default) {
            val ctx = getApplication<Application>()
            val sourceUrl = if (useOriginalSource && asset.originalDataUrl != null) {
                asset.originalDataUrl
            } else {
                asset.dataUrl
            }

            val sourceBitmap = ProceduralAssetRenderer.getBitmap(ctx, sourceUrl)
            if (sourceBitmap == null) {
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(isRemovingBackground = false) }
                    showFeedback(true, "Could not load sprite bitmap for background removal")
                }
                return@launch
            }

            val (processedBitmap, result) = SpriteBackgroundRemover.removeBackground(sourceBitmap, options)
            val pngBytes = ByteArrayOutputStream().use { baos ->
                processedBitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
                baos.toByteArray()
            }
            val outW = processedBitmap.width
            val outH = processedBitmap.height
            processedBitmap.recycle()

            val saved = withContext(Dispatchers.IO) { storage.saveProcessedPng(pngBytes) }
            if (saved == null) {
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(isRemovingBackground = false) }
                    showFeedback(true, "Failed to save transparent sprite PNG")
                }
                return@launch
            }

            val (file, hash) = saved
            withContext(Dispatchers.Main) {
                if (saveAsCopy) {
                    val copyRecord = AssetRecord(
                        id = uid("asset"),
                        name = "${asset.name} (No BG)",
                        kind = AssetKind.IMAGE,
                        category = asset.category,
                        mimeType = "image/png",
                        hash = hash,
                        dataUrl = file.absolutePath,
                        width = outW,
                        height = outH,
                        createdAt = nowIso(),
                        originalDataUrl = sourceUrl,
                    )
                    dispatchCommand(ProjectCommands.addAsset(copyRecord))
                    showFeedback(
                        false,
                        "Saved transparent copy \"${copyRecord.name}\" (${result.removedPercent.roundToInt()}% removed)",
                    )
                } else {
                    dispatchCommand(
                        ProjectCommands.replaceAsset(asset.id) { a ->
                            a.copy(
                                dataUrl = file.absolutePath,
                                mimeType = "image/png",
                                hash = hash,
                                width = outW,
                                height = outH,
                                originalDataUrl = a.originalDataUrl ?: a.dataUrl,
                            )
                        }
                    )
                    showFeedback(
                        false,
                        "Removed background from \"${asset.name}\" (${result.removedPercent.roundToInt()}% cleared)",
                    )
                }
                _uiState.update { it.copy(bgRemovalAssetId = null, isRemovingBackground = false) }
            }
        }
    }

    fun restoreSpriteOriginalBackground(assetId: String) {
        val asset = _uiState.value.project.assets.find { it.id == assetId } ?: return
        val originalUrl = asset.originalDataUrl ?: return
        dispatchCommand(
            ProjectCommands.replaceAsset(asset.id) { a ->
                a.copy(
                    dataUrl = originalUrl,
                    originalDataUrl = null,
                )
            }
        )
        _uiState.update { it.copy(bgRemovalAssetId = null, isRemovingBackground = false) }
        showFeedback(false, "Restored original image for \"${asset.name}\"")
    }

    fun importAudioUri(uri: Uri, addToTimeline: Boolean = true) {
        viewModelScope.launch(Dispatchers.IO) {
            val ctx = getApplication<Application>()
            val displayName = resolveDisplayName(ctx, uri) ?: "Audio_${System.currentTimeMillis() % 10000}"
            val ext = displayName.substringAfterLast('.', "mp3")
            val copied = storage.importMediaFile(uri, ext)
            if (copied == null) {
                withContext(Dispatchers.Main) { showFeedback(true, "Could not read audio file") }
                return@launch
            }
            val (file, hash) = copied
            val durationSec = runCatching {
                val mmr = MediaMetadataRetriever()
                mmr.setDataSource(file.absolutePath)
                val ms = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 5000L
                mmr.release()
                max(0.5, ms / 1000.0)
            }.getOrDefault(5.0)

            val peaks = List(64) { i ->
                val v = 0.25f + 0.65f * kotlin.math.abs(kotlin.math.sin(i * 0.42 + hash.hashCode())).toFloat()
                v.coerceIn(0.1f, 0.95f)
            }

            val asset = AssetRecord(
                id = uid("asset"),
                name = displayName.substringBeforeLast('.'),
                kind = AssetKind.AUDIO,
                category = AssetCategory.AUDIO,
                mimeType = ctx.contentResolver.getType(uri) ?: "audio/mpeg",
                hash = hash,
                dataUrl = file.absolutePath,
                durationSec = durationSec,
                waveformPeaks = peaks,
                createdAt = nowIso(),
            )

            withContext(Dispatchers.Main) {
                dispatchCommand(ProjectCommands.addAsset(asset))
                if (addToTimeline) {
                    val comp = _uiState.value.activeComposition
                    val fps = getActiveCompFps(_uiState.value.project, comp.id)
                    val durFrames = max(24, (durationSec * fps).roundToInt())
                    val trackId = comp.audioTracks.firstOrNull()?.id ?: run {
                        val newTrack = AudioTrackDocument(uid("atrack"), "Audio Track 1")
                        dispatchCommand(ProjectCommands.addAudioTrack(comp.id, newTrack))
                        newTrack.id
                    }
                    val clip = AudioClipDocument(
                        id = uid("aclip"),
                        assetId = asset.id,
                        name = asset.name,
                        startFrame = _uiState.value.currentFrame,
                        durationFrames = durFrames,
                    )
                    dispatchCommand(ProjectCommands.addAudioClip(comp.id, trackId, clip))
                }
                showFeedback(false, "Imported audio \"${asset.name}\"")
            }
        }
    }

    fun placeAssetInScene(asset: AssetRecord) {
        val comp = _uiState.value.activeComposition
        if (asset.kind == AssetKind.AUDIO) {
            val fps = getActiveCompFps(_uiState.value.project, comp.id)
            val durFrames = max(24, ((asset.durationSec ?: 5.0) * fps).roundToInt())
            val trackId = comp.audioTracks.firstOrNull()?.id ?: run {
                val newTrack = AudioTrackDocument(uid("atrack"), "Audio Track 1")
                dispatchCommand(ProjectCommands.addAudioTrack(comp.id, newTrack))
                newTrack.id
            }
            dispatchCommand(
                ProjectCommands.addAudioClip(
                    comp.id,
                    trackId,
                    AudioClipDocument(uid("aclip"), asset.id, asset.name, _uiState.value.currentFrame, durationFrames = durFrames)
                )
            )
            setWorkspace(WorkspaceTab.COMPOSITION)
            showFeedback(false, "Added audio clip to timeline")
            return
        }

        val w = (asset.width ?: 320).toDouble().coerceIn(80.0, comp.settings.width.toDouble())
        val h = (asset.height ?: 320).toDouble().coerceIn(80.0, comp.settings.height.toDouble())
        val layer = LayerDocument(
            id = uid("layer"),
            name = asset.name,
            role = when (asset.category) {
                AssetCategory.BACKGROUND -> LayerRole.BACKGROUND
                AssetCategory.CHARACTER -> LayerRole.CHARACTER
                AssetCategory.EFFECT -> LayerRole.EFFECT
                else -> LayerRole.PROP
            },
            outFrame = comp.settings.durationFrames,
            transform = Transform2D(comp.settings.width / 2.0, comp.settings.height / 2.0),
            objects = listOf(SceneObjectDocument.SpriteObject(uid("obj"), asset.name, asset.id, w, h)),
        )
        if (dispatchCommand(ProjectCommands.addLayer(comp.id, layer))) {
            selectLayer(layer.id)
            setWorkspace(WorkspaceTab.COMPOSITION)
            showFeedback(false, "Placed \"${asset.name}\" in scene")
        }
    }

    fun placeCharacterInScene(character: CharacterDefinition) {
        val comp = _uiState.value.activeComposition
        val defaultClip = character.clips.find { it.id == character.defaultClipId } ?: character.clips.firstOrNull()
        val layer = LayerDocument(
            id = uid("layer"),
            name = character.name,
            role = LayerRole.CHARACTER,
            outFrame = comp.settings.durationFrames,
            transform = Transform2D(comp.settings.width / 2.0, comp.settings.height * 0.65),
            objects = listOf(
                SceneObjectDocument.CharacterObject(
                    id = uid("obj"),
                    name = character.name,
                    characterId = character.id,
                    activeClipId = defaultClip?.id ?: "",
                    width = 220.0,
                    height = 220.0,
                )
            ),
        )
        if (dispatchCommand(ProjectCommands.addLayer(comp.id, layer))) {
            selectLayer(layer.id)
            setWorkspace(WorkspaceTab.COMPOSITION)
            showFeedback(false, "Placed \"${character.name}\" in scene")
        }
    }

    fun exportMotionXToUri(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val ctx = getApplication<Application>()
            val json = storage.serializeMotionXArchive(_uiState.value.project)
            val res = runCatching {
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                    ?: error("Unable to open output stream")
            }
            withContext(Dispatchers.Main) {
                if (res.isSuccess) showFeedback(false, "Exported .motionx project archive")
                else showFeedback(true, "Export failed: ${res.exceptionOrNull()?.message}")
            }
        }
    }

    fun importMotionXFromUri(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val ctx = getApplication<Application>()
            val text = runCatching {
                ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            if (text == null) {
                withContext(Dispatchers.Main) { showFeedback(true, "Unable to read .motionx file") }
                return@launch
            }
            val parsed = storage.parseMotionXArchive(text)
            withContext(Dispatchers.Main) {
                parsed.onSuccess { proj ->
                    loadProjectObject(proj, switchToEditor = true)
                    showFeedback(false, "Imported \"${proj.name}\"")
                }.onFailure { err ->
                    showFeedback(true, "Invalid .motionx archive: ${err.message}")
                }
            }
        }
    }

    fun exportPngSnapshot(uri: Uri, scope: ExportScope) {
        viewModelScope.launch {
            val s = _uiState.value
            val frame = if (scope == ExportScope.FULL_MOVIE) s.movieFrame else s.currentFrame
            val res = StudioExporter.exportPngSnapshot(getApplication(), s.project, s.activeCompositionId, frame, scope, uri)
            res.onSuccess { showFeedback(false, "Saved PNG snapshot") }
                .onFailure { showFeedback(true, "PNG export failed: ${it.message}") }
        }
    }

    fun startMp4Export(uri: Uri, scope: ExportScope) {
        exportJob?.cancel()
        stopPlayback()
        stopMoviePlayback()
        _uiState.update { it.copy(isExporting = true, exportProgress = ExportProgress(0, 1, "Initializing encoder…")) }
        exportJob = viewModelScope.launch {
            val s = _uiState.value
            val res = StudioExporter.exportMp4Video(
                context = getApplication(),
                project = s.project,
                activeCompId = s.activeCompositionId,
                scope = scope,
                destUri = uri,
                onProgress = { prog -> _uiState.update { it.copy(exportProgress = prog) } },
            )
            _uiState.update { it.copy(isExporting = false, exportProgress = null) }
            res.onSuccess { showFeedback(false, "MP4 video exported successfully") }
                .onFailure { err ->
                    if (err !is kotlinx.coroutines.CancellationException) {
                        showFeedback(true, "MP4 export failed: ${err.message}")
                    }
                }
        }
    }

    fun startGifExport(
        uri: Uri,
        scope: ExportScope,
        gifFps: Int = 15,
        maxDimension: Int = 480,
    ) {
        exportJob?.cancel()
        stopPlayback()
        stopMoviePlayback()
        _uiState.update { it.copy(isExporting = true, exportProgress = ExportProgress(0, 1, "Initializing GIF encoder…")) }
        exportJob = viewModelScope.launch {
            val s = _uiState.value
            val res = StudioExporter.exportGifToUri(
                context = getApplication(),
                project = s.project,
                activeCompId = s.activeCompositionId,
                scope = scope,
                destUri = uri,
                gifFps = gifFps,
                maxDimension = maxDimension,
                onProgress = { prog -> _uiState.update { it.copy(exportProgress = prog) } },
            )
            _uiState.update { it.copy(isExporting = false, exportProgress = null) }
            res.onSuccess { showFeedback(false, "Animated GIF exported successfully") }
                .onFailure { err ->
                    if (err !is kotlinx.coroutines.CancellationException) {
                        showFeedback(true, "GIF export failed: ${err.message}")
                    }
                }
        }
    }

    fun startQuickExport(
        format: ExportFormat,
        scope: ExportScope,
        gifFps: Int = 15,
        gifMaxDimension: Int = 480,
        autoShare: Boolean = true,
    ) {
        exportJob?.cancel()
        stopPlayback()
        stopMoviePlayback()
        val initStage = if (format == ExportFormat.GIF) "Initializing GIF encoder…" else "Initializing MP4 encoder…"
        _uiState.update {
            it.copy(
                isExporting = true,
                exportProgress = ExportProgress(0, 1, initStage),
                lastExportedArtifact = null,
            )
        }
        exportJob = viewModelScope.launch {
            val s = _uiState.value
            val app = getApplication<Application>()
            val outFile = StudioExporter.createShareExportFile(app, s.project.name, format)
            val res = when (format) {
                ExportFormat.GIF -> StudioExporter.exportGifToFile(
                    context = app,
                    project = s.project,
                    activeCompId = s.activeCompositionId,
                    scope = scope,
                    outputFile = outFile,
                    gifFps = gifFps,
                    maxDimension = gifMaxDimension,
                    onProgress = { prog -> _uiState.update { it.copy(exportProgress = prog) } },
                )
                ExportFormat.MP4 -> StudioExporter.exportMp4ToFile(
                    context = app,
                    project = s.project,
                    activeCompId = s.activeCompositionId,
                    scope = scope,
                    outputFile = outFile,
                    onProgress = { prog -> _uiState.update { it.copy(exportProgress = prog) } },
                )
            }
            _uiState.update { it.copy(isExporting = false, exportProgress = null) }
            res.onSuccess { artifact ->
                _uiState.update { it.copy(lastExportedArtifact = artifact) }
                val label = if (format == ExportFormat.GIF) "Animated GIF" else "MP4 video"
                showFeedback(false, "$label compiled and ready to share")
                if (autoShare) {
                    StudioExporter.launchShareIntent(app, artifact).onFailure { err ->
                        showFeedback(true, "Could not open share sheet: ${err.message}")
                    }
                }
            }.onFailure { err ->
                outFile.delete()
                if (err !is kotlinx.coroutines.CancellationException) {
                    showFeedback(true, "Export failed: ${err.message}")
                }
            }
        }
    }

    fun saveArtifactToUri(artifact: ExportedArtifact, destUri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            val res = runCatching {
                app.contentResolver.openOutputStream(destUri)?.use { out ->
                    artifact.file.inputStream().use { inp -> inp.copyTo(out) }
                } ?: error("Cannot open destination file")
            }
            withContext(Dispatchers.Main) {
                res.onSuccess { showFeedback(false, "Saved ${artifact.displayName}") }
                    .onFailure { showFeedback(true, "Save failed: ${it.message}") }
            }
        }
    }

    fun clearLastExportedArtifact() {
        _uiState.update { it.copy(lastExportedArtifact = null) }
    }

    fun cancelExport() {
        exportJob?.cancel()
        exportJob = null
        _uiState.update { it.copy(isExporting = false, exportProgress = null) }
        showFeedback(false, "Export cancelled")
    }

    // Live On-Device MCP HTTP Server controls
    fun getLiveMcpBaseUrl(): String {
        val s = _uiState.value
        val ip = s.mcpServerHostIp.ifBlank { "127.0.0.1" }
        return "http://$ip:${s.mcpServerPort}"
    }

    fun startMcpServer(port: Int = _uiState.value.mcpServerPort) {
        val validPort = port.coerceIn(1024, 65535)
        val ip = EmbeddedMcpHttpServer.detectLocalIpv4()
        mcpHttpServer.start(validPort).onSuccess { boundPort ->
            _uiState.update {
                it.copy(
                    mcpServerRunning = true,
                    mcpServerPort = boundPort,
                    mcpServerHostIp = ip,
                    mcpServerLastError = null,
                    mcpServerRequestCount = 0,
                    mcpServerSessionCount = 0,
                )
            }
            appendMcpLog(
                direction = "server",
                serverName = EmbeddedMcpHttpServer.SERVER_NAME,
                method = "server/start",
                detail = "Listening on http://$ip:$boundPort/mcp (open local Wi-Fi)",
                isError = false,
                durationMs = 0L,
            )
            showFeedback(false, "Live MCP server listening on $ip:$boundPort")
        }.onFailure { err ->
            val msg = err.message ?: "Failed to bind port $validPort"
            _uiState.update {
                it.copy(
                    mcpServerRunning = false,
                    mcpServerHostIp = ip,
                    mcpServerLastError = msg,
                )
            }
            appendMcpLog(
                direction = "server",
                serverName = EmbeddedMcpHttpServer.SERVER_NAME,
                method = "server/start",
                detail = msg,
                isError = true,
                durationMs = 0L,
            )
            showFeedback(true, "Could not start MCP server: $msg")
        }
    }

    fun stopMcpServer() {
        mcpHttpServer.stop()
        _uiState.update {
            it.copy(
                mcpServerRunning = false,
                mcpServerSessionCount = 0,
                mcpServerLastError = null,
            )
        }
        appendMcpLog(
            direction = "server",
            serverName = EmbeddedMcpHttpServer.SERVER_NAME,
            method = "server/stop",
            detail = "Server stopped",
            isError = false,
            durationMs = 0L,
        )
        showFeedback(false, "MCP server stopped")
    }

    fun setMcpServerPort(port: Int) {
        val clamped = port.coerceIn(1024, 65535)
        val wasRunning = _uiState.value.mcpServerRunning
        _uiState.update { it.copy(mcpServerPort = clamped, mcpServerLastError = null) }
        if (wasRunning) {
            startMcpServer(clamped)
        }
    }

    fun refreshMcpNetworkIp() {
        val ip = EmbeddedMcpHttpServer.detectLocalIpv4()
        _uiState.update { it.copy(mcpServerHostIp = ip) }
    }

    private fun createMcpBridge(): McpServerBridge = object : McpServerBridge {
        override suspend fun listTools(): List<McpToolInfo> = LocalMcpEngine.tools

        override suspend fun listResources(): List<McpResourceInfo> =
            LocalMcpEngine.resourcesFor(_uiState.value.project)

        override suspend fun readResource(uri: String): Pair<String, String> {
            val s = _uiState.value
            val mime = if (uri.endsWith(".md")) "text/markdown" else "application/json"
            val text = LocalMcpEngine.readResource(
                project = s.project,
                uri = uri,
                recentExports = s.mcpRecentExports,
                baseUrl = getLiveMcpBaseUrl(),
            )
            return text to mime
        }

        override suspend fun listPrompts(): List<McpPromptInfo> = LocalMcpEngine.prompts

        override suspend fun getPrompt(name: String, arguments: JsonObject): JsonArray? =
            McpCatalog.getPromptMessages(name, arguments)

        override suspend fun callTool(name: String, arguments: JsonObject, baseUrl: String): Pair<String, Boolean> =
            mcpAsyncExecutor.execute(name, arguments, baseUrl)

        override fun resolveExportFile(fileName: String): McpExportedFileEntry? =
            mcpExportRegistry[fileName]

        override fun onIncomingRpc(
            method: String,
            detail: String,
            isError: Boolean,
            durationMs: Long,
            totalRequests: Int,
            activeSessions: Int,
        ) {
            _uiState.update {
                it.copy(
                    mcpServerRequestCount = totalRequests,
                    mcpServerSessionCount = activeSessions,
                )
            }
            appendMcpLog(
                direction = "server",
                serverName = "Antigravity / LAN",
                method = method,
                detail = detail,
                isError = isError,
                durationMs = durationMs,
            )
        }
    }

    // MCP actions
    fun executeLocalMcpTool(toolName: String, argsJsonText: String): Pair<String, Boolean> {
        val t0 = System.currentTimeMillis()
        val argsObj = runCatching {
            val trimmed = argsJsonText.trim()
            if (trimmed.isEmpty()) JsonObject(emptyMap()) else StudioJson.parseToJsonElement(trimmed).jsonObject
        }.getOrElse { err ->
            val msg = "Invalid JSON arguments: ${err.message}"
            appendMcpLog("local", "motion-studio-local", "tools/call:$toolName", msg, true, System.currentTimeMillis() - t0)
            return msg to true
        }

        val outcome = LocalMcpEngine.executeTool(_uiState.value.project, _uiState.value.activeCompositionId, toolName, argsObj)
        if (!outcome.isError && outcome.command != null) {
            dispatchCommand(outcome.command)
            outcome.switchCompId?.let { setActiveComposition(it) }
        }
        appendMcpLog(
            "local",
            "motion-studio-local",
            "tools/call:$toolName",
            if (outcome.isError) outcome.outputJson else "Applied cleanly",
            outcome.isError,
            System.currentTimeMillis() - t0,
        )
        return outcome.outputJson to outcome.isError
    }

    suspend fun executeLocalMcpToolAsync(toolName: String, argsJsonText: String): Pair<String, Boolean> {
        val t0 = System.currentTimeMillis()
        val argsObj = runCatching {
            val trimmed = argsJsonText.trim()
            if (trimmed.isEmpty()) JsonObject(emptyMap()) else StudioJson.parseToJsonElement(trimmed).jsonObject
        }.getOrElse { err ->
            val msg = "Invalid JSON arguments: ${err.message}"
            appendMcpLog("local", "motion-studio-local", "tools/call:$toolName", msg, true, System.currentTimeMillis() - t0)
            return msg to true
        }

        val baseUrl = if (_uiState.value.mcpServerRunning) getLiveMcpBaseUrl() else ""
        val (out, isErr) = mcpAsyncExecutor.execute(toolName, argsObj, baseUrl)
        appendMcpLog(
            "local",
            "motion-studio-local",
            "tools/call:$toolName",
            if (isErr) out.take(160) else "Applied cleanly",
            isErr,
            System.currentTimeMillis() - t0,
        )
        return out to isErr
    }

    fun connectExternalMcpServer(name: String, url: String, token: String) {
        val trimmedUrl = url.trim()
        if (trimmedUrl.isEmpty()) return
        val tempId = uid("mcpsrv")
        val placeholder = ExternalMcpServer(tempId, name.trim().ifEmpty { trimmedUrl }, trimmedUrl, token, status = "connecting")
        _uiState.update { it.copy(externalMcpServers = it.externalMcpServers + placeholder) }

        viewModelScope.launch {
            val t0 = System.currentTimeMillis()
            val res = RemoteMcpClient.connect(trimmedUrl, token)
            res.onSuccess { connected ->
                val finalSrv = connected.copy(id = tempId, name = name.trim().ifEmpty { connected.name })
                _uiState.update { s ->
                    s.copy(externalMcpServers = s.externalMcpServers.map { if (it.id == tempId) finalSrv else it })
                }
                appendMcpLog("client", finalSrv.name, "initialize", "Connected (${finalSrv.tools.size} tools)", false, System.currentTimeMillis() - t0)
            }.onFailure { err ->
                _uiState.update { s ->
                    s.copy(externalMcpServers = s.externalMcpServers.map {
                        if (it.id == tempId) it.copy(status = "error", errorMessage = err.message) else it
                    })
                }
                appendMcpLog("client", placeholder.name, "initialize", err.message ?: "Connection error", true, System.currentTimeMillis() - t0)
            }
        }
    }

    fun removeExternalMcpServer(serverId: String) {
        _uiState.update { s -> s.copy(externalMcpServers = s.externalMcpServers.filterNot { it.id == serverId }) }
    }

    suspend fun invokeExternalMcpTool(server: ExternalMcpServer, toolName: String, argsJsonText: String): Pair<String, Boolean> {
        val t0 = System.currentTimeMillis()
        val argsObj = runCatching {
            val trimmed = argsJsonText.trim()
            if (trimmed.isEmpty()) JsonObject(emptyMap()) else StudioJson.parseToJsonElement(trimmed).jsonObject
        }.getOrElse { return "Invalid JSON: ${it.message}" to true }

        val res = RemoteMcpClient.callTool(server, toolName, argsObj)
        return res.fold(
            onSuccess = { txt ->
                appendMcpLog("client", server.name, "tools/call:$toolName", "Success", false, System.currentTimeMillis() - t0)
                txt to false
            },
            onFailure = { err ->
                val msg = err.message ?: "MCP call failed"
                appendMcpLog("client", server.name, "tools/call:$toolName", msg, true, System.currentTimeMillis() - t0)
                msg to true
            },
        )
    }

    suspend fun readExternalMcpResource(server: ExternalMcpServer, uri: String): Pair<String, Boolean> {
        val t0 = System.currentTimeMillis()
        val res = RemoteMcpClient.readResource(server, uri)
        return res.fold(
            onSuccess = { txt ->
                appendMcpLog("client", server.name, "resources/read", uri, false, System.currentTimeMillis() - t0)
                txt to false
            },
            onFailure = { err ->
                val msg = err.message ?: "Read failed"
                appendMcpLog("client", server.name, "resources/read", msg, true, System.currentTimeMillis() - t0)
                msg to true
            },
        )
    }

    fun applyMcpJsonAsProject(rawJson: String) {
        storage.parseMotionXArchive(rawJson).onSuccess { proj ->
            loadProjectObject(proj, switchToEditor = false)
            showFeedback(false, "Applied MCP JSON to live studio")
        }.onFailure { err ->
            showFeedback(true, "JSON is not a valid MotionProject: ${err.message}")
        }
    }

    fun clearMcpLogs() = _uiState.update { it.copy(mcpLogs = emptyList()) }

    private fun appendMcpLog(
        direction: String,
        serverName: String,
        method: String,
        detail: String,
        isError: Boolean,
        durationMs: Long,
    ) {
        val entry = McpActivityLogEntry(uid("log"), nowIso(), direction, serverName, method, detail, isError, durationMs)
        _uiState.update { it.copy(mcpLogs = (listOf(entry) + it.mcpLogs).take(120)) }
    }

    private fun resolveDisplayName(ctx: Application, uri: Uri): String? = runCatching {
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()

    override fun onCleared() {
        super.onCleared()
        mcpHttpServer.stop()
        stopPlayback()
        stopMoviePlayback()
        audioEngine.dispose()
    }
}
