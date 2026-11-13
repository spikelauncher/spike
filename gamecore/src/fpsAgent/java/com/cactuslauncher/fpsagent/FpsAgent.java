package com.cactuslauncher.fpsagent;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.instrument.Instrumentation;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class FpsAgent implements Runnable {
    private static final Pattern FPS = Pattern.compile("^(\\d{1,5}) fps");
    private final Instrumentation instrumentation;
    private final File status;
    private final File cache;
    private Object game;
    private Field fpsText;
    private Field selfField;
    private long nextSearch;
    private int searches;
    private String[] cached;
    private int cachedTries;
    private boolean cachedClassSeen;
    private boolean cacheFailed;

    private FpsAgent(Instrumentation instrumentation, File status) {
        this.instrumentation = instrumentation;
        this.status = status;
        this.cache = new File(status.getParentFile(), "spike-fps.cache");
    }

    public static void premain(String argument, Instrumentation instrumentation) {
        try {
            if (argument == null || argument.trim().isEmpty()) return;
            Thread thread = new Thread(new FpsAgent(instrumentation, new File(argument.trim())), "spike-fps-agent");
            thread.setDaemon(true);
            thread.setPriority(Thread.MIN_PRIORITY);
            thread.start();
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void run() {
        try {
            Thread.sleep(3000);
            while (true) {
                try { tick(); } catch (Throwable ignored) { }
                Thread.sleep(1000);
            }
        } catch (Throwable ignored) {
        }
    }

    private void tick() throws Exception {
        long now = System.currentTimeMillis();
        if (fpsText == null && now >= nextSearch) {
            if (useCache()) {
                findCached();
                cachedTries++;
                nextSearch = now + 1000;
            } else {
                search();
                searches++;
                nextSearch = now + (searches < 40 ? 3000 : 60000);
                if (fpsText != null) saveCache();
            }
        }
        int fps = -1;
        if (fpsText != null) {
            Object value = fpsText.get(game);
            if (value instanceof String) {
                Matcher match = FPS.matcher((String) value);
                if (match.find()) fps = Integer.parseInt(match.group(1));
            }
        }
        MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        long gcMillis = 0;
        for (GarbageCollectorMXBean collector : ManagementFactory.getGarbageCollectorMXBeans()) {
            long time = collector.getCollectionTime();
            if (time > 0) gcMillis += time;
        }
        long uptime = ManagementFactory.getRuntimeMXBean().getUptime();
        String line = fps + " " + heap.getUsed() + " " + heap.getMax() + " " + gcMillis + " " + uptime + " end\n";
        FileOutputStream output = new FileOutputStream(status, false);
        try { output.write(line.getBytes(Charset.forName("US-ASCII"))); }
        finally { output.close(); }
    }

    private boolean useCache() {
        if (cached == null) {
            cached = new String[0];
            try {
                if (cache.isFile() && cache.length() < 4096) {
                    byte[] bytes = new byte[(int) cache.length()];
                    FileInputStream input = new FileInputStream(cache);
                    try {
                        int read = 0;
                        while (read < bytes.length) {
                            int count = input.read(bytes, read, bytes.length - read);
                            if (count < 0) break;
                            read += count;
                        }
                    } finally {
                        input.close();
                    }
                    String[] parts = new String(bytes, Charset.forName("UTF-8")).trim().split(" ");
                    if (parts.length == 3) cached = parts;
                }
            } catch (Throwable ignored) {
            }
        }
        return cached.length == 3 && !cacheFailed && cachedTries < (cachedClassSeen ? 15 : 90);
    }

    private void findCached() {
        Class<?> type = null;
        for (Class<?> loaded : instrumentation.getAllLoadedClasses()) {
            if (loaded.getName().equals(cached[0])) { type = loaded; break; }
        }
        if (type == null) return;
        if (!cachedClassSeen) {
            cachedClassSeen = true;
            cachedTries = 0;
        }
        try {
            Field self = type.getDeclaredField(cached[1]);
            Field text = type.getDeclaredField(cached[2]);
            if (!Modifier.isStatic(self.getModifiers()) || self.getType() != type || text.getType() != String.class) {
                cacheFailed = true;
                return;
            }
            if (!open(type, self)) return;
            Object instance = self.get(null);
            if (instance == null) return;
            text.setAccessible(true);
            Object value = text.get(instance);
            if (value instanceof String && FPS.matcher((String) value).find()) {
                game = instance;
                fpsText = text;
            }
        } catch (NoSuchFieldException missing) {
            cacheFailed = true;
        } catch (Throwable ignored) {
        }
    }

    private void saveCache() {
        try {
            String line = fpsText.getDeclaringClass().getName() + " " + selfField.getName() + " " + fpsText.getName() + "\n";
            FileOutputStream output = new FileOutputStream(cache, false);
            try { output.write(line.getBytes(Charset.forName("UTF-8"))); }
            finally { output.close(); }
        } catch (Throwable ignored) {
        }
    }

    private void search() {
        Class<?>[] classes = instrumentation.getAllLoadedClasses();
        for (Class<?> type : classes) {
            try {
                if (type.isArray() || type.isPrimitive() || type.isInterface()) continue;
                String name = type.getName();
                if (!name.startsWith("net.minecraft.") && name.indexOf('.') >= 0) continue;
                Field[] fields = type.getDeclaredFields();
                for (Field self : fields) {
                    if (!Modifier.isStatic(self.getModifiers()) || self.getType() != type) continue;
                    if (!open(type, self)) break;
                    Object instance = self.get(null);
                    if (instance == null) continue;
                    for (Field text : fields) {
                        if (Modifier.isStatic(text.getModifiers()) || text.getType() != String.class) continue;
                        text.setAccessible(true);
                        Object value = text.get(instance);
                        if (value instanceof String && FPS.matcher((String) value).find()) {
                            game = instance;
                            fpsText = text;
                            selfField = self;
                            return;
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private boolean open(Class<?> type, Field field) {
        try {
            field.setAccessible(true);
            return true;
        } catch (Throwable notOpen) {
            try {
                Method getModule = Class.class.getMethod("getModule");
                Object module = getModule.invoke(type);
                Object agentModule = getModule.invoke(FpsAgent.class);
                Class<?> moduleClass = Class.forName("java.lang.Module");
                Method redefine = Instrumentation.class.getMethod("redefineModule", moduleClass, Set.class, Map.class,
                        Map.class, Set.class, Map.class);
                String name = type.getName();
                String pkg = name.substring(0, Math.max(0, name.lastIndexOf('.')));
                redefine.invoke(instrumentation, module, Collections.emptySet(), Collections.emptyMap(),
                        Collections.singletonMap(pkg, Collections.singleton(agentModule)), Collections.emptySet(),
                        Collections.emptyMap());
                field.setAccessible(true);
                return true;
            } catch (Throwable ignored) {
                return false;
            }
        }
    }
}
