package com.cactuslauncher.game;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * Quiet, reversible system tweaks for the game process (Windows only):
 * <ul>
 *   <li>the game's Java runs on the high-performance (discrete) GPU on hybrid-graphics laptops;</li>
 *   <li>the game process gets "above normal" CPU priority.</li>
 * </ul>
 * The GPU choice is a per-user Windows setting, so the previous value is journaled before it is changed and put
 * back once no process of that Java is running any more: when the game exits, or on the next launcher start if
 * the launcher was closed first. The priority belongs to the process and disappears with it.
 * Nothing here may break a launch: every failure is swallowed.
 */
public final class SystemBoost {
    static final String GPU_KEY = "HKCU\\Software\\Microsoft\\DirectX\\UserGpuPreferences";
    private static final String HIGH_PERFORMANCE = "GpuPreference=2";
    private static volatile boolean enabled = true;
    private static final Object LOCK = new Object();

    private SystemBoost() {}

    public static void setEnabled(boolean value) {
        enabled = value;
        if (!value) restoreIdle();
    }

    public static boolean enabled() { return enabled; }

    /** Call right before the game process starts: the GPU choice is read when the process opens its device. */
    public static void beforeLaunch(Path javaExecutable) {
        if (!enabled || !windows() || javaExecutable == null) return;
        try {
            synchronized (LOCK) {
                String exe = javaExecutable.toAbsolutePath().normalize().toString();
                Path journal = journal(exe);
                if (!Files.isRegularFile(journal)) {
                    String original = queryGpuPreference(exe);
                    if (UNREADABLE.equals(original)) return;
                    String current = original == null ? "" : original;
                    // Respect an explicit user choice (power saving or already high performance).
                    if (gpuChoice(current) != 0) return;
                    var values = new Properties();
                    values.setProperty("exe", exe);
                    values.setProperty("absent", Boolean.toString(original == null));
                    values.setProperty("original", current);
                    Files.createDirectories(journal.getParent());
                    try (var output = Files.newOutputStream(journal)) { values.store(output, null); }
                    if (!run("reg", "add", GPU_KEY, "/v", exe, "/t", "REG_SZ", "/d", withHighPerformance(current), "/f")) {
                        Files.deleteIfExists(journal);
                    }
                }
            }
        } catch (IOException | RuntimeException ignored) {
        }
    }

    /**
     * Call once the game is running. [game] is null when another process (Ghost Mode) owns it; the game is then
     * found by its Java executable.
     */
    public static void afterLaunch(Path javaExecutable, Process game) {
        if (!windows() || javaExecutable == null) return;
        String exe = javaExecutable.toAbsolutePath().normalize().toString();
        Thread worker = new Thread(() -> {
            if (enabled) {
                List<Long> ids = new ArrayList<>();
                if (game != null) ids.add(game.pid());
                else ProcessHandle.allProcesses().filter(handle -> runs(handle, exe)).forEach(handle -> ids.add(handle.pid()));
                raisePriority(ids);
            }
            if (game != null) {
                try { game.onExit().get(); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
                catch (Exception ignored) { }
                restoreIdle();
            }
        }, "cactus-system-boost");
        worker.setDaemon(true);
        worker.start();
    }

    /** Puts back every GPU choice whose Java is no longer running. Safe to call at any time. */
    public static void restoreIdle() {
        if (!windows()) return;
        try {
            synchronized (LOCK) {
                Path folder = folder();
                if (!Files.isDirectory(folder)) return;
                List<Path> journals = new ArrayList<>();
                try (DirectoryStream<Path> files = Files.newDirectoryStream(folder, "gpu-*.properties")) {
                    for (Path file : files) journals.add(file);
                }
                for (Path file : journals) {
                    var values = new Properties();
                    try (var input = Files.newInputStream(file)) { values.load(input); }
                    String exe = values.getProperty("exe", "");
                    if (exe.isBlank()) { Files.deleteIfExists(file); continue; }
                    if (enabled && ProcessHandle.allProcesses().anyMatch(handle -> runs(handle, exe))) continue;
                    boolean restored = Boolean.parseBoolean(values.getProperty("absent", "true"))
                            ? run("reg", "delete", GPU_KEY, "/v", exe, "/f") || queryGpuPreference(exe) == null
                            : run("reg", "add", GPU_KEY, "/v", exe, "/t", "REG_SZ", "/d", values.getProperty("original", ""), "/f");
                    if (restored) Files.deleteIfExists(file);
                }
            }
        } catch (IOException | RuntimeException ignored) {
        }
    }

    /** 0 = let Windows decide, 1 = power saving, 2 = high performance. */
    static int gpuChoice(String data) {
        for (String part : data.split(";")) {
            String item = part.trim();
            if (item.startsWith("GpuPreference=")) {
                try { return Integer.parseInt(item.substring("GpuPreference=".length()).trim()); }
                catch (NumberFormatException e) { return 0; }
            }
        }
        return 0;
    }

    /** Keeps the other per-app DirectX options and replaces only the GPU choice. */
    static String withHighPerformance(String data) {
        var result = new StringBuilder();
        for (String part : data.split(";")) {
            String item = part.trim();
            if (item.isEmpty() || item.startsWith("GpuPreference=")) continue;
            result.append(item).append(';');
        }
        return result.append(HIGH_PERFORMANCE).append(';').toString();
    }

    private static final java.util.regex.Pattern REG_SZ_LINE = java.util.regex.Pattern.compile("\\s{2,}REG_SZ\\s{2,}(.*)$");

    /**
     * Parses the data out of `reg query /v` output (one value). The value name is not compared: reg prints it in
     * the console code page, which garbles non-ASCII user folders.
     */
    static String parseRegQuery(String output) {
        if (output == null) return null;
        for (String line : output.split("\\R")) {
            var match = REG_SZ_LINE.matcher(line);
            if (match.find()) return match.group(1).trim();
        }
        return null;
    }

    private static final String UNREADABLE = "\u0000";

    /** The current data, [UNREADABLE] for a value that exists but cannot be parsed, null when it does not exist. */
    private static String queryGpuPreference(String exe) {
        String output = output("reg", "query", GPU_KEY, "/v", exe);
        if (output == null) return null;
        String data = parseRegQuery(output);
        return data == null ? UNREADABLE : data;
    }

    private static void raisePriority(List<Long> ids) {
        if (ids.isEmpty()) return;
        var script = new StringBuilder();
        for (long id : ids) {
            script.append("$p=Get-Process -Id ").append(id).append(" -ErrorAction SilentlyContinue;")
                    .append("if($p -and $p.PriorityClass -eq 'Normal'){$p.PriorityClass='AboveNormal'};");
        }
        run("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", script.toString());
    }

    private static boolean runs(ProcessHandle handle, String exe) {
        return handle.info().command().map(command -> command.equalsIgnoreCase(exe)).orElse(false);
    }

    private static Path folder() {
        return ProfileStore.defaultFile().resolveSibling("system-boost");
    }

    private static Path journal(String exe) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(exe.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
            return folder().resolve("gpu-" + HexFormat.of().formatHex(digest, 0, 12) + ".properties");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean windows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private static boolean run(String... command) {
        return output(command) != null;
    }

    /** Output of a successful command, or null on failure or timeout. */
    private static String output(String... command) {
        Process process = null;
        try {
            process = new ProcessBuilder(command).redirectErrorStream(true).start();
            byte[] bytes = process.getInputStream().readNBytes(64 * 1024);
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return null;
            }
            return process.exitValue() == 0 ? new String(bytes, StandardCharsets.UTF_8) : null;
        } catch (IOException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (process != null) process.destroyForcibly();
            return null;
        }
    }
}
