package com.stevebrilien.dshmobile.core.runtimeandroid

/** Result returned by the managed PRoot/Linux execution domain. */
data class RuntimeShellResult(
    val command: String,
    val workingDirectory: String,
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val elapsedMillis: Long,
    val timedOut: Boolean,
)
