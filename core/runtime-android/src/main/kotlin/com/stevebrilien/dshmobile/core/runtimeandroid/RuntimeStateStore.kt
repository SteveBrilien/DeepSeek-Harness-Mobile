package com.stevebrilien.dshmobile.core.runtimeandroid

import android.content.Context
import android.util.AtomicFile
import com.stevebrilien.dshmobile.core.runtimeapi.RuntimeSlot
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets

data class RuntimeLayout(
    val root: File,
    val nativeDir: File,
    val downloadsDir: File,
    val logsDir: File,
    val tmpDir: File,
    val persistentDshHome: File,
    val slotA: File,
    val slotB: File,
    val stateFile: File,
) {
    fun slotRoot(slot: RuntimeSlot): File = when (slot) {
        RuntimeSlot.A -> slotA
        RuntimeSlot.B -> slotB
    }

    fun rootfs(slot: RuntimeSlot): File = File(slotRoot(slot), "rootfs")
    fun slotManifest(slot: RuntimeSlot): File = File(slotRoot(slot), "runtime.json")
}

data class RuntimeDiskState(
    val activeSlot: RuntimeSlot? = null,
    val previousSlot: RuntimeSlot? = null,
    val generation: Long = 0,
)

internal class RuntimeStateStore(context: Context) {
    companion object { private const val SCHEMA = 1 }

    val layout: RuntimeLayout = run {
        val root = File(context.filesDir, "runtime")
        RuntimeLayout(
            root = root,
            nativeDir = File(root, "native"),
            downloadsDir = File(root, "downloads"),
            logsDir = File(root, "logs"),
            tmpDir = File(context.cacheDir, "dsh-runtime"),
            persistentDshHome = File(context.filesDir, "persistent/dsh-home"),
            slotA = File(root, "slots/a"),
            slotB = File(root, "slots/b"),
            stateFile = File(root, "state.json"),
        )
    }

    fun ensureLayout() {
        listOf(
            layout.root,
            layout.nativeDir,
            layout.downloadsDir,
            layout.logsDir,
            layout.tmpDir,
            layout.persistentDshHome,
            layout.slotA,
            layout.slotB,
        ).forEach { check(it.exists() || it.mkdirs()) { "Unable to create ${it.absolutePath}" } }
        if (!layout.stateFile.exists()) write(RuntimeDiskState())
    }

    fun read(): RuntimeDiskState {
        ensureLayout()
        val json = JSONObject(layout.stateFile.readText(StandardCharsets.UTF_8))
        require(json.optInt("schemaVersion", -1) == SCHEMA) { "Unsupported runtime state schema" }
        return RuntimeDiskState(
            activeSlot = json.optString("activeSlot").takeIf { it.isNotBlank() }?.let(RuntimeSlot::valueOf),
            previousSlot = json.optString("previousSlot").takeIf { it.isNotBlank() }?.let(RuntimeSlot::valueOf),
            generation = json.optLong("generation", 0),
        )
    }

    fun write(state: RuntimeDiskState) {
        layout.stateFile.parentFile?.let { check(it.exists() || it.mkdirs()) }
        val json = JSONObject()
            .put("schemaVersion", SCHEMA)
            .put("generation", state.generation)
            .put("activeSlot", state.activeSlot?.name ?: "")
            .put("previousSlot", state.previousSlot?.name ?: "")
        val atomic = AtomicFile(layout.stateFile)
        val output = atomic.startWrite()
        try {
            output.write(json.toString(2).toByteArray(StandardCharsets.UTF_8))
            atomic.finishWrite(output)
        } catch (t: Throwable) {
            atomic.failWrite(output)
            throw t
        }
    }

    fun activate(slot: RuntimeSlot) {
        val current = read()
        write(
            current.copy(
                activeSlot = slot,
                previousSlot = current.activeSlot?.takeIf { it != slot },
                generation = current.generation + 1,
            ),
        )
    }

    fun rollbackTarget(): RuntimeSlot? = read().previousSlot
}
