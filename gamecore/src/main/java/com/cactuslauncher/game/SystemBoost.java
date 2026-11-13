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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SystemBoost {
    static final String GPU_KEY = "HKCU\\Software\\Microsoft\\DirectX\\UserGpuPreferences";
    static final String POWER_SAVER = "a1841308-3542-4a6e-8a7b-9c71d5c9b0e4";
    static final String BALANCED = "381b4222-f694-41f0-9685-ff5bb260df2e";
    private static final String HIGH_PERFORMANCE = "GpuPreference=2";
    private static final String UNREADABLE = "\u0000";
    private static final Pattern REG_SZ_LINE = Pattern.compile("\\s{2,}REG_SZ\\s{2,}(.*)$");
    private static final Pattern GUID = Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static volatile boolean enabled = true;
    private static final Object LOCK = new Object();

    private SystemBoost() {}

    public static void setEnabled(boolean value) {
        enabled = value;
        if (!value) restoreIdle();
    }

    public static boolean enabled() { return enabled; }

    public static void beforeLaunch(Path javaExecutable) {
        if (!enabled || !windows() || javaExecutable == null) return;
        try {
            synchronized (LOCK) {
                String exe = javaExecutable.toAbsolutePath().normalize().toString();
                Path journal = gpuJournal(exe);
                if (Files.isRegularFile(journal)) return;
                String original = queryGpuPreference(exe);
                if (UNREADABLE.equals(original)) return;
                String current = original == null ? "" : original;
                if (gpuChoice(current) != 0) return;
                var values = new Properties();
                values.setProperty("exe", exe);
                values.setProperty("absent", Boolean.toString(original == null));
                values.setProperty("original", current);
                store(journal, values);
                if (!run("reg", "add", GPU_KEY, "/v", exe, "/t", "REG_SZ", "/d", withHighPerformance(current), "/f")) {
                    Files.deleteIfExists(journal);
                }
            }
        } catch (IOException | RuntimeException ignored) {
        }
    }

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
        }, "spike-system-boost");
        worker.setDaemon(true);
        worker.start();
    }

    static boolean leavePowerSaver(long gamePid) {
        if (!windows()) return false;
        try {
            synchronized (LOCK) {
                Path journal = folder().resolve("power.properties");
                if (Files.isRegularFile(journal)) return false;
                String active = activePowerScheme();
                if (!POWER_SAVER.equalsIgnoreCase(active)) return false;
                var values = new Properties();
                values.setProperty("original", active);
                values.setProperty("applied", BALANCED);
                putProcess(values, "game", gamePid);
                store(journal, values);
                if (run("powercfg", "/setactive", BALANCED)) return true;
                Files.deleteIfExists(journal);
            }
        } catch (IOException | RuntimeException ignored) {
        }
        return false;
    }

    static boolean lowerPriority(ProcessHandle target, long gamePid, WindowsHelper helper) {
        if (helper == null || target == null) return false;
        try {
            synchronized (LOCK) {
                long pid = target.pid();
                if (!"Normal".equals(helper.priority(pid, ""))) return false;
                Path journal = folder().resolve("prio-" + pid + ".properties");
                var values = new Properties();
                putProcess(values, "target", pid);
                putProcess(values, "game", gamePid);
                store(journal, values);
                if (helper.priority(pid, "BelowNormal") != null) return true;
                Files.deleteIfExists(journal);
            }
        } catch (IOException | RuntimeException ignored) {
        }
        return false;
    }

    static void restoreLowered(WindowsHelper helper) {
        if (!windows()) return;
        try {
            synchronized (LOCK) {
                for (Path file : journals("prio-*.properties")) restorePriority(file, helper);
            }
        } catch (IOException | RuntimeException ignored) {
        }
    }

    public static void restoreIdle() {
        if (!windows()) return;
        try {
            synchronized (LOCK) {
                for (Path file : journals("gpu-*.properties")) {
                    var values = load(file);
                    String exe = values.getProperty("exe", "");
                    if (exe.isBlank()) { Files.deleteIfExists(file); continue; }
                    if (enabled && ProcessHandle.allProcesses().anyMatch(handle -> runs(handle, exe))) continue;
                    boolean restored = Boolean.parseBoolean(values.getProperty("absent", "true"))
                            ? run("reg", "delete", GPU_KEY, "/v", exe, "/f") || queryGpuPreference(exe) == null
                            : run("reg", "add", GPU_KEY, "/v", exe, "/t", "REG_SZ", "/d", values.getProperty("original", ""), "/f");
                    if (restored) Files.deleteIfExists(file);
                }
                for (Path file : journals("prio-*.properties")) {
                    if (alive(load(file), "game")) continue;
                    restorePriority(file, null);
                }
                Path power = folder().resolve("power.properties");
                if (Files.isRegularFile(power)) {
                    var values = load(power);
                    if (!alive(values, "game")) {
                        String applied = values.getProperty("applied", "");
                        String original = values.getProperty("original", "");
                        boolean untouched = applied.equalsIgnoreCase(activePowerScheme());
                        if (!untouched || original.isBlank() || run("powercfg", "/setactive", original)) Files.deleteIfExists(power);
                    }
                }
            }
        } catch (IOException | RuntimeException ignored) {
        }
    }

    static boolean windows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

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

    static String withHighPerformance(String data) {
        var result = new StringBuilder();
        for (String part : data.split(";")) {
            String item = part.trim();
            if (item.isEmpty() || item.startsWith("GpuPreference=")) continue;
            result.append(item).append(';');
        }
        return result.append(HIGH_PERFORMANCE).append(';').toString();
    }

    static String parseRegQuery(String output) {
        if (output == null) return null;
        for (String line : output.split("\\R")) {
            var match = REG_SZ_LINE.matcher(line);
            if (match.find()) return match.group(1).trim();
        }
        return null;
    }

    static String parseGuid(String output) {
        if (output == null) return null;
        Matcher match = GUID.matcher(output);
        return match.find() ? match.group().toLowerCase(Locale.ROOT) : null;
    }

    private static String activePowerScheme() {
        return parseGuid(output("powercfg", "/getactivescheme"));
    }

    private static void restorePriority(Path file, WindowsHelper helper) throws IOException {
        var values = load(file);
        if (alive(values, "target")) {
            long pid = Long.parseLong(values.getProperty("target.pid"));
            if (helper != null) {
                if ("BelowNormal".equals(helper.priority(pid, ""))) helper.priority(pid, "Normal");
            } else {
                run("powershell.exe", "-NoProfile", "-NonInteractive", "-Command",
                        "$p=Get-Process -Id " + pid + " -ErrorAction SilentlyContinue;"
                                + "if($p -and $p.PriorityClass -eq 'BelowNormal'){$p.PriorityClass='Normal'}");
            }
        }
        Files.deleteIfExists(file);
    }

    private static void putProcess(Properties values, String prefix, long pid) {
        values.setProperty(prefix + ".pid", Long.toString(pid));
        values.setProperty(prefix + ".start", ProcessHandle.of(pid)
                .flatMap(handle -> handle.info().startInstant()).map(instant -> Long.toString(instant.toEpochMilli())).orElse(""));
    }

    private static boolean alive(Properties values, String prefix) {
        try {
            long pid = Long.parseLong(values.getProperty(prefix + ".pid", ""));
            String start = values.getProperty(prefix + ".start", "");
            return ProcessHandle.of(pid).filter(ProcessHandle::isAlive).map(handle -> start.isEmpty()
                    || handle.info().startInstant().map(instant -> Long.toString(instant.toEpochMilli()).equals(start)).orElse(true))
                    .orElse(false);
        } catch (NumberFormatException e) {
            return false;
        }
    }

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

    private static List<Path> journals(String glob) throws IOException {
        Path folder = folder();
        List<Path> files = new ArrayList<>();
        if (!Files.isDirectory(folder)) return files;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(folder, glob)) {
            for (Path file : stream) files.add(file);
        }
        return files;
    }

    private static Properties load(Path file) throws IOException {
        var values = new Properties();
        try (var input = Files.newInputStream(file)) { values.load(input); }
        return values;
    }

    private static void store(Path file, Properties values) throws IOException {
        Files.createDirectories(file.getParent());
        try (var output = Files.newOutputStream(file)) { values.store(output, null); }
    }

    private static Path gpuJournal(String exe) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(exe.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
            return folder().resolve("gpu-" + HexFormat.of().formatHex(digest, 0, 12) + ".properties");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean run(String... command) {
        return output(command) != null;
    }

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
