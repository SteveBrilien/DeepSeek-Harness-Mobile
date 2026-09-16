package com.stevebrilien.dshmobile.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalRoutingTest {
    @Test fun privilegedCommandsDoNotFallBackToAppUid() {
        for (command in listOf("pm list packages", " am start", "dumpsys battery", "settings get global", "input tap 1 1", "cmd package", "svc wifi", "/system/bin/pm list packages")) {
            assertTrue("expected ADB identity for $command", requiresAdbShell(command))
        }
    }
    @Test fun benignCommandsDoNotRequireAdbShell() {
        for (command in listOf("ls", "getprop ro.build.version.sdk", "pwd", "cat file", "printf hi", "/system/bin/getprop ro.product.model", "command -v pm")) {
            assertFalse("unexpected ADB classification for $command", requiresAdbShell(command))
        }
    }
}
