package com.hypeflow.sources.arxiv;

import com.hypeflow.model.TimeBucket;
import com.hypeflow.model.TimeInterval;
import com.hypeflow.model.TimeSeries;
import com.hypeflow.sources.SourceClient;
import com.hypeflow.sources.SourceClientException;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.StringReader;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ArxivSourceClient implements SourceClient {

    private static final Logger log = LoggerFactory.getLogger(ArxivSourceClient.class);
    private static final String SOURCE_ID = "arxiv";
    private static final int PAGE_SIZE = 100;
    private static final int MAX_RESULTS = 1000;
    private static final String USER_AGENT = "HypeFlow/1.0 (https://github.com/hypeflow-org/hypeflow/)";

    private final OkHttpClient httpClient;
    private final String baseUrl;
    private final long rateLimitDelayMs;

    public ArxivSourceClient(OkHttpClient httpClient, String baseUrl) {
        this(httpClient, baseUrl, 3000);
    }

    public ArxivSourceClient(OkHttpClient httpClient, String baseUrl, long rateLimitDelayMs) {
        this.httpClient = httpClient;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.rateLimitDelayMs = rateLimitDelayMs;
    }

    @Override
    public String sourceId() {
        return SOURCE_ID;
    }

    @Override
    public TimeSeries fetchDailyTimeSeries(String topic,
                                           LocalDate startInclusive,
                                           LocalDate endInclusive) {
        if (topic == null || topic.isBlank()) {
            throw new IllegalArgumentException("topic cannot be null or blank");
        }
        if (startInclusive.isAfter(endInclusive)) {
            throw new IllegalArgumentException("startInclusive cannot be after endInclusive");
        }

        Map<LocalDate, Integer> counts = new HashMap<>();
        int start = 0;
        boolean hasMore = true;

        while (hasMore && start < MAX_RESULTS) {
            String url = String.format(
                    "%s?search_query=all:%s&sortBy=submittedDate&sortOrder=descending&start=%d&max_results=%d",
                    baseUrl,
                    URLEncoder.encode(topic, StandardCharsets.UTF_8),
                    start,
                    PAGE_SIZE
            );

            log.debug("arXiv request (start={}): {}", start, url);

            Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .get()
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                String responseBody = response.body() != null ? response.body().string() : "";

                if (!response.isSuccessful()) {
                    log.error("arXiv Error (HTTP {}): {}", response.code(), responseBody);
                    throw new SourceClientException(
                            SOURCE_ID,
                            "HTTP " + response.code() + " from arXiv"
                    );
                }

                ParseResult parseResult = parseArxivAtom(responseBody);

                if (parseResult.dates.isEmpty()) {
                    hasMore = false;
                    continue;
                }

                for (LocalDate date : parseResult.dates) {
                    if (date.isBefore(startInclusive)) {
                        hasMore = false;
                        break;
                    }
                    if (!date.isAfter(endInclusive)) {
                        counts.merge(date, 1, Integer::sum);
                    }
                }

                if (parseResult.entryCount < PAGE_SIZE) {
                    hasMore = false;
                }

                start += PAGE_SIZE;

                if (hasMore && start < MAX_RESULTS && rateLimitDelayMs > 0) {
                    Thread.sleep(rateLimitDelayMs);
                }

            } catch (IOException e) {
                throw new SourceClientException(SOURCE_ID, "I/O error calling arXiv", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new SourceClientException(SOURCE_ID, "Interrupted while calling arXiv", e);
            }
        }

        List<TimeBucket> buckets = buildContinuousBuckets(startInclusive, endInclusive, counts);
        return new TimeSeries(SOURCE_ID, topic, TimeInterval.DAY, buckets);
    }

    private record ParseResult(List<LocalDate> dates, int entryCount) {}

    private ParseResult parseArxivAtom(String xml) {
        List<LocalDate> dates = new ArrayList<>();

        if (xml == null || xml.isBlank()) {
            return new ParseResult(dates, 0);
        }

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);

            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(xml)));

            NodeList entryNodes = doc.getElementsByTagName("entry");
            int entryCount = entryNodes.getLength();

            NodeList publishedNodes = doc.getElementsByTagName("published");
            for (int i = 0; i < publishedNodes.getLength(); i++) {
                String dateStr = publishedNodes.item(i).getTextContent();
                if (dateStr != null && dateStr.length() >= 10) {
                    try {
                        LocalDate date = LocalDate.parse(
                                dateStr.substring(0, 10),
                                DateTimeFormatter.ISO_LOCAL_DATE
                        );
                        dates.add(date);
                    } catch (Exception e) {
                        log.warn("Failed to parse arXiv date: {}", dateStr);
                    }
                }
            }
            return new ParseResult(dates, entryCount);
        } catch (Exception e) {
            throw new SourceClientException(SOURCE_ID, "Failed to parse arXiv Atom response: " + e.getMessage(), e);
        }
    }

    private static List<TimeBucket> buildContinuousBuckets(
            LocalDate start, LocalDate end, Map<LocalDate, Integer> counts) {
        List<TimeBucket> buckets = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            buckets.add(new TimeBucket(d, counts.getOrDefault(d, 0)));
        }
        return buckets;
    }
}
