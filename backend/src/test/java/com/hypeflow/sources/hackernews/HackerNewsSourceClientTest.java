package com.hypeflow.sources.hackernews;

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

class HackerNewsSourceClientTest {

    private MockWebServer mockWebServer;
    private HackerNewsSourceClient client;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        OkHttpClient httpClient = new OkHttpClient.Builder().build();
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        client = new HackerNewsSourceClient(httpClient, objectMapper, mockWebServer.url("/").toString());
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void testUrlConstruction() throws Exception {
        mockWebServer.enqueue(new MockResponse()
                .setBody("{\"nbHits\":42}")
                .setResponseCode(200));

        client.fetchDailyTimeSeries("rust",
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 1));

        RecordedRequest request = mockWebServer.takeRequest();
        String path = request.getPath();
        assertNotNull(path);

        assertTrue(path.contains("search_by_date"));
        assertTrue(path.contains("query=rust"));
        assertTrue(path.contains("tags=story"));
        assertTrue(path.contains("numericFilters=created_at_i"));
    }

    @Test
    void testResponseParsing() {
        mockWebServer.enqueue(new MockResponse()
                .setBody("{\"nbHits\":42}")
                .setResponseCode(200));

        TimeSeries result = client.fetchDailyTimeSeries("rust",
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 1));

        assertEquals("hackernews", result.source());
        assertEquals(1, result.buckets().size());
        assertEquals(42, result.buckets().get(0).count());
    }

    @Test
    void testMultipleDays() {
        mockWebServer.enqueue(new MockResponse().setBody("{\"nbHits\":10}").setResponseCode(200));
        mockWebServer.enqueue(new MockResponse().setBody("{\"nbHits\":20}").setResponseCode(200));

        TimeSeries result = client.fetchDailyTimeSeries("rust",
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 2));

        assertEquals(2, result.buckets().size());
        assertEquals(10, result.buckets().get(0).count());
        assertEquals(20, result.buckets().get(1).count());
    }

    @Test
    void testEmptyResponse() {
        mockWebServer.enqueue(new MockResponse()
                .setBody("{\"nbHits\":0}")
                .setResponseCode(200));

        TimeSeries result = client.fetchDailyTimeSeries("nonexistent",
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 1));

        assertEquals(1, result.buckets().size());
        assertEquals(0, result.buckets().get(0).count());
    }

    @Test
    void testHttpError() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(429));

        assertThrows(SourceClientException.class, () ->
                client.fetchDailyTimeSeries("rust",
                        LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 1))
        );
    }

    @Test
    void testMaxRangeDaysValidation() {
        assertThrows(IllegalArgumentException.class, () ->
                client.fetchDailyTimeSeries("rust",
                        LocalDate.of(2025, 1, 1), LocalDate.of(2025, 3, 1))
        );
    }
}
