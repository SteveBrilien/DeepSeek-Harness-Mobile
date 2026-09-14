package com.stevebrilien.dshmobile.core.recovery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.nio.file.Files

class RecoveryBackupManagerTest {
    @Test
    fun logicalArchivePathsDoNotCollapseProotLink2SymlinkBackings() {
        val root = Files.createTempDirectory("dsh-recovery-l2s").toFile()
        try {
            val backing = File(root, ".l2s.backing").apply { writeText("payload") }
            val first = File(root, "logical-a")
            val second = File(root, "logical-b")
            Files.createSymbolicLink(first.toPath(), backing.toPath().fileName)
            Files.createSymbolicLink(second.toPath(), backing.toPath().fileName)

            assertEquals(backing.canonicalFile, first.canonicalFile)
            assertEquals(backing.canonicalFile, second.canonicalFile)
            assertEquals("logical-a", backupLogicalRelativePath(first, root))
            assertEquals("logical-b", backupLogicalRelativePath(second, root))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun persistentBackupSkipsPnpmStoreAndLink2SymlinkImplementationFiles() {
        assertFalse(shouldIncludePersistentDshBackupPath(".local/share/pnpm/store/v11/files/ca/blob"))
        assertFalse(shouldIncludePersistentDshBackupPath("profiles/web/.l2s.deadbeef"))
        assertTrue(shouldIncludePersistentDshBackupPath("profiles/web/package.json"))
        assertTrue(shouldIncludePersistentDshBackupPath("sessions/example.jsonl"))
    }

    @Test
    fun entryRegistryDeduplicatesSameSourceButRejectsRealPathCollision() {
        val registry = BackupEntryRegistry()
        assertTrue(registry.register("AppPrivate/dsh-home/a", "/source/a"))
        assertFalse(registry.register("AppPrivate/dsh-home/a", "/source/a"))
        try {
            registry.register("AppPrivate/dsh-home/a", "/different/source")
            fail("expected collision")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message.orEmpty().contains("Backup entry collision"))
        }
    }
}
