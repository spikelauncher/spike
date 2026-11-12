package com.cactuslauncher.game;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LauncherPreferencesStoreTest {
    @TempDir Path temp;

    @Test
    void themesRoundTrip() throws Exception {
        var store = new LauncherPreferencesStore(temp.resolve("themes.properties"));
        assertNull(store.load());
        for (String theme : LauncherPreferencesStore.THEMES) {
            for (boolean complete : new boolean[] {false, true}) {
                var preferences = new LauncherPreferencesStore.Preferences(complete, true, 4, 4, true, true, true, true, true, false, false, true,
                        "1920 × 1080", "/games", theme);
                store.save(preferences);
                assertEquals(preferences, store.load());
            }
        }
    }

    /** A test build briefly remembered the open section of "Остальное"; such files must still load. */
    @Test
    void settingsWithRetiredKeysStillLoad() throws Exception {
        Path file = temp.resolve("settings.properties");
        var store = new LauncherPreferencesStore(file);
        store.save(new LauncherPreferencesStore.Preferences(true, true, 4, 4, true, true, true, true, true, false, false, true,
                "1920 × 1080", "/games", "dark"));
        Files.writeString(file, Files.readString(file) + "otherSection=servers\nseasonalTheme=winter\n");
        var loaded = store.load();
        assertEquals("dark", loaded.theme());
        assertEquals(4, loaded.memoryGb());
        store.save(loaded);
        assertFalse(Files.readString(file).contains("otherSection"), "the old key is dropped on the next save");
        assertFalse(Files.readString(file).contains("seasonalTheme"));
        assertFalse(loaded.ghostModeIntroSeen());
    }
}
