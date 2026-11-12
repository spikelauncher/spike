package com.cactuslauncher.game;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NewsStoreTest {
    private static final String FEED = """
            {"schema":1,"posts":[{
              "id":"spike","badge":"Бета","title":"Spike","summary":"Новости",
              "featured":true,
              "banners":["https://example.com/cover.jpg","https://example.com/second.png"],
              "body":[{"text":"Текст статьи"}],
              "links":[{"label":"Скачать","url":"https://example.com/download"}]
            }]}
            """;

    @TempDir Path cache;

    @Test
    void acceptsMultipleBannersAndClickableLinks() {
        var post = NewsStore.parse(FEED.getBytes(StandardCharsets.UTF_8)).getFirst();
        assertTrue(post.featured());
        assertEquals(2, post.banners().size());
        assertEquals("https://example.com/download", post.links().getFirst().url());
        assertEquals("Текст статьи", post.body().getFirst().text());
    }

    @Test
    void rejectsUnsafeUrlsAndUnknownSchema() {
        assertThrows(IllegalArgumentException.class, () ->
                NewsStore.parse(FEED.replace("https://example.com/download", "file:///tmp/file")
                        .getBytes(StandardCharsets.UTF_8)));
        assertThrows(IllegalArgumentException.class, () ->
                NewsStore.parse(FEED.replace("\"schema\":1", "\"schema\":2").getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void failedRefreshKeepsLastSavedFeed() throws Exception {
        Path file = cache.resolve("news.json");
        Files.writeString(file, FEED);
        var store = new NewsStore(cache, URI.create("https://127.0.0.1:1/news.json"));
        assertEquals("spike", store.refresh().getFirst().id());
        assertEquals(FEED, Files.readString(file));
    }
}
