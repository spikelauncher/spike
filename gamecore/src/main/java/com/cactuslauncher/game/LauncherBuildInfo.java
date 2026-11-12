package com.cactuslauncher.game;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public final class LauncherBuildInfo {
    private LauncherBuildInfo() {}

    public static String version() {
        try {
            return load().getProperty("version", "");
        } catch (IOException e) {
            return "";
        }
    }

    static Properties load() throws IOException {
        Properties result = new Properties();
        try (InputStream stream = LauncherBuildInfo.class.getClassLoader().getResourceAsStream("cactus-build.properties")) {
            if (stream == null) throw new IOException("В эту сборку не включены данные автообновления.");
            result.load(stream);
        }
        return result;
    }
}
