package com.example.storage

import android.content.Context
import android.net.Uri
import com.example.commands.nowIso
import com.example.effects.EffectComboPreset
import com.example.engine.MotionPresetDefinition
import com.example.schema.MotionProject
import com.example.schema.MotionXArchive
import com.example.schema.StudioJson
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.io.File
import java.security.MessageDigest

data class SavedProjectSummary(
    val id: String,
    val name: String,
    val updatedAt: String,
    val compositionCount: Int,
    val width: Int = 1280,
    val height: Int = 720,
    val fps: Int = 24,
    val durationFrames: Int = 240,
    val layerCount: Int = 0,
    val previewProject: MotionProject? = null,
)

class ProjectStorage(private val context: Context) {
    private val rootDir: File get() = File(context.filesDir, "motion_studio").apply { mkdirs() }
    private val projectsDir: File get() = File(rootDir, "projects").apply { mkdirs() }
    private val mediaDir: File get() = File(rootDir, "media").apply { mkdirs() }
    private val lastIdFile: File get() = File(rootDir, "last_opened_id.txt")
    private val customPresetsFile: File get() = File(rootDir, "custom_presets.json")
    private val customEffectCombosFile: File get() = File(rootDir, "custom_effect_combos.json")

    fun saveProject(project: MotionProject) {
        val target = File(projectsDir, "${sanitizeFileName(project.id)}.json")
        val tmp = File(projectsDir, "${sanitizeFileName(project.id)}.json.tmp")
        tmp.writeText(StudioJson.encodeToString(project))
        if (target.exists()) target.delete()
        tmp.renameTo(target)
        lastIdFile.writeText(project.id)
    }

    fun loadProject(id: String): MotionProject? {
        val file = File(projectsDir, "${sanitizeFileName(id)}.json")
        if (!file.exists()) return null
        return runCatching {
            StudioJson.decodeFromString<MotionProject>(file.readText())
        }.getOrNull()
    }

    fun getLastOpenedProjectId(): String? = runCatching {
        if (lastIdFile.exists()) lastIdFile.readText().trim().ifEmpty { null } else null
    }.getOrNull()

    fun listSavedProjects(): List<SavedProjectSummary> {
        val files = projectsDir.listFiles { f -> f.isFile && f.name.endsWith(".json") } ?: return emptyList()
        return files.mapNotNull { file ->
            runCatching {
                val p = StudioJson.decodeFromString<MotionProject>(file.readText())
                val firstComp = p.compositions.firstOrNull()
                val resolvedFps = com.example.schema.fpsToDouble(firstComp?.settings?.fpsOverride ?: p.fps)
                    .toInt()
                    .coerceAtLeast(1)
                SavedProjectSummary(
                    id = p.id,
                    name = p.name,
                    updatedAt = p.updatedAt,
                    compositionCount = p.compositions.size,
                    width = firstComp?.settings?.width ?: 1280,
                    height = firstComp?.settings?.height ?: 720,
                    fps = resolvedFps,
                    durationFrames = firstComp?.settings?.durationFrames ?: 240,
                    layerCount = firstComp?.layers?.size ?: 0,
                    previewProject = p,
                )
            }.getOrNull()
        }.sortedByDescending { it.updatedAt }
    }

    fun deleteProject(id: String) {
        File(projectsDir, "${sanitizeFileName(id)}.json").delete()
    }

    fun saveCustomPresets(presets: List<MotionPresetDefinition>) {
        runCatching {
            customPresetsFile.writeText(StudioJson.encodeToString(presets))
        }
    }

    fun loadCustomPresets(): List<MotionPresetDefinition> = runCatching {
        if (!customPresetsFile.exists()) return emptyList()
        StudioJson.decodeFromString<List<MotionPresetDefinition>>(customPresetsFile.readText())
    }.getOrDefault(emptyList())

    fun saveCustomEffectCombos(combos: List<EffectComboPreset>) {
        runCatching {
            customEffectCombosFile.writeText(StudioJson.encodeToString(combos))
        }
    }

    fun loadCustomEffectCombos(): List<EffectComboPreset> = runCatching {
        if (!customEffectCombosFile.exists()) return emptyList()
        StudioJson.decodeFromString<List<EffectComboPreset>>(customEffectCombosFile.readText())
    }.getOrDefault(emptyList())

    /**
     * Copies an imported media URI (from Photo Picker or SAF) into app-private storage
     * and returns its stable local file path plus SHA-256 content hash for deduplication.
     */
    fun importMediaFile(uri: Uri, extension: String): Pair<File, String>? = runCatching {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        val hash = digest.joinToString("") { "%02x".format(it) }
        val cleanExt = extension.lowercase().replace(Regex("[^a-z0-9]"), "").ifEmpty { "bin" }
        val outFile = File(mediaDir, "${hash.take(24)}.$cleanExt")
        if (!outFile.exists()) {
            outFile.writeBytes(bytes)
        }
        outFile to hash
    }.getOrNull()

    /**
     * Persists a processed transparent PNG byte array (e.g., background-removed sprite) into
     * app-private media storage with SHA-256 deduplication.
     */
    fun saveProcessedPng(pngBytes: ByteArray): Pair<File, String>? = runCatching {
        val digest = MessageDigest.getInstance("SHA-256").digest(pngBytes)
        val hash = digest.joinToString("") { "%02x".format(it) }
        val outFile = File(mediaDir, "cutout_${hash.take(24)}.png")
        if (!outFile.exists()) {
            outFile.writeBytes(pngBytes)
        }
        outFile to hash
    }.getOrNull()

    fun serializeMotionXArchive(project: MotionProject): String {
        val archive = MotionXArchive(
            format = "motionx-project-archive",
            archiveVersion = 1,
            exportedAt = nowIso(),
            project = project,
        )
        return StudioJson.encodeToString(archive)
    }

    fun parseMotionXArchive(jsonText: String): Result<MotionProject> = runCatching {
        val trimmed = jsonText.trim()
        runCatching {
            StudioJson.decodeFromString<MotionXArchive>(trimmed).project
        }.getOrElse {
            StudioJson.decodeFromString<MotionProject>(trimmed)
        }
    }

    private fun sanitizeFileName(raw: String): String =
        raw.replace(Regex("[^A-Za-z0-9_.-]"), "_")
}
