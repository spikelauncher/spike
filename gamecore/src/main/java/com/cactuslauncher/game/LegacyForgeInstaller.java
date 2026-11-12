package com.cactuslauncher.game;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.List;

public final class LegacyForgeInstaller {
    private LegacyForgeInstaller() {}

    static List<String> command(Path installer, Path instance) throws IOException {
        String executable = File.separatorChar == '\\' ? "java.exe" : "java";
        Path java = Path.of(System.getProperty("java.home"), "bin", executable);
        try {
            Path classpath = Path.of(LegacyForgeInstaller.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            return List.of(java.toString(), "-Djava.awt.headless=true", "-cp", classpath.toString(),
                    LegacyForgeInstaller.class.getName(), installer.toString(), instance.toString());
        } catch (URISyntaxException e) {
            throw new IOException("Не удалось определить путь к лаунчеру.", e);
        }
    }

    public static void main(String[] args) throws Exception {
        try (var loader = new URLClassLoader(new URL[]{Path.of(args[0]).toUri().toURL()}, ClassLoader.getPlatformClassLoader())) {
            var headless = loader.loadClass("net.minecraftforge.installer.ServerInstall").getDeclaredField("headless");
            headless.setAccessible(true);
            headless.setBoolean(null, true);
            Class<?> action = loader.loadClass("net.minecraftforge.installer.ClientInstall");
            Class<?> predicate = loader.loadClass("com.google.common.base.Predicate");
            Object accept = Proxy.newProxyInstance(loader, new Class<?>[]{predicate}, (proxy, method, arguments) ->
                    switch (method.getName()) {
                        case "apply" -> true;
                        case "equals" -> proxy == arguments[0];
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "toString" -> "Forge client library filter";
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
            Object client = action.getConstructor().newInstance();
            File target = new File(args[1]);
            if (!Boolean.TRUE.equals(action.getMethod("run", File.class, predicate).invoke(client, target, accept)))
                throw new IOException("Legacy Forge client installation failed");
        }
    }
}
