package com.cactuslauncher.game;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SystemBoostTest {
    @Test
    void readsGpuChoice() {
        assertEquals(0, SystemBoost.gpuChoice(""));
        assertEquals(1, SystemBoost.gpuChoice("GpuPreference=1;"));
        assertEquals(2, SystemBoost.gpuChoice("SwapEffectUpgradeEnable=1;GpuPreference=2;"));
        assertEquals(0, SystemBoost.gpuChoice("GpuPreference=x;"));
    }

    @Test
    void replacesOnlyGpuChoice() {
        assertEquals("GpuPreference=2;", SystemBoost.withHighPerformance(""));
        assertEquals("SwapEffectUpgradeEnable=1;GpuPreference=2;",
                SystemBoost.withHighPerformance("GpuPreference=0;SwapEffectUpgradeEnable=1;"));
    }

    @Test
    void parsesRegQueryOutput() {
        String output = "\r\nHKEY_CURRENT_USER\\Software\\Microsoft\\DirectX\\UserGpuPreferences\r\n"
                + "    C:\\Users\\??????\\runtimes\\java-21\\bin\\java.exe    REG_SZ    GpuPreference=1;\r\n\r\n";
        assertEquals("GpuPreference=1;", SystemBoost.parseRegQuery(output));
        assertNull(SystemBoost.parseRegQuery("ERROR: nothing"));
    }

    @Test
    void parsesPowerSchemeGuid() {
        assertEquals(SystemBoost.POWER_SAVER, SystemBoost.parseGuid(
                "Идентификатор схемы питания: A1841308-3542-4A6E-8A7B-9C71D5C9B0E4  (Экономия энергии)"));
        assertNull(SystemBoost.parseGuid("nothing here"));
    }

    @Test
    void savedPreferenceDefaultsToOn() throws Exception {
        var store = new LauncherPreferencesStore(java.nio.file.Files.createTempDirectory("boost").resolve("p.properties"));
        store.save(new LauncherPreferencesStore.Preferences(true, true, 4, 4, true, true, true, true, true, false, false, true,
                "1920 × 1080", "/games", "dark"));
        assertTrue(store.load().systemBoost());
    }
}
