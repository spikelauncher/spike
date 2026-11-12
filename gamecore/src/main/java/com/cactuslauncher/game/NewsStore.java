package com.cactuslauncher.game;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import javax.imageio.ImageIO;

public final class NewsStore {
    private static final URI FEED = URI.create("https://raw.githubusercontent.com/spikelauncher/news/main/news.json");
    private static final int MAX_FEED = 512 * 1024;
    private static final int MAX_IMAGE = 8 * 1024 * 1024;

    public record Block(String title, String text) {}
    public record Link(String label, String url) {}
    public record Post(String id, String badge, String title, String summary, String cardSummary,
                       String byline, boolean featured, boolean showBodyOnCard,
                       List<String> banners, List<Block> body, List<Link> links) {}

    private final Path directory;
    private final URI feed;

    public NewsStore(Path directory) {
        this(directory, FEED);
    }

    NewsStore(Path directory, URI feed) {
        this.directory = directory;
        this.feed = feed;
    }

    public List<Post> loadCached() {
        Path cache = directory.resolve("news.json");
        if (Files.isRegularFile(cache)) {
            try (var input = Files.newInputStream(cache)) {
                return parse(read(input, MAX_FEED));
            } catch (IOException | RuntimeException ignored) {}
        }
        try (var input = NewsStore.class.getResourceAsStream("/news-default.json")) {
            return input == null ? List.of() : parse(read(input, MAX_FEED));
        } catch (IOException | RuntimeException ignored) {
            return List.of();
        }
    }

    public List<Post> refresh() {
        try {
            byte[] bytes = download(feed, MAX_FEED);
            List<Post> posts = parse(bytes);
            save(directory.resolve("news.json"), bytes);
            return posts;
        } catch (IOException | RuntimeException ignored) {
            return loadCached();
        }
    }

    public BufferedImage image(String url) {
        try {
            URI uri = https(url);
            var hash = MessageDigest.getInstance("SHA-256").digest(url.getBytes(StandardCharsets.UTF_8));
            Path cache = directory.resolve("banners").resolve(HexFormat.of().formatHex(hash));
            if (Files.isRegularFile(cache)) {
                try (var input = Files.newInputStream(cache)) {
                    return decode(read(input, MAX_IMAGE));
                } catch (IOException | RuntimeException ignored) {}
            }
            byte[] bytes = download(uri, MAX_IMAGE);
            BufferedImage image = decode(bytes);
            save(cache, bytes);
            return image;
        } catch (IOException | NoSuchAlgorithmException | RuntimeException ignored) {
            return null;
        }
    }

    static List<Post> parse(byte[] bytes) {
        JsonObject feed = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
        if (!text(feed, "schema", true).equals("1")) throw new IllegalArgumentException("Unsupported news schema");
        var ids = new HashSet<String>();
        var posts = new ArrayList<Post>();
        for (JsonElement entry : array(feed, "posts", 100, true)) {
            JsonObject item = entry.getAsJsonObject();
            String id = text(item, "id", true);
            if (!id.matches("[a-zA-Z0-9_-]{1,80}") || !ids.add(id)) throw new IllegalArgumentException("Invalid news ID");
            var banners = new ArrayList<String>();
            for (JsonElement banner : array(item, "banners", 12, false))
                banners.add(https(banner.getAsString()).toString());
            var blocks = new ArrayList<Block>();
            for (JsonElement entryBlock : array(item, "body", 48, false)) {
                JsonObject block = entryBlock.getAsJsonObject();
                blocks.add(new Block(text(block, "title", false), text(block, "text", true)));
            }
            var links = new ArrayList<Link>();
            for (JsonElement entryLink : array(item, "links", 24, false)) {
                JsonObject link = entryLink.getAsJsonObject();
                links.add(new Link(text(link, "label", true), https(text(link, "url", true)).toString()));
            }
            posts.add(new Post(id, text(item, "badge", true), text(item, "title", true),
                    text(item, "summary", true), text(item, "cardSummary", false), text(item, "byline", false),
                    flag(item, "featured"), flag(item, "showBodyOnCard"),
                    List.copyOf(banners), List.copyOf(blocks), List.copyOf(links)));
        }
        return List.copyOf(posts);
    }

    private static JsonArray array(JsonObject object, String key, int limit, boolean required) {
        if (!required && !object.has(key)) return new JsonArray();
        JsonArray values = object.getAsJsonArray(key);
        if (values == null || values.size() > limit) throw new IllegalArgumentException("Invalid news list: " + key);
        return values;
    }

    private static String text(JsonObject object, String key, boolean required) {
        if (!required && !object.has(key)) return "";
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive()
                || (!key.equals("schema") && !value.getAsJsonPrimitive().isString()))
            throw new IllegalArgumentException("Invalid news field: " + key);
        String text = value.getAsString();
        if (text.length() > 12_000 || (required && text.isBlank())) throw new IllegalArgumentException("Invalid news text: " + key);
        return text;
    }

    private static boolean flag(JsonObject object, String key) {
        if (!object.has(key)) return false;
        var value = object.getAsJsonPrimitive(key);
        if (!value.isBoolean()) throw new IllegalArgumentException("Invalid news flag: " + key);
        return value.getAsBoolean();
    }

    private static URI https(String url) {
        URI uri = URI.create(url);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null)
            throw new IllegalArgumentException("Expected an HTTPS URL");
        return uri;
    }

    private static byte[] download(URI uri, int limit) throws IOException {
        for (int redirects = 0; redirects <= 3; redirects++) {
            var connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(5_000);
            connection.setReadTimeout(6_000);
            connection.setUseCaches(false);
            connection.setRequestProperty("Cache-Control", "no-cache");
            connection.setRequestProperty("User-Agent", "SpikeLauncher");
            try {
                int status = connection.getResponseCode();
                if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
                    String location = connection.getHeaderField("Location");
                    if (location == null) throw new IOException("Missing redirect location");
                    uri = https(uri.resolve(location).toString());
                    continue;
                }
                if (status != 200 || connection.getContentLengthLong() > limit)
                    throw new IOException("News download failed: " + status);
                try (var input = connection.getInputStream()) {
                    return read(input, limit);
                }
            } finally {
                connection.disconnect();
            }
        }
        throw new IOException("Too many redirects");
    }

    private static byte[] read(InputStream input, int limit) throws IOException {
        byte[] bytes = input.readNBytes(limit + 1);
        if (bytes.length > limit) throw new IOException("News file too large");
        return bytes;
    }

    private static BufferedImage decode(byte[] bytes) throws IOException {
        try (var input = new javax.imageio.stream.MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("Unsupported banner");
            var reader = readers.next();
            try {
                String format = reader.getFormatName();
                if (!format.equalsIgnoreCase("JPEG") && !format.equalsIgnoreCase("PNG"))
                    throw new IOException("Unsupported banner format");
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * height > 25_000_000)
                    throw new IOException("Invalid banner dimensions");
                int sample = Math.max(1, (Math.max(width, height) + 1799) / 1800);
                var params = reader.getDefaultReadParam();
                params.setSourceSubsampling(sample, sample, 0, 0);
                return reader.read(0, params);
            } finally {
                reader.dispose();
            }
        }
    }

    private static void save(Path path, byte[] bytes) {
        try {
            Files.createDirectories(path.getParent());
            Path temporary = Files.createTempFile(path.getParent(), ".news-", ".tmp");
            try {
                Files.write(temporary, bytes);
                try {
                    Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException ignored) {
                    Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException ignored) {}
    }
}
