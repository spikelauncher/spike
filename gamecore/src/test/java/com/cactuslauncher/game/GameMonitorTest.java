package com.cactuslauncher.game;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GameMonitorTest {
    private static final long MB = 1024L * 1024L;
    @TempDir Path temp;

    @Test
    void parsesAgentStatusLine() {
        var sample = GameMonitor.parseStatus("57 104857600 4294967296 1200 65000 end\n");
        assertEquals(new GameMonitor.Sample(57, 104857600L, 4294967296L, 1200, 65000), sample);
        assertNull(GameMonitor.parseStatus("57 104857600 4294967296 1200"));
        assertNull(GameMonitor.parseStatus("x 1 2 3 4 end"));
        assertNull(GameMonitor.parseStatus(null));
    }

    @Test
    void medianIgnoresUnknownFps() {
        assertEquals(20, GameMonitor.medianFps(List.of(sample(10, 0, 0), sample(20, 0, 0), sample(90, 0, 0))));
        assertEquals(-1, GameMonitor.medianFps(List.of(sample(-1, 0, 0), sample(-1, 0, 0))));
    }

    @Test
    void detectsMemoryStarvation() {
        var starved = List.of(timed(3900, 0, 0), timed(3950, 400, 2), timed(3920, 900, 4));
        assertTrue(GameMonitor.memoryStarved(starved));
        var roomy = List.of(timed(1500, 0, 0), timed(2500, 400, 2), timed(1800, 900, 4));
        assertFalse(GameMonitor.memoryStarved(roomy));
        var fullButCalm = List.of(timed(3900, 0, 0), timed(3950, 10, 2), timed(3920, 20, 4));
        assertFalse(GameMonitor.memoryStarved(fullButCalm));
    }

    private static GameMonitor.Sample timed(long usedMb, long gcMillis, int second) {
        return new GameMonitor.Sample(20, usedMb * MB, 4096 * MB, gcMillis, 60_000 + second * 1000L);
    }

    @Test
    void findsStatusFileInCommand() {
        var command = List.of("java", "-Xmx4G", "-javaagent:C:\\data\\fps-agent\\fps-agent-1a.jar=C:\\games\\b 1\\logs\\spike-fps.status",
                "net.minecraft.client.main.Main");
        assertEquals(Path.of("C:\\games\\b 1\\logs\\spike-fps.status"), GameMonitor.statusFileFrom(command));
        assertNull(GameMonitor.statusFileFrom(List.of("java", "-javaagent:authlib-injector.jar=https://x", "Main")));
    }

    @Test
    void readsMemoryHint() throws Exception {
        assertEquals(0, GameMonitor.memoryHintGb(temp));
        Files.writeString(temp.resolve(GameMonitor.MEMORY_HINT_FILE), "6");
        assertEquals(6, GameMonitor.memoryHintGb(temp));
        Files.writeString(temp.resolve(GameMonitor.MEMORY_HINT_FILE), "99");
        assertEquals(0, GameMonitor.memoryHintGb(temp));
    }

    @Test
    void agentJarIsBundledForJava8() throws Exception {
        try (var resource = GameMonitor.class.getResourceAsStream("fps-agent.jar")) {
            assertNotNull(resource);
            try (var jar = new JarInputStream(resource)) {
                assertEquals("com.cactuslauncher.fpsagent.FpsAgent", jar.getManifest().getMainAttributes().getValue("Premain-Class"));
                boolean found = false;
                for (var entry = jar.getNextJarEntry(); entry != null; entry = jar.getNextJarEntry()) {
                    if (!entry.getName().equals("com/cactuslauncher/fpsagent/FpsAgent.class")) continue;
                    byte[] header = jar.readNBytes(8);
                    assertEquals(52, ((header[6] & 0xff) << 8) | (header[7] & 0xff));
                    found = true;
                }
                assertTrue(found);
            }
        }
    }

    private static GameMonitor.Sample sample(int fps, long usedMb, long gcMillis) {
        return new GameMonitor.Sample(fps, usedMb * MB, 4096 * MB, gcMillis, 60_000);
    }
}
