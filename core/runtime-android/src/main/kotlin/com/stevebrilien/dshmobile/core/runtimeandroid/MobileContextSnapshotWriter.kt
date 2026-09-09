package com.stevebrilien.dshmobile.core.runtimeandroid

import android.os.Build
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets

internal class MobileContextSnapshotWriter(
    private val stateStore: RuntimeStateStore,
) {
    companion object {
        private const val SCHEMA_VERSION = 1
        private const val CONTEXT_VERSION = "mobile-context-v2"
    }

    fun writeStableBootSnapshot(): File {
        stateStore.ensureLayout()
        val file = File(stateStore.layout.persistentDshHome, "mobile/context.json")
        file.parentFile?.let { check(it.exists() || it.mkdirs()) }
        val json = JSONObject()
            .put("schemaVersion", SCHEMA_VERSION)
            .put("contextVersion", CONTEXT_VERSION)
            .put("androidVersion", Build.VERSION.RELEASE.orEmpty())
            .put("manufacturer", Build.MANUFACTURER.orEmpty())
            .put("deviceModel", Build.MODEL.orEmpty())
            .put("dshVersion", RuntimePins.DSH_VERSION)

        val atomic = AtomicFile(file)
        val output = atomic.startWrite()
        try {
            output.write(json.toString(2).toByteArray(StandardCharsets.UTF_8))
            atomic.finishWrite(output)
        } catch (t: Throwable) {
            atomic.failWrite(output)
            throw t
        }
        return file
    }
}
