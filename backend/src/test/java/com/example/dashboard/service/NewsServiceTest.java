package com.example.dashboard.service;

import com.example.dashboard.dto.NewsItem;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NewsServiceTest {

    private final NewsService service = new NewsService();

    private static final String SAMPLE = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <rss version="2.0"><channel>
              <title>Yahoo! Finance: NVDA News</title>
              <item>
                <title>First headline &amp; more</title>
                <link>https://example.com/a</link>
                <pubDate>Thu, 08 Oct 2026 01:03:00 +0000</pubDate>
              </item>
              <item>
                <title>Second headline</title>
                <link>https://example.com/b</link>
                <pubDate>Wed, 07 Oct 2026 12:00:00 +0000</pubDate>
              </item>
            </channel></rss>
            """;

    @Test
    void parsesHeadlines() throws Exception {
        List<NewsItem> items = service.parse(stream(SAMPLE), 5);

        assertEquals(2, items.size());
        assertEquals("First headline & more", items.get(0).title());
        assertEquals("https://example.com/a", items.get(0).link());
        assertEquals("Thu, 08 Oct 2026 01:03:00 +0000", items.get(0).published());
    }

    @Test
    void respectsLimit() throws Exception {
        assertEquals(1, service.parse(stream(SAMPLE), 1).size());
    }

    @Test
    void emptyFeedReturnsEmptyList() throws Exception {
        assertTrue(service.parse(
                stream("<rss version=\"2.0\"><channel/></rss>"), 5).isEmpty());
    }

    private ByteArrayInputStream stream(String xml) {
        return new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
    }
}
