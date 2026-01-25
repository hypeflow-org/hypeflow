package com.hypeflow.sources.wikipedia;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hypeflow.model.TimeSeries;
import com.hypeflow.sources.SourceClientException;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class WikipediaSourceClientTest {

    private MockWebServer mockWebServer;
    private WikipediaSourceClient client;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        OkHttpClient httpClient = new OkHttpClient.Builder().build();
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        client = new WikipediaSourceClient(
                httpClient,
                objectMapper,
                mockWebServer.url("/").toString(),
                "en.wikipedia.org",
                "all-access",
                "user",
                "TestAgent/1.0"
        ) {
            @Override
            public TimeSeries fetchDailyTimeSeries(String topic, LocalDate start, LocalDate end) {
                // Override BASE_URL to point to mock server
                try {
                    var field = WikipediaSourceClient.class.getDeclaredField("BASE_URL");
                    field.setAccessible(true);
                    field.set(null, mockWebServer.url("/").toString());
                } catch (Exception ignored) {}

                return super.fetchDailyTimeSeries(topic, start, end);
            }
        };
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void testUrlConstruction() throws Exception {
        mockWebServer.enqueue(new MockResponse()
                .setBody("{\"items\":[]}")
                .setResponseCode(200));

        client.fetchDailyTimeSeries("Albert Einstein",
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 3));

        RecordedRequest request = mockWebServer.takeRequest();
        String path = request.getPath();
        assertNotNull(path);

        assertTrue(path.contains("/metrics/pageviews/per-article/"));
        assertTrue(path.contains("en.wikipedia.org"));
        assertTrue(path.contains("all-access"));
        assertTrue(path.contains("user"));
        assertTrue(path.contains("Albert_Einstein"));
        assertTrue(path.contains("2025010100"));
        assertTrue(path.contains("2025010300"));
    }

    @Test
    void testResponseParsing() {
        String json = """
                {
                  "items": [
                    {"timestamp": "2025010100", "views": 120},
                    {"timestamp": "2025010200", "views": 250}
                  ]
                }
                """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(json)
                .setResponseCode(200));

        TimeSeries result = client.fetchDailyTimeSeries("Einstein",
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 2));

        assertEquals("wikipedia", result.source());
        assertEquals(2, result.buckets().size());
        assertEquals(120, result.buckets().get(0).count());
        assertEquals(250, result.buckets().get(1).count());
    }

    @Test
    void testEmptyResponse() {
        mockWebServer.enqueue(new MockResponse()
                .setBody("{\"items\":[]}")
                .setResponseCode(200));

        TimeSeries result = client.fetchDailyTimeSeries("UnknownTopic",
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 1));

        assertEquals(1, result.buckets().size());
        assertEquals(0, result.buckets().get(0).count());
    }

    @Test
    void test404NotFoundReturnsZeroBuckets() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(404));

        TimeSeries result = client.fetchDailyTimeSeries("Nonexistent",
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 3));

        assertEquals(3, result.buckets().size());
        assertEquals(0, result.buckets().get(0).count());
        assertEquals(0, result.buckets().get(1).count());
        assertEquals(0, result.buckets().get(2).count());
    }

    @Test
    void testHttpError() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));

        assertThrows(SourceClientException.class, () ->
                client.fetchDailyTimeSeries("Einstein",
                        LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 1))
        );
    }

    @Test
    void testInvalidTopic() {
        assertThrows(IllegalArgumentException.class, () ->
                client.fetchDailyTimeSeries("",
                        LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 1))
        );
    }

    @Test
    void testInvalidDateRange() {
        assertThrows(IllegalArgumentException.class, () ->
                client.fetchDailyTimeSeries("Einstein",
                        LocalDate.of(2025, 1, 3), LocalDate.of(2025, 1, 1))
        );
    }
}
