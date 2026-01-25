package com.hypeflow.sources.newsapi;

import com.fasterxml.jackson.databind.ObjectMapper;
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

class NewsApiSourceClientTest {

    private MockWebServer mockWebServer;
    private NewsApiSourceClient client;

    private static final String API_KEY = "test-api-key";
    private static final String LANGUAGE = "en";

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        OkHttpClient httpClient = new OkHttpClient.Builder().build();
        ObjectMapper objectMapper = new ObjectMapper();

        client = new NewsApiSourceClient(
                httpClient,
                objectMapper,
                mockWebServer.url("/").toString(),
                API_KEY,
                LANGUAGE
        );
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void testUrlConstruction() throws Exception {
        mockWebServer.enqueue(new MockResponse()
                .setBody("""
                        {
                          "status": "ok",
                          "articles": []
                        }
                        """)
                .setResponseCode(200));

        client.fetchDailyTimeSeries(
                "machine learning",
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 1, 2)
        );

        RecordedRequest request = mockWebServer.takeRequest();
        assertNotNull(request);

        String path = request.getPath();
        assertTrue(path.contains("q=machine+learning"));
        assertTrue(path.contains("from=2025-01-01"));
        assertTrue(path.contains("to=2025-01-02"));
        assertTrue(path.contains("language=en"));
        assertTrue(path.contains("sortBy=publishedAt"));
        assertTrue(path.contains("pageSize=10"));
        assertTrue(path.contains("page=1"));

        assertEquals(API_KEY, request.getHeader("X-Api-Key"));
    }

    @Test
    void testResponseParsing() {
        String json = """
                {
                  "status": "ok",
                  "articles": [
                    { "publishedAt": "2025-01-01T10:00:00Z" },
                    { "publishedAt": "2025-01-01T15:00:00Z" },
                    { "publishedAt": "2025-01-02T09:00:00Z" }
                  ]
                }
                """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(json)
                .setResponseCode(200));

        TimeSeries result = client.fetchDailyTimeSeries(
                "bitcoin",
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 1, 2)
        );

        assertEquals("newsapi", result.source());
        assertEquals(2, result.buckets().size());
        assertEquals(2, result.buckets().get(0).count()); // 2025-01-01
        assertEquals(1, result.buckets().get(1).count()); // 2025-01-02
    }

    @Test
    void testEmptyResponse() {
        mockWebServer.enqueue(new MockResponse()
                .setBody("""
                        {
                          "status": "ok",
                          "articles": []
                        }
                        """)
                .setResponseCode(200));

        TimeSeries result = client.fetchDailyTimeSeries(
                "nonexistent",
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 1, 1)
        );

        assertEquals(1, result.buckets().size());
        assertEquals(0, result.buckets().get(0).count());
    }

    @Test
    void testHttpError() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(500)
                .setBody("Internal Server Error"));

        assertThrows(SourceClientException.class, () ->
                client.fetchDailyTimeSeries(
                        "bitcoin",
                        LocalDate.of(2025, 1, 1),
                        LocalDate.of(2025, 1, 1)
                )
        );
    }

    @Test
    void testApiErrorResponse() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("""
                        {
                          "status": "error",
                          "code": "apiKeyInvalid",
                          "message": "Your API key is invalid"
                        }
                        """));

        assertThrows(SourceClientException.class, () ->
                client.fetchDailyTimeSeries(
                        "bitcoin",
                        LocalDate.of(2025, 1, 1),
                        LocalDate.of(2025, 1, 1)
                )
        );
    }

    @Test
    void testInvalidTopic() {
        assertThrows(IllegalArgumentException.class, () ->
                client.fetchDailyTimeSeries(
                        "",
                        LocalDate.of(2025, 1, 1),
                        LocalDate.of(2025, 1, 1)
                )
        );
    }

    @Test
    void testInvalidDateRange() {
        assertThrows(IllegalArgumentException.class, () ->
                client.fetchDailyTimeSeries(
                        "bitcoin",
                        LocalDate.of(2025, 1, 2),
                        LocalDate.of(2025, 1, 1)
                )
        );
    }

    @Test
    void testArticlesOutsideRangeAreIgnored() {
        String json = """
                {
                  "status": "ok",
                  "articles": [
                    { "publishedAt": "2024-12-31T23:59:59Z" },
                    { "publishedAt": "2025-01-02T00:00:00Z" }
                  ]
                }
                """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(json)
                .setResponseCode(200));

        TimeSeries result = client.fetchDailyTimeSeries(
                "bitcoin",
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 1, 1)
        );

        assertEquals(1, result.buckets().size());
        assertEquals(0, result.buckets().get(0).count());
    }
}
