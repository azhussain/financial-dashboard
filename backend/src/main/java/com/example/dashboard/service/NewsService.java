package com.example.dashboard.service;

import com.example.dashboard.dto.NewsItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Latest headlines for a ticker via Yahoo Finance's public RSS feed.
 * No API key required — the feed powers the assistant's news tool.
 */
@Service
public class NewsService {

    private static final Logger log = LoggerFactory.getLogger(NewsService.class);
    private static final String FEED =
            "https://feeds.finance.yahoo.com/rss/2.0/headline?s=%s&region=US&lang=en-US";

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public List<NewsItem> getHeadlines(String symbol, int limit) {
        String url = FEED.formatted(
                URLEncoder.encode(symbol.trim(), StandardCharsets.UTF_8));
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("User-Agent", "stocks-explorer/1.0")
                    .GET()
                    .build();
            HttpResponse<InputStream> response =
                    http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                log.warn("Yahoo news feed returned {} for {}", response.statusCode(), symbol);
                return List.of();
            }
            try (InputStream body = response.body()) {
                return parse(body, limit);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return List.of();
        } catch (Exception e) {
            log.warn("Failed to fetch news for {}: {}", symbol, e.toString());
            return List.of();
        }
    }

    List<NewsItem> parse(InputStream xml, int limit) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature(
                "http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setExpandEntityReferences(false);
        Document doc = factory.newDocumentBuilder().parse(xml);

        NodeList items = doc.getElementsByTagName("item");
        List<NewsItem> out = new ArrayList<>();
        for (int i = 0; i < items.getLength() && out.size() < limit; i++) {
            Element item = (Element) items.item(i);
            out.add(new NewsItem(
                    text(item, "title"), text(item, "pubDate"), text(item, "link")));
        }
        return out;
    }

    private String text(Element item, String tag) {
        NodeList nodes = item.getElementsByTagName(tag);
        return nodes.getLength() == 0 ? "" : nodes.item(0).getTextContent().trim();
    }
}
