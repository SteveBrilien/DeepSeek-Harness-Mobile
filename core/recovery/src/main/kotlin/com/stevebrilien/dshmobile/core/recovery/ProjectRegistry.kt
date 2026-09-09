package com.stevebrilien.dshmobile.core.recovery

import android.util.AtomicFile
import com.stevebrilien.dshmobile.core.model.Project
import com.stevebrilien.dshmobile.core.model.ProjectId
import com.stevebrilien.dshmobile.core.model.RuntimeId
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.UUID

class ProjectRegistry(
    private val vault: RecoveryVault,
) {
    companion object {
        private const val SCHEMA_VERSION = 1
        private const val REGISTRY_RELATIVE_PATH = "Recovery/Config/projects/registry.json"
    }

    private val registryFile: File
        get() = File(vault.status().root, REGISTRY_RELATIVE_PATH)

    fun list(): Result<List<Project>> = runCatching {
        ensureRegistry()
        val root = readRoot()
        val projects = root.optJSONArray("projects") ?: JSONArray()
        buildList {
            for (index in 0 until projects.length()) {
                val item = projects.optJSONObject(index) ?: continue
                parseProject(item)?.let(::add)
            }
        }.sortedBy { it.displayName.lowercase() }
    }

    fun get(id: ProjectId): Result<Project?> = runCatching {
        list().getOrThrow().firstOrNull { it.id == id }
    }

    fun createDefault(
        displayName: String,
        description: String? = null,
    ): Result<Project> = runCatching {
        val safeName = displayName.trim().also {
            require(it.isNotEmpty()) { "Project name cannot be empty" }
            require(it != "." && it != "..") { "Invalid project name" }
            require(!it.contains('/') && !it.contains('\\')) { "Project name cannot contain path separators" }
        }
        val status = vault.ensureLayout().getOrThrow()
        val directory = File(status.root, "Projects/$safeName")
        check(directory.exists() || directory.mkdirs()) { "Unable to create ${directory.absolutePath}" }
        registerFolder(directory, safeName, description).getOrThrow()
    }

    fun registerFolder(
        folder: File,
        displayName: String = folder.name,
        description: String? = null,
        gitRemote: String? = null,
        runtimeId: RuntimeId? = null,
    ): Result<Project> = runCatching {
        ensureRegistry()
        val canonical = folder.canonicalFile
        require(canonical.exists()) { "Project folder does not exist: ${canonical.absolutePath}" }
        require(canonical.isDirectory) { "Project path is not a directory: ${canonical.absolutePath}" }
        val name = displayName.trim().ifBlank { canonical.name }

        val root = readRoot()
        val projects = root.optJSONArray("projects") ?: JSONArray().also { root.put("projects", it) }
        for (index in 0 until projects.length()) {
            val item = projects.optJSONObject(index) ?: continue
            if (item.optString("path") == canonical.absolutePath) {
                val existing = parseProject(item) ?: continue
                return@runCatching existing
            }
        }

        val project = Project(
            id = ProjectId(UUID.randomUUID().toString()),
            displayName = name,
            path = canonical.absolutePath,
            description = description,
            gitRemote = gitRemote,
            runtimeId = runtimeId,
        )
        projects.put(toJson(project))
        root.put("updatedAtEpochMillis", System.currentTimeMillis())
        writeRoot(root)
        project
    }

    fun update(project: Project): Result<Project> = runCatching {
        ensureRegistry()
        val root = readRoot()
        val projects = root.optJSONArray("projects") ?: JSONArray().also { root.put("projects", it) }
        var replaced = false
        for (index in 0 until projects.length()) {
            val item = projects.optJSONObject(index) ?: continue
            if (item.optString("id") == project.id.value) {
                projects.put(index, toJson(project.copy(path = File(project.path).canonicalPath)))
                replaced = true
                break
            }
        }
        check(replaced) { "Unknown project: ${project.id.value}" }
        root.put("updatedAtEpochMillis", System.currentTimeMillis())
        writeRoot(root)
        project
    }

    fun unregister(id: ProjectId): Result<Boolean> = runCatching {
        ensureRegistry()
        val root = readRoot()
        val projects = root.optJSONArray("projects") ?: return@runCatching false
        val retained = JSONArray()
        var removed = false
        for (index in 0 until projects.length()) {
            val item = projects.optJSONObject(index) ?: continue
            if (item.optString("id") == id.value) {
                removed = true
            } else {
                retained.put(item)
            }
        }
        if (removed) {
            root.put("projects", retained)
            root.put("updatedAtEpochMillis", System.currentTimeMillis())
            writeRoot(root)
        }
        removed
    }

    private fun ensureRegistry() {
        vault.ensureLayout().getOrThrow()
        if (registryFile.exists()) return
        val root = JSONObject()
            .put("schemaVersion", SCHEMA_VERSION)
            .put("updatedAtEpochMillis", System.currentTimeMillis())
            .put("projects", JSONArray())
        writeRoot(root)
    }

    private fun readRoot(): JSONObject {
        val root = JSONObject(registryFile.readText(StandardCharsets.UTF_8))
        val schema = root.optInt("schemaVersion", -1)
        require(schema == SCHEMA_VERSION) { "Unsupported Project Registry schema: $schema" }
        return root
    }

    private fun writeRoot(root: JSONObject) {
        registryFile.parentFile?.let { check(it.exists() || it.mkdirs()) }
        val atomic = AtomicFile(registryFile)
        val output = atomic.startWrite()
        try {
            output.write(root.toString(2).toByteArray(StandardCharsets.UTF_8))
            atomic.finishWrite(output)
        } catch (t: Throwable) {
            atomic.failWrite(output)
            throw t
        }
    }

    private fun toJson(project: Project): JSONObject = JSONObject()
        .put("id", project.id.value)
        .put("displayName", project.displayName)
        .put("path", project.path)
        .apply {
            project.description?.let { put("description", it) }
            project.gitRemote?.let { put("gitRemote", it) }
            project.runtimeId?.let { put("runtimeId", it.value) }
        }

    private fun parseProject(json: JSONObject): Project? {
        val id = json.optString("id").takeIf { it.isNotBlank() } ?: return null
        val name = json.optString("displayName").takeIf { it.isNotBlank() } ?: return null
        val path = json.optString("path").takeIf { it.isNotBlank() } ?: return null
        return Project(
            id = ProjectId(id),
            displayName = name,
            path = path,
            description = json.optString("description").takeIf { it.isNotBlank() },
            gitRemote = json.optString("gitRemote").takeIf { it.isNotBlank() },
            runtimeId = json.optString("runtimeId").takeIf { it.isNotBlank() }?.let(::RuntimeId),
        )
    }
}
