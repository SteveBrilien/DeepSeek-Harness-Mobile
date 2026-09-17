package com.stevebrilien.dshmobile.core.recovery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.nio.file.Files

class FileOperationSafetyTest {
    private fun rejects(message: String, action: () -> Unit) {
        try { action(); fail("expected rejection") }
        catch (expected: IllegalStateException) { assertTrue(expected.message.orEmpty().contains(message)) }
    }

    @Test fun copyingDirectoryToSelfOrChildFailsBeforeWritingAnything() {
        val root = Files.createTempDirectory("file-guard-copy").toFile()
        try {
            val source = File(root, "source").apply { mkdirs() }
            val child = File(source, "child").apply { mkdirs() }
            File(source, "file.txt").writeText("original")
            rejects("itself or a descendant") { FileOperationSafety.requireDestinationOutsideSource(source, source) }
            rejects("itself or a descendant") { FileOperationSafety.requireDestinationOutsideSource(source, child) }
            assertEquals(listOf("child", "file.txt"), source.listFiles().orEmpty().map { it.name }.sorted())
            FileOperationSafety.requireDestinationOutsideSource(source, root)
            FileOperationSafety.requireDestinationOutsideSource(File(source, "file.txt"), child)
        } finally { root.deleteRecursively() }
    }

    @Test fun rootsCannotBeDeletedOrMoved() {
        val parent = Files.createTempDirectory("file-guard-roots").toFile()
        try {
            val recovery = File(parent, "vault").apply { mkdirs() }
            val trash = File(recovery, ".Trash").apply { mkdirs() }
            val child = File(recovery, "notes").apply { mkdirs() }
            rejects("browser root") { FileOperationSafety.requireNotProtectedRoot(parent, parent, recovery) }
            rejects("vault root") { FileOperationSafety.requireNotProtectedRoot(recovery, parent, recovery) }
            rejects("Trash root") { FileOperationSafety.requireNotProtectedRoot(trash, parent, recovery) }
            FileOperationSafety.requireNotProtectedRoot(child, parent, recovery)
        } finally { parent.deleteRecursively() }
    }

    @Test fun recursiveCopyCannotTraverseSymlinkOutsideOrLoop() {
        val root = Files.createTempDirectory("file-guard-links").toFile()
        val outside = Files.createTempDirectory("file-guard-outside").toFile()
        try {
            val source = File(root, "source").apply { mkdirs() }
            File(source, "safe.txt").writeText("safe")
            FileOperationSafety.requireNoRecursiveSymlinks(source, root)
            Files.createSymbolicLink(File(source, "escape").toPath(), outside.toPath())
            rejects("symbolic links") { FileOperationSafety.requireNoRecursiveSymlinks(source, root) }
            Files.delete(File(source, "escape").toPath())
            Files.createSymbolicLink(File(source, "loop").toPath(), source.toPath())
            rejects("symbolic links") { FileOperationSafety.requireNoRecursiveSymlinks(source, root) }
        } finally { root.deleteRecursively(); outside.deleteRecursively() }
    }
}
