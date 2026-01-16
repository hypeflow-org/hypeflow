package com.hypeflow.sources.gdelt;

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

class GdeltSourceClientTest {

    private MockWebServer mockWebServer;
    private OkHttpClient httpClient;
    private ObjectMapper objectMapper;
    private GdeltSourceClient client;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        httpClient = new OkHttpClient.Builder().build();
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        client = new GdeltSourceClient(httpClient, objectMapper, mockWebServer.url("/").toString());
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void testUrlConstruction() throws Exception {
        mockWebServer.enqueue(new MockResponse()
                .setBody("{\"timeline\":[]}")
                .setResponseCode(200));

        client.fetchDailyTimeSeries("bitcoin",
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 3));

        RecordedRequest request = mockWebServer.takeRequest();
        String path = request.getPath();

        assertTrue(path.contains("query=bitcoin"));
        assertTrue(path.contains("mode=timelinevolraw"));
        assertTrue(path.contains("format=json"));
        assertTrue(path.contains("startdatetime=20250101000000"));
        assertTrue(path.contains("enddatetime=20250103235959"));
    }

    @Test
    void testResponseParsing() {
        String json = """
                {
                  "timeline": [{
                    "data": [
                      {"date": "20250101120000", "value": 100},
                      {"date": "20250102120000", "value": 200}
                    ]
                  }]
                }
                """;
        mockWebServer.enqueue(new MockResponse().setBody(json).setResponseCode(200));

        TimeSeries result = client.fetchDailyTimeSeries("bitcoin",
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 2));

        assertEquals("gdelt", result.source());
        assertEquals(2, result.buckets().size());
        assertEquals(100, result.buckets().get(0).count());
        assertEquals(200, result.buckets().get(1).count());
    }

    @Test
    void testEmptyResponse() {
        mockWebServer.enqueue(new MockResponse()
                .setBody("{\"timeline\":[]}")
                .setResponseCode(200));

        TimeSeries result = client.fetchDailyTimeSeries("nonexistent",
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 1));

        assertEquals(1, result.buckets().size());
        assertEquals(0, result.buckets().get(0).count());
    }

    @Test
    void testHttpError() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));

        assertThrows(SourceClientException.class, () ->
                client.fetchDailyTimeSeries("bitcoin",
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
                client.fetchDailyTimeSeries("bitcoin",
                        LocalDate.of(2025, 1, 3), LocalDate.of(2025, 1, 1))
        );
    }
}
