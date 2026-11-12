package com.cactuslauncher.game;

import com.google.gson.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarFile;
import java.util.*;

/** Installs official Fabric profiles and Forge's official client installer into one instance. */
public final class LoaderInstaller {
    public record Progress(String stage) {}
    private static final String FABRIC_META = "https://meta.fabricmc.net/v2";
    private static final String FORGE_PROMOTIONS = "https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json";
    private static final String FORGE_CATALOG = "https://files.minecraftforge.net/net/minecraftforge/forge/maven-metadata.json";
    private static final String FORGE_MAVEN = "https://maven.minecraftforge.net/net/minecraftforge/forge/";
    private static final long MAX_ARTIFACT = 512L * 1024 * 1024;
    private final Path root;
    private final Consumer progress;

    @FunctionalInterface public interface Consumer { void accept(Progress progress); }

    public LoaderInstaller(Path root, Consumer progress) {
        this.root = root.toAbsolutePath().normalize();
        this.progress = progress;
    }

    public String install(String loader, String gameVersion) throws IOException {
        if (loader.equalsIgnoreCase("Fabric")) return installFabric(gameVersion);
        if (loader.equalsIgnoreCase("Forge")) return installForge(gameVersion);
        if (loader.equalsIgnoreCase("Vanilla")) return gameVersion;
        throw new IOException("Этот загрузчик пока не поддерживается: " + loader);
    }

    public static boolean installed(Path root, String loader, String gameVersion) {
        if (loader.equalsIgnoreCase("Vanilla")) return VersionInstaller.installed(root, gameVersion);
        try {
            if (!VersionInstaller.installed(root, gameVersion)) return false;
            Properties marker = readMarker(root);
            if (!gameVersion.equals(marker.getProperty("minecraft")) || !loader.equalsIgnoreCase(marker.getProperty("loader"))) return false;
            String id = marker.getProperty("profile", "");
            if (!safeId(id)) return false;
            Path json = root.resolve("versions").resolve(id).resolve(id + ".json");
            if (Files.isSymbolicLink(root.resolve("versions")) || Files.isSymbolicLink(json.getParent())
                    || Files.isSymbolicLink(json) || !Files.isRegularFile(json, LinkOption.NOFOLLOW_LINKS)) return false;
            try (var reader = Files.newBufferedReader(json)) {
                JsonObject profile = JsonParser.parseReader(reader).getAsJsonObject();
                return gameVersion.equals(profile.has("inheritsFrom") ? profile.get("inheritsFrom").getAsString() : profile.get("id").getAsString());
            }
        } catch (Exception e) { return false; }
    }

    public static String profileId(Path root, String loader, String gameVersion) throws IOException {
        if (loader.equalsIgnoreCase("Vanilla")) return gameVersion;
        if (!installed(root, loader, gameVersion)) throw new IOException("Сначала установите загрузчик для этой сборки.");
        return readMarker(root).getProperty("profile");
    }

    private String installFabric(String gameVersion) throws IOException {
        progress.accept(new Progress("Получаем совместимые версии Fabric"));
        JsonArray versions = getJson(FABRIC_META + "/versions/loader/" + pathSegment(gameVersion)).getAsJsonArray();
        JsonObject selected = null;
        for (JsonElement value : versions) {
            JsonObject item = value.getAsJsonObject();
            if (item.has("loader") && item.getAsJsonObject("loader").has("version")
                    && item.getAsJsonObject("loader").get("version").isJsonPrimitive()
                    && item.has("loader") && (!item.has("stable") || item.get("stable").getAsBoolean())) { selected = item; break; }
        }
        if (selected == null) throw new IOException("У Fabric нет стабильного загрузчика для Minecraft " + gameVersion + ".");
        String loaderVersion = selected.getAsJsonObject("loader").get("version").getAsString();
        String url = FABRIC_META + "/versions/loader/" + pathSegment(gameVersion) + "/" + pathSegment(loaderVersion) + "/profile/json";
        JsonObject profile = getJson(url).getAsJsonObject();
        String id = required(profile, "id");
        if (!safeId(id) || !gameVersion.equals(required(profile, "inheritsFrom"))) throw new IOException("Fabric вернул профиль для другой версии Minecraft.");
        progress.accept(new Progress("Загружаем библиотеки Fabric"));
        Path staging = Files.createTempDirectory(root, ".fabric-stage-");
        try {
            downloadProfileLibraries(profile, staging, false);
            checkCancelled();
            Path versionDir = root.resolve("versions").resolve(id);
            ensureNoSymlinks(versionDir);
            Files.createDirectories(versionDir);
            atomicWrite(versionDir.resolve(id + ".json"), profile.toString());
            writeMarker("Fabric", gameVersion, id, loaderVersion);
            return id;
        } finally { deleteTree(staging); }
    }

    public record ForgeInstaller(String coordinate, String url, String hash) {}

    public static ForgeInstaller resolveForge(String gameVersion) throws IOException {
        JsonObject catalog = getJson(FORGE_CATALOG).getAsJsonObject();
        JsonArray versions = catalog.getAsJsonArray(gameVersion);
        if (versions == null) throw forgeUnavailable(gameVersion);

        JsonObject promotions = getJson(FORGE_PROMOTIONS).getAsJsonObject().getAsJsonObject("promos");
        String prefix = gameVersion + "-";
        Set<String> candidates = new LinkedHashSet<>();
        for (String channel : List.of("-recommended", "-latest")) {
            String promoted = string(promotions, gameVersion + channel);
            if (promoted == null) continue;
            String exact = prefix + promoted;
            for (int i = versions.size() - 1; i >= 0; i--) {
                String coordinate = versions.get(i).getAsString();
                if (coordinate.equals(exact) || coordinate.startsWith(exact + "-")) candidates.add(coordinate);
            }
        }
        for (int i = versions.size() - 1; i >= 0; i--) candidates.add(versions.get(i).getAsString());

        for (String coordinate : candidates) {
            checkCancelled();
            if (!safeId(coordinate) || !coordinate.startsWith(prefix)) continue;
            String url = FORGE_MAVEN + coordinate + "/forge-" + coordinate + "-installer.jar";
            try {
                return new ForgeInstaller(coordinate, url, sha1(url + ".sha1"));
            } catch (FileNotFoundException missing) {
                // Some older Forge releases only published a universal JAR.
            }
        }
        throw forgeUnavailable(gameVersion);
    }

    private static IOException forgeUnavailable(String gameVersion) {
        return new IOException("У Forge нет установщика для Minecraft " + gameVersion
                + ". Выберите версию Minecraft с поддержкой Forge.");
    }

    private String installForge(String gameVersion) throws IOException {
        progress.accept(new Progress("Получаем совместимую версию Forge"));
        return installForge(gameVersion, resolveForge(gameVersion));
    }

    public String installForge(String gameVersion, ForgeInstaller release) throws IOException {
        String forgeVersion = release.coordinate().substring(gameVersion.length() + 1);
        ensureNoSymlinks(root.resolve("versions"));
        ensureNoSymlinks(root.resolve("libraries"));
        Path installDir = Files.createTempDirectory(root, ".forge-install-");
        Path installer = installDir.resolve("forge-installer.jar");
        Process process = null;
        try {
            progress.accept(new Progress("Скачиваем официальный установщик Forge " + forgeVersion));
            downloadHashed(release.url(), installer, release.hash());
            JsonObject expectedProfile;
            String legacyArtifact = null;
            boolean stripLegacyClient = false;
            boolean legacy;
            try (var jar = new JarFile(installer.toFile())) {
                var metadataEntry = jar.getJarEntry("version.json");
                legacy = metadataEntry == null;
                if (legacy) metadataEntry = jar.getJarEntry("install_profile.json");
                if (metadataEntry == null) throw new IOException("В установщике Forge отсутствует профиль версии.");
                try (var reader = new InputStreamReader(jar.getInputStream(metadataEntry), StandardCharsets.UTF_8)) {
                    JsonObject metadata = JsonParser.parseReader(reader).getAsJsonObject();
                    expectedProfile = legacy ? metadata.getAsJsonObject("versionInfo") : metadata;
                    JsonObject legacyInstall = legacy ? metadata.getAsJsonObject("install") : null;
                    String minecraft = legacy ? string(legacyInstall, "minecraft")
                            : string(expectedProfile, "inheritsFrom");
                    if (legacy) {
                        legacyArtifact = required(legacyInstall, "path");
                        stripLegacyClient = "true".equals(string(legacyInstall, "stripMeta"));
                    }
                    if (!gameVersion.equals(minecraft)) throw new IOException("Установщик Forge предназначен для другой версии Minecraft.");
                }
            }

            String id = required(expectedProfile, "id");
            if (!safeId(id) || id.equals(".") || id.equals("..") || id.equals(gameVersion))
                throw new IOException("Forge вернул некорректный профиль запуска.");
            Path profilePath = root.resolve("versions").resolve(id).resolve(id + ".json");
            Path launchProfiles = root.resolve("launcher_profiles.json");
            Path installLog = root.resolve("logs/forge-install.log");
            ensureNoSymlinks(profilePath);
            ensureNoSymlinks(launchProfiles);
            ensureNoSymlinks(root.resolve("launcher_profiles_microsoft_store.json"));
            ensureNoSymlinks(installLog);
            if (Files.notExists(launchProfiles)) {
                try {
                    Files.writeString(launchProfiles, "{\"profiles\":{}}\n", StandardOpenOption.CREATE_NEW);
                } catch (FileAlreadyExistsException ignored) {
                    ensureNoSymlinks(launchProfiles);
                }
            }

            if (!Files.isRegularFile(launchProfiles, LinkOption.NOFOLLOW_LINKS))
                throw new IOException("Некорректный файл launcher_profiles.json.");
            progress.accept(new Progress("Устанавливаем Forge " + forgeVersion));
            Files.createDirectories(installLog.getParent());
            List<String> command = legacy
                    ? LegacyForgeInstaller.command(installer, root)
                    : modernForgeCommand(gameVersion, installer);
            Files.writeString(installLog, "");
            long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(15);
            for (int attempt = 0; ; attempt++) {
                process = new ProcessBuilder(command).directory(root.toFile())
                        .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.appendTo(installLog.toFile())).start();
                while (!process.waitFor(250, TimeUnit.MILLISECONDS)) {
                    if (System.nanoTime() - deadline >= 0)
                        throw new IOException("Превышено время установки Forge. Подробности: logs/forge-install.log.");
                }
                if (process.exitValue() == 0) break;
                if (attempt > 0 || !failedForgeLibraryDownloads(installLog))
                    throw new IOException("Установщик Forge завершился с ошибкой. Подробности: logs/forge-install.log.");
                progress.accept(new Progress("Повторяем загрузку библиотек Forge"));
                checkCancelled();
            }
            ensureNoSymlinks(profilePath);
            if (!Files.isRegularFile(profilePath, LinkOption.NOFOLLOW_LINKS))
                throw new IOException("Установщик Forge завершился без профиля запуска. Подробности: logs/forge-install.log.");
            JsonObject profile;
            try (var reader = Files.newBufferedReader(profilePath)) { profile = JsonParser.parseReader(reader).getAsJsonObject(); }
            if (legacy && !profile.has("inheritsFrom") && !expectedProfile.has("inheritsFrom")) {
                if (stripLegacyClient && !Files.isRegularFile(profilePath.resolveSibling(id + ".jar"), LinkOption.NOFOLLOW_LINKS))
                    throw new IOException("Установщик Forge не создал клиентский JAR. Подробности: logs/forge-install.log.");
                profile.addProperty("inheritsFrom", gameVersion);
                profile.addProperty("jar", stripLegacyClient ? id : gameVersion);
                atomicWrite(profilePath, profile.toString());
            }
            if (!id.equals(string(profile, "id")) || !gameVersion.equals(string(profile, "inheritsFrom")))
                throw new IOException("Forge создал несовместимый профиль запуска.");
            if (legacy) {
                progress.accept(new Progress("Проверяем библиотеки клиента Forge"));
                downloadProfileLibraries(profile, installDir.resolve("libraries"), true);
                downloadLegacyMappings(gameVersion, legacyArtifact);
            }
            checkCancelled();
            writeMarker("Forge", gameVersion, id, forgeVersion);
            return id;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("Установка Forge отменена.");
        } finally {
            if (process != null && process.isAlive()) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
            }
            deleteTree(installDir);
        }
    }

    private List<String> modernForgeCommand(String gameVersion, Path installer) throws IOException {
        int javaVersion = 8;
        Path vanillaJson = root.resolve("versions").resolve(gameVersion).resolve(gameVersion + ".json");
        try (var reader = Files.newBufferedReader(vanillaJson)) {
            JsonObject vanilla = JsonParser.parseReader(reader).getAsJsonObject();
            if (vanilla.has("javaVersion")) javaVersion = vanilla.getAsJsonObject("javaVersion").get("majorVersion").getAsInt();
        }
        Path javaExec = new JavaRuntimeManager(root.getParent().getParent().resolve("runtimes"))
                .ensure(javaVersion, message -> progress.accept(new Progress(message)));
        return List.of(javaExec.toString(), "-Djava.awt.headless=true", "-jar", installer.toString(), "--installClient", root.toString());
    }

    private static boolean failedForgeLibraryDownloads(Path installLog) throws IOException {
        try (var lines = Files.lines(installLog, StandardCharsets.ISO_8859_1)) {
            return lines.anyMatch(line -> line.contains("These libraries failed to download")
                    || line.contains("Read timed out") || line.contains("Connection reset"));
        }
    }

    private void downloadLegacyMappings(String gameVersion, String coordinate) throws IOException {
        Path libraries = root.resolve("libraries");
        Path artifact = libraries.resolve(mavenPath(coordinate)).normalize();
        if (!artifact.startsWith(libraries)) throw new IOException("Некорректный путь библиотеки Forge.");
        ensureNoSymlinks(artifact);
        try (var jar = new JarFile(artifact.toFile())) {
            var versionEntry = jar.getJarEntry("fmlversion.properties");
            if (versionEntry == null || jar.getJarEntry("cpw/mods/fml/relauncher/CoreFMLLibraries.class") == null) return;
            Properties version = new Properties();
            try (var in = jar.getInputStream(versionEntry)) { version.load(in); }
            String hash = version.getProperty("fmlbuild.deobfuscation.hash", "");
            if (!hash.matches("[a-f0-9]{40}")) throw new IOException("Forge не указал контрольную сумму таблицы FML.");
            String file = "deobfuscation_data_" + gameVersion + ".zip";
            Path target = root.resolve("lib").resolve(file);
            ensureNoSymlinks(target);
            Files.createDirectories(target.getParent());
            downloadHashed("https://files.prismlauncher.org/fmllibs/" + file, target, hash);
        }
    }

    private void downloadProfileLibraries(JsonObject profile, Path staging, boolean legacyForge) throws IOException {
        JsonArray libraries = profile.getAsJsonArray("libraries");
        if (libraries == null) throw new IOException("Профиль загрузчика не содержит библиотек.");
        Set<Path> done = new HashSet<>();
        for (JsonElement entry : libraries) {
            checkCancelled();
            JsonObject library = entry.getAsJsonObject();
            if (!VersionInstaller.allowed(library) || library.has("downloads")) continue;
            if (legacyForge && (library.has("natives")
                    || (library.has("clientreq") && !library.get("clientreq").getAsBoolean()))) continue;
            String coordinate = required(library, "name");
            String base = library.has("url") ? library.get("url").getAsString() : "https://libraries.minecraft.net/";
            String relative = mavenPath(coordinate);
            URI baseUri = URI.create(base.endsWith("/") ? base : base + "/");
            if (!allowedMaven(baseUri.getHost())) throw new IOException("Библиотека загрузчика ссылается на неизвестный Maven-репозиторий.");
            Path target = root.resolve("libraries").resolve(relative).normalize();
            if (!target.startsWith(root.resolve("libraries")) || !done.add(target)) continue;
            ensureNoSymlinks(target);
            if (legacyForge && Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) continue;
            String artifactUrl = baseUri.resolve(relative).toString();
            Path staged = staging.resolve(relative).normalize();
            if (!staged.startsWith(staging)) throw new IOException("Некорректный путь библиотеки загрузчика.");
            Files.createDirectories(staged.getParent());
            downloadHashed(artifactUrl, staged, sha1(artifactUrl + ".sha1"));
            Files.createDirectories(target.getParent());
            move(staged, target);
        }
    }

    private static String sha1(String url) throws IOException {
        String value = getText(url).trim().split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        if (!value.matches("[a-f0-9]{40}")) throw new IOException("Maven не вернул корректную SHA-1 контрольную сумму.");
        return value;
    }

    private void downloadHashed(String url, Path target, String expectedHash) throws IOException {
        retryNetwork(() -> {
            downloadArtifact(url, target, expectedHash);
            return null;
        });
    }

    private void downloadArtifact(String url, Path target, String expectedHash) throws IOException {
        if (Files.isSymbolicLink(target)) throw new IOException("Загружаемый файл не должен быть символической ссылкой.");
        URI uri = URI.create(url);
        boolean legacyMappings = "files.prismlauncher.org".equals(uri.getHost())
                && uri.getPath().matches("/fmllibs/deobfuscation_data_[A-Za-z0-9._+-]+[.]zip");
        if (!"https".equals(uri.getScheme()) || !(allowedMaven(uri.getHost()) || legacyMappings))
            throw new IOException("Загрузка с неизвестного сервера заблокирована.");
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
        connection.setConnectTimeout(15000); connection.setReadTimeout(30000); connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("User-Agent", "SpikeLauncher/2");
        Path temp = Files.createTempFile(target.getParent(), "loader-", ".part");
        try {
            if (connection.getResponseCode() != 200) throw new IOException("Сервер загрузки вернул HTTP " + connection.getResponseCode() + ".");
            long size = 0;
            MessageDigest digest = digest();
            try (InputStream in = connection.getInputStream(); OutputStream out = Files.newOutputStream(temp)) {
                byte[] buffer = new byte[65536]; int count;
                while ((count = in.read(buffer)) != -1) {
                    checkCancelled(); size += count;
                    if (size > MAX_ARTIFACT) throw new IOException("Библиотека загрузчика превышает допустимый размер.");
                    out.write(buffer, 0, count); digest.update(buffer, 0, count);
                }
            }
            if (size == 0 || !HexFormat.of().formatHex(digest.digest()).equals(expectedHash)) throw new IOException("Контрольная сумма библиотеки загрузчика не совпала.");
            Files.createDirectories(target.getParent()); move(temp, target);
        } finally { connection.disconnect(); Files.deleteIfExists(temp); }
    }

    private static JsonElement getJson(String url) throws IOException { return JsonParser.parseString(getText(url)); }
    private static String getText(String url) throws IOException {
        return retryNetwork(() -> fetchText(url));
    }

    private static String fetchText(String url) throws IOException {
        URI uri = URI.create(url);
        if (!"https".equals(uri.getScheme()) || !allowedMaven(uri.getHost())) throw new IOException("Запрос к неизвестному серверу заблокирован.");
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
        connection.setConnectTimeout(15000); connection.setReadTimeout(30000); connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("User-Agent", "SpikeLauncher/2");
        try {
            int status = connection.getResponseCode();
            if (status == 404) throw new FileNotFoundException("Сервер загрузчика вернул HTTP 404.");
            if (status != 200) throw new IOException("Сервер загрузчика вернул HTTP " + status + ".");
            try (var input = connection.getInputStream()) {
                byte[] bytes = input.readNBytes(16 * 1024 * 1024 + 1);
                if (bytes.length > 16 * 1024 * 1024) throw new IOException("Ответ сервера загрузчика слишком большой.");
                return new String(bytes, StandardCharsets.UTF_8);
            }
        } finally { connection.disconnect(); }
    }

    @FunctionalInterface
    private interface NetworkRequest<T> {
        T run() throws IOException;
    }

    private static <T> T retryNetwork(NetworkRequest<T> request) throws IOException {
        try {
            return request.run();
        } catch (SocketTimeoutException | SocketException firstFailure) {
            checkCancelled();
            try {
                return request.run();
            } catch (IOException secondFailure) {
                secondFailure.addSuppressed(firstFailure);
                throw secondFailure;
            }
        }
    }

    private void writeMarker(String loader, String game, String profile, String loaderVersion) throws IOException {
        Properties properties = new Properties();
        properties.setProperty("loader", loader); properties.setProperty("minecraft", game);
        properties.setProperty("profile", profile); properties.setProperty("loaderVersion", loaderVersion);
        Path marker = root.resolve(".cactus-loader.properties");
        if (Files.isSymbolicLink(marker)) throw new IOException("Маркер загрузчика не должен быть символической ссылкой.");
        Path temp = Files.createTempFile(root, "loader-marker-", ".tmp");
        try (var out = Files.newOutputStream(temp)) { properties.store(out, "Spike Launcher loader"); }
        try { move(temp, marker); } finally { Files.deleteIfExists(temp); }
    }

    private static Properties readMarker(Path root) throws IOException {
        Path marker = root.resolve(".cactus-loader.properties");
        if (Files.isSymbolicLink(marker)) throw new IOException("Некорректный маркер загрузчика.");
        Properties p = new Properties();
        try (var in = Files.newInputStream(marker)) { p.load(in); }
        return p;
    }

    private static String mavenPath(String coordinate) throws IOException {
        String[] parts = coordinate.split(":", -1);
        if (parts.length < 3 || parts.length > 4) throw new IOException("Некорректная Maven-координата загрузчика.");
        for (String part : parts) if (!part.matches("[A-Za-z0-9._+-]+")) throw new IOException("Некорректная Maven-координата загрузчика.");
        String group = parts[0].replace('.', '/'); String artifact = parts[1]; String version = parts[2];
        String classifier = parts.length == 4 ? "-" + parts[3] : "";
        return group + "/" + artifact + "/" + version + "/" + artifact + "-" + version + classifier + ".jar";
    }

    private static String pathSegment(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20"); }
    private static boolean safeId(String id) { return id != null && id.matches("[A-Za-z0-9._+-]{1,128}"); }
    private static String required(JsonObject o, String key) throws IOException {
        String value = string(o, key); if (value == null || value.isBlank()) throw new IOException("Профиль загрузчика не содержит " + key + "."); return value;
    }
    private static String string(JsonObject o, String key) {
        try { return o != null && o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : null; }
        catch (RuntimeException e) { return null; }
    }
    private static boolean allowedMaven(String host) {
        if (host == null) return false;
        return host.equals("meta.fabricmc.net") || host.equals("maven.fabricmc.net") || host.equals("maven.minecraftforge.net")
                || host.equals("libraries.minecraft.net") || host.equals("repo1.maven.org") || host.equals("repo.maven.apache.org")
                || host.equals("files.minecraftforge.net");
    }
    private static MessageDigest digest() throws IOException {
        try { return MessageDigest.getInstance("SHA-1"); }
        catch (NoSuchAlgorithmException e) { throw new IOException(e); }
    }
    private static void atomicWrite(Path path, String content) throws IOException {
        Path temp = Files.createTempFile(path.getParent(), "profile-", ".tmp");
        try { Files.writeString(temp, content, StandardCharsets.UTF_8); move(temp, path); }
        finally { Files.deleteIfExists(temp); }
    }
    private static void move(Path from, Path to) throws IOException {
        try { Files.move(from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException e) { Files.move(from, to, StandardCopyOption.REPLACE_EXISTING); }
    }
    private static void deleteTree(Path path) throws IOException {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return;
        try (var paths = Files.walk(path)) { for (Path item : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(item); }
    }
    private static void checkCancelled() throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Установка загрузчика отменена.");
    }
    private void ensureNoSymlinks(Path path) throws IOException {
        for (Path current = path; current != null && current.startsWith(root); current = current.getParent())
            if (Files.isSymbolicLink(current)) throw new IOException("Путь установки загрузчика содержит символическую ссылку.");
    }
}
