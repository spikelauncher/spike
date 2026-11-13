package com.cactuslauncher

import com.cactuslauncher.ui.microsoftProfileLabel
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class LauncherStartsTest {
    @Test fun counterPersistsAcrossStartsAndLabelChangesOnlyOnMultiplesOfTen() {
        val directory = Files.createTempDirectory("launcher-starts")
        try {
            val file = directory.resolve("counter")
            for (launch in 1L..31L) {
                assertEquals(launch, recordLauncherStart(file))
                assertEquals(if (launch % 10L == 0L) "Microslop" else "Microsoft", microsoftProfileLabel(launch))
            }
            assertEquals("31", Files.readString(file))
            assertEquals("Microsoft", microsoftProfileLabel(0))
        } finally { directory.toFile().deleteRecursively() }
    }
}
