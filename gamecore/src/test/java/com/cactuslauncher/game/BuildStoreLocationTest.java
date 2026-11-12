package com.cactuslauncher.game;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BuildStoreLocationTest {
    @TempDir Path temp;

    @Test
    void selectedFolderSurvivesReopenEditsAndChangingTheFolderForNewBuilds() throws Exception {
        Path registry = temp.resolve("config/builds.properties");
        Path firstDisk = temp.resolve("disk one/Minecraft");
        var store = new BuildStore(registry);
        var first = store.create("First", "1.21.1", "Forge", firstDisk).getFirst();
        Path firstFolder = firstDisk.resolve("instances").resolve(first.id());
        assertEquals(firstFolder, store.folder(first.id()));
        Files.createDirectories(firstFolder.resolve("saves/world"));
        Files.writeString(firstFolder.resolve("saves/world/level.dat"), "saved world");

        store = new BuildStore(registry);
        store.rename(first.id(), "Renamed");
        store.setIcon(first.id(), "diamond");
        store.setAutoUpdateMods(first.id(), true);
        store.markLaunched(first.id());
        Path secondDisk = temp.resolve("disk two");
        var second = store.create("Second", "1.21.1", "Fabric", secondDisk).getLast();
        var reopened = new BuildStore(registry);
        assertEquals(firstFolder, reopened.folder(first.id()));
        assertEquals("saved world", Files.readString(reopened.folder(first.id()).resolve("saves/world/level.dat")));
        assertTrue(reopened.lastLaunched(first.id()) > 0);
        assertEquals(secondDisk.resolve("instances").resolve(second.id()), reopened.folder(second.id()));
        assertFalse(Files.exists(registry.getParent().resolve("instances")));
        reopened.delete(second.id(), true);
        assertFalse(Files.exists(secondDisk.resolve("instances").resolve(second.id())));
        reopened.delete(first.id());
        assertTrue(Files.exists(firstFolder.resolve("saves/world/level.dat")), "Keep-files deletion must preserve the original world");
    }

    @Test
    void legacyBuildsStayAccessibleAfterAddingABuildOnAnotherDisk() throws Exception {
        Path registry = temp.resolve("config/builds.properties");
        var store = new BuildStore(registry);
        var legacy = store.create("Legacy", "1.21.1", "Vanilla").getFirst();
        Path original = store.folder(legacy.id());
        Files.writeString(original.resolve("options.txt"), "original options");
        store.create("New", "1.21.1", "Vanilla", temp.resolve("chosen"));
        var reopened = new BuildStore(registry);
        assertEquals(original, reopened.folder(legacy.id()));
        assertEquals("original options", Files.readString(original.resolve("options.txt")));
        reopened.delete(legacy.id(), true);
        assertFalse(Files.exists(original));
        assertEquals(1, reopened.load().size());
    }

    @Test
    void rejectsRelativeRecordedLocationWithoutOverwritingTheRegistry() throws Exception {
        Path registry = temp.resolve("builds.properties");
        var store = new BuildStore(registry);
        var build = store.create("Build", "1.21.1", "Vanilla").getFirst();
        var properties = new Properties();
        try (var in = Files.newInputStream(registry)) { properties.load(in); }
        properties.setProperty("build." + build.id() + ".instancesDirectory", "../elsewhere");
        try (var out = Files.newOutputStream(registry)) { properties.store(out, null); }
        byte[] before = Files.readAllBytes(registry);
        assertThrows(java.io.IOException.class, store::load);
        assertThrows(java.io.IOException.class, () -> store.create("Other", "1.21.1", "Vanilla"));
        assertArrayEquals(before, Files.readAllBytes(registry));
    }

    @Test
    void refusesSymlinkedInstancesWithoutDeletingTheirContents() throws Exception {
        Path chosen = Files.createDirectory(temp.resolve("chosen"));
        Path outside = Files.createDirectory(temp.resolve("outside"));
        Files.writeString(outside.resolve("keep.txt"), "keep");
        Files.createSymbolicLink(chosen.resolve("instances"), outside);
        var store = new BuildStore(temp.resolve("config/builds.properties"));
        assertThrows(java.io.IOException.class, () -> store.create("Build", "1.21.1", "Vanilla", chosen));
        assertTrue(store.load().isEmpty());
        assertEquals("keep", Files.readString(outside.resolve("keep.txt")));
    }
}
