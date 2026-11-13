package com.cactuslauncher.game;

import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public final class GameMonitor implements Runnable {
    static final int LOW_FPS = 30;
    private static final String AGENT_RESOURCE = "fps-agent.jar";
    private static final String STATUS_FILE = "spike-fps.status";
    static final String MEMORY_HINT_FILE = ".spike-memory-hint";
    private static final long GIB = 1024L * 1024L * 1024L;
    private static final Set<String> NEVER_LOWER = Set.of("explorer.exe", "dwm.exe", "audiodg.exe", "csrss.exe",
            "svchost.exe", "powershell.exe", "conhost.exe", "obs64.exe", "obs32.exe", "discord.exe", "java.exe",
            "javaw.exe", "taskmgr.exe", "searchhost.exe", "startmenuexperiencehost.exe", "shellexperiencehost.exe");

    record Sample(int fps, long heapUsed, long heapMax, long gcMillis, long uptimeMillis) {}

    private final Process game;
    private final Path root;
    private final Path status;
    private final Set<String> applied = new HashSet<>();
    private WindowsHelper helper;

    private GameMonitor(Process game, Path root, Path status) {
        this.game = game;
        this.root = root;
        this.status = status;
    }

    public static List<String> agentArguments(Path root) {
        try {
            Path jar = agentJar();
            if (jar == null) return List.of();
            Path status = root.resolve("logs").resolve(STATUS_FILE).toAbsolutePath().normalize();
            Files.createDirectories(status.getParent());
            Files.deleteIfExists(status);
            return List.of("-javaagent:" + jar + "=" + status);
        } catch (IOException | RuntimeException e) {
            return List.of();
        }
    }

    public static Path statusFileFrom(List<String> command) {
        for (String argument : command) {
            if (!argument.startsWith("-javaagent:")) continue;
            int name = argument.indexOf("fps-agent");
            if (name < 0) continue;
            int separator = argument.indexOf(".jar=", name);
            if (separator < 0) continue;
            String status = argument.substring(separator + ".jar=".length());
            if (!status.isBlank()) return Path.of(status);
        }
        return null;
    }

    public static void start(Process game, Path root, Path status) {
        if (game == null || status == null) return;
        Thread thread = new Thread(new GameMonitor(game, root, status), "spike-game-monitor");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.start();
    }

    public static int memoryHintGb(Path root) {
        try {
            Path file = root.resolve(MEMORY_HINT_FILE);
            if (!Files.isRegularFile(file)) return 0;
            int value = Integer.parseInt(Files.readString(file, StandardCharsets.UTF_8).trim());
            return value >= 2 && value <= 16 ? value : 0;
        } catch (IOException | RuntimeException e) {
            return 0;
        }
    }

    @Override
    public void run() {
        long pid = game.pid();
        Deque<Sample> window = new ArrayDeque<>();
        boolean background = false;
        boolean backgroundLogged = false;
        int awayStreak = 0;
        long lastDiagnosis = 0;
        try {
            helper = WindowsHelper.start();
            while (!game.waitFor(2, TimeUnit.SECONDS)) {
                boolean[] state = helper == null ? null : helper.windowState(pid);
                boolean away = state != null && (state[0] || !state[1]);
                awayStreak = away ? awayStreak + 1 : 0;
                if (!background && awayStreak >= 2) {
                    background = true;
                    enterBackground(pid);
                    if (!backgroundLogged) {
                        backgroundLogged = true;
                        log("Игра свёрнута или не в фокусе: снизил её приоритет и включил экономный режим, "
                                + "чтобы остальные программы не тормозили. Вернётся, когда откроешь игру.");
                    }
                } else if (background && !away) {
                    background = false;
                    enterForeground(pid);
                }
                Sample sample = readFresh(status);
                if (sample == null || background) { window.clear(); continue; }
                window.addLast(sample);
                while (window.size() > 6) window.removeFirst();
                if (sample.uptimeMillis() < 60_000 || window.size() < 5) continue;
                int fps = medianFps(window);
                boolean low = fps >= 0 ? fps < LOW_FPS : memoryStarved(window);
                long now = System.currentTimeMillis();
                if (low && now - lastDiagnosis > 90_000) {
                    lastDiagnosis = now;
                    diagnose(pid, List.copyOf(window), fps);
                    window.clear();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException ignored) {
        } finally {
            if (helper != null) helper.close();
            SystemBoost.restoreIdle();
            try { Files.deleteIfExists(status); } catch (IOException ignored) { }
        }
    }

    private void enterBackground(long pid) {
        if (helper == null) return;
        helper.priority(pid, "BelowNormal");
        helper.efficiencyMode(pid, true);
        SystemBoost.restoreLowered(helper);
    }

    private void enterForeground(long pid) {
        if (helper == null) return;
        helper.efficiencyMode(pid, false);
        helper.priority(pid, "AboveNormal");
    }

    private void diagnose(long pid, List<Sample> window, int fps) {
        var causes = new ArrayList<String>();
        Sample last = window.get(window.size() - 1);
        if (memoryStarved(window) && last.heapMax() > 0) {
            int current = (int) Math.round(last.heapMax() / (double) GIB);
            int wanted = Math.min(16, current + 2);
            if (wanted > memoryHintGb(root) && wanted > current) {
                writeMemoryHint(wanted);
                causes.add("Java не хватает памяти: занято " + last.heapUsed() / (1024 * 1024) + " из "
                        + last.heapMax() / (1024 * 1024) + " МБ, сборка мусора съедает " + Math.round(gcShare(window) * 100)
                        + "% времени. Со следующего запуска сборке будет выделено " + wanted + " ГБ (если память стоит на «Авто»).");
            }
        }
        if (!applied.contains("power") && SystemBoost.leavePowerSaver(pid)) {
            applied.add("power");
            causes.add("Была включена схема питания «Экономия энергии»: до конца игры переключил на «Сбалансированную».");
        }
        double cpu = systemCpuLoad();
        if (cpu > 0.85 && helper != null) {
            List<String> lowered = lowerHogs(pid);
            if (!lowered.isEmpty()) {
                causes.add("Процессор загружен на " + Math.round(cpu * 100) + "%: понизил приоритет фоновых программ ("
                        + String.join(", ", lowered) + ") до конца игры или пока игра свёрнута.");
            } else {
                causes.add("Процессор загружен на " + Math.round(cpu * 100) + "%, в основном самой игрой: упирается в процессор "
                        + "(дальность прорисовки, много мобов или модов).");
            }
        }
        int gpu = helper == null ? -1 : helper.gpuPercent(pid);
        if (gpu >= 90) {
            causes.add("Видеокарта загружена на " + gpu + "%: игра упирается в графику. Помогут меньшая дальность прорисовки, "
                    + "отключение шейдеров или мод Sodium.");
        }
        if (causes.isEmpty()) {
            causes.add("Процессор, память и видеокарта не на пределе: вероятно, тормозит сама сцена (редстоун, мобы) или мод.");
        }
        log((fps >= 0 ? "FPS упал до " + fps + ". " : "Игра подтормаживает. ") + String.join(" ", causes));
    }

    private List<String> lowerHogs(long gamePid) {
        String user = ProcessHandle.current().info().user().orElse("");
        if (user.isEmpty()) return List.of();
        long self = ProcessHandle.current().pid();
        Map<Long, Duration> before = new HashMap<>();
        ProcessHandle.allProcesses().forEach(handle -> {
            if (handle.pid() == gamePid || handle.pid() == self) return;
            var info = handle.info();
            if (!user.equals(info.user().orElse(""))) return;
            info.totalCpuDuration().ifPresent(cpu -> before.put(handle.pid(), cpu));
        });
        long sampleMillis = 1500;
        try { Thread.sleep(sampleMillis); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); return List.of(); }
        Map<String, List<ProcessHandle>> handles = new HashMap<>();
        Map<String, Long> cpuMillis = new HashMap<>();
        for (var entry : before.entrySet()) {
            ProcessHandle.of(entry.getKey()).ifPresent(handle -> {
                var info = handle.info();
                String name = info.command().map(command -> Path.of(command).getFileName().toString().toLowerCase(Locale.ROOT)).orElse("");
                if (name.isEmpty() || NEVER_LOWER.contains(name)) return;
                long delta = info.totalCpuDuration().map(cpu -> cpu.minus(entry.getValue()).toMillis()).orElse(0L);
                if (delta <= 0) return;
                cpuMillis.merge(name, delta, Long::sum);
                handles.computeIfAbsent(name, key -> new ArrayList<>()).add(handle);
            });
        }
        int cores = Math.max(1, Runtime.getRuntime().availableProcessors());
        var lowered = new ArrayList<String>();
        for (var entry : cpuMillis.entrySet()) {
            double share = entry.getValue() / (double) (sampleMillis * cores);
            if (share < 0.10) continue;
            boolean any = false;
            for (ProcessHandle handle : handles.get(entry.getKey())) any |= SystemBoost.lowerPriority(handle, gamePid, helper);
            if (any) lowered.add(entry.getKey());
        }
        return lowered;
    }

    private void writeMemoryHint(int gb) {
        try { Files.writeString(root.resolve(MEMORY_HINT_FILE), Integer.toString(gb), StandardCharsets.UTF_8); }
        catch (IOException ignored) { }
    }

    private void log(String message) {
        try {
            Path file = root.resolve("logs").resolve("cactus-launch.log");
            String line = "[" + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "] [Spike FPS] " + message + System.lineSeparator();
            Files.writeString(file, line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
        }
    }

    private static double systemCpuLoad() {
        try {
            var bean = (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
            bean.getCpuLoad();
            Thread.sleep(500);
            return bean.getCpuLoad();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return -1;
        } catch (RuntimeException e) {
            return -1;
        }
    }

    static Sample parseStatus(String text) {
        if (text == null) return null;
        String[] parts = text.trim().split("\\s+");
        if (parts.length != 6 || !parts[5].equals("end")) return null;
        try {
            return new Sample(Integer.parseInt(parts[0]), Long.parseLong(parts[1]), Long.parseLong(parts[2]),
                    Long.parseLong(parts[3]), Long.parseLong(parts[4]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static int medianFps(Iterable<Sample> window) {
        var values = new ArrayList<Integer>();
        for (Sample sample : window) if (sample.fps() >= 0) values.add(sample.fps());
        if (values.isEmpty()) return -1;
        values.sort(Integer::compare);
        return values.get(values.size() / 2);
    }

    static double gcShare(List<Sample> window) {
        if (window.size() < 2) return 0;
        Sample first = window.get(0), last = window.get(window.size() - 1);
        long wall = last.uptimeMillis() - first.uptimeMillis();
        if (wall <= 0) return 0;
        return Math.max(0, Math.min(1, (last.gcMillis() - first.gcMillis()) / (double) wall));
    }

    static boolean memoryStarved(Iterable<Sample> window) {
        var list = new ArrayList<Sample>();
        long minUsed = Long.MAX_VALUE;
        long max = 0;
        for (Sample sample : window) {
            list.add(sample);
            minUsed = Math.min(minUsed, sample.heapUsed());
            max = Math.max(max, sample.heapMax());
        }
        if (list.size() < 2 || max <= 0) return false;
        return minUsed / (double) max > 0.85 && gcShare(list) > 0.08;
    }

    private static Sample readFresh(Path status) {
        try {
            if (!Files.isRegularFile(status)) return null;
            if (System.currentTimeMillis() - Files.getLastModifiedTime(status).toMillis() > 5000) return null;
            return parseStatus(Files.readString(status, StandardCharsets.US_ASCII));
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static Path agentJar() throws IOException {
        Path folder = ProfileStore.defaultFile().resolveSibling("fps-agent");
        try (InputStream input = GameMonitor.class.getResourceAsStream(AGENT_RESOURCE)) {
            if (input == null) return null;
            byte[] bytes = input.readAllBytes();
            String hash = Integer.toHexString(java.util.Arrays.hashCode(bytes));
            Path jar = folder.resolve("fps-agent-" + hash + ".jar").toAbsolutePath().normalize();
            if (Files.isRegularFile(jar) && Files.size(jar) == bytes.length) return jar;
            Files.createDirectories(folder);
            Path temp = Files.createTempFile(folder, "fps-agent", ".tmp");
            try {
                Files.write(temp, bytes);
                Files.move(temp, jar, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temp);
            }
            return jar;
        }
    }
}
