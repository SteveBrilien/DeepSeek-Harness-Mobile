package com.stevebrilien.dshmobile.core.recovery

import java.io.File
import java.nio.file.Files

/** Non-destructive preflight for the native file browser. Never expand access. */
internal object FileOperationSafety {
    fun requireNotProtectedRoot(source: File, browserRoot: File, recoveryRoot: File) {
        val safe = source.canonicalFile
        check(safe != browserRoot.canonicalFile) { "Cannot modify the file browser root." }
        check(safe != recoveryRoot.canonicalFile) { "Cannot modify the recovery vault root." }
        check(safe != File(recoveryRoot, ".Trash").canonicalFile) { "Cannot modify the recovery Trash root." }
    }

    fun requireDestinationOutsideSource(source: File, destinationDirectory: File) {
        if (!source.isDirectory) return
        val root = source.canonicalFile.toPath()
        check(!destinationDirectory.canonicalFile.toPath().startsWith(root)) {
            "Cannot copy or move a directory into itself or a descendant."
        }
    }

    /** A link can traverse outside the granted root, or loop back to an ancestor. */
    fun requireNoRecursiveSymlinks(source: File, authorizedRoot: File) {
        val granted = authorizedRoot.canonicalFile.toPath()
        val pending = ArrayDeque<File>()
        pending.add(source)
        while (pending.isNotEmpty()) {
            val file = pending.removeLast()
            check(!Files.isSymbolicLink(file.toPath())) { "Recursive file operation cannot follow symbolic links." }
            check(file.canonicalFile.toPath().startsWith(granted)) { "File path escapes authorized root." }
            if (file.isDirectory) {
                val children = file.listFiles() ?: error("Unable to inspect directory before file operation.")
                children.forEach(pending::add)
            } else {
                check(file.isFile) { "Unsupported file type in recursive operation." }
            }
        }
    }
}
