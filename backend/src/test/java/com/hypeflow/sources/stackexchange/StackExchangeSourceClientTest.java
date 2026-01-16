package com.hypeflow.sources.stackexchange;

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

class StackExchangeSourceClientTest {

    private MockWebServer mockWebServer;
    private OkHttpClient httpClient;
    private ObjectMapper objectMapper;
    private StackExchangeSourceClient client;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        httpClient = new OkHttpClient.Builder().build();
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        client = new StackExchangeSourceClient(
                httpClient, objectMapper, mockWebServer.url("/").toString(), "stackoverflow"
        );
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void testUrlConstruction() throws Exception {
        mockWebServer.enqueue(new MockResponse()
                .setBody("{\"total\":100}")
                .setResponseCode(200));

        client.fetchDailyTimeSeries("java",
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 1));

        RecordedRequest request = mockWebServer.takeRequest();
        String path = request.getPath();

        assertTrue(path.contains("search/advanced"));
        assertTrue(path.contains("site=stackoverflow"));
        assertTrue(path.contains("q=java"));
        assertTrue(path.contains("pagesize=1"));
    }

    @Test
    void testResponseParsing() {
        mockWebServer.enqueue(new MockResponse()
                .setBody("{\"total\":500}")
                .setResponseCode(200));

        TimeSeries result = client.fetchDailyTimeSeries("java",
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 1));

        assertEquals("stackexchange", result.source());
        assertEquals(1, result.buckets().size());
        assertEquals(500, result.buckets().get(0).count());
    }

    @Test
    void testMultipleDays() {
        mockWebServer.enqueue(new MockResponse().setBody("{\"total\":100}").setResponseCode(200));
        mockWebServer.enqueue(new MockResponse().setBody("{\"total\":150}").setResponseCode(200));

        TimeSeries result = client.fetchDailyTimeSeries("java",
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 2));

        assertEquals(2, result.buckets().size());
        assertEquals(100, result.buckets().get(0).count());
        assertEquals(150, result.buckets().get(1).count());
    }

    @Test
    void testEmptyResponse() {
        mockWebServer.enqueue(new MockResponse()
                .setBody("{\"total\":0,\"items\":[]}")
                .setResponseCode(200));

        TimeSeries result = client.fetchDailyTimeSeries("obscurelanguage",
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 1));

        assertEquals(1, result.buckets().size());
        assertEquals(0, result.buckets().get(0).count());
    }

    @Test
    void testHttpError() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(400));

        assertThrows(SourceClientException.class, () ->
                client.fetchDailyTimeSeries("java",
                        LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 1))
        );
    }

    @Test
    void testMaxRangeDaysValidation() {
        assertThrows(IllegalArgumentException.class, () ->
                client.fetchDailyTimeSeries("java",
                        LocalDate.of(2025, 1, 1), LocalDate.of(2025, 3, 1))
        );
    }
}
