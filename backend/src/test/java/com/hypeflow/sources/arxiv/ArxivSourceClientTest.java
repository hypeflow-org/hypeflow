package com.hypeflow.sources.arxiv;

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

class ArxivSourceClientTest {

    private MockWebServer mockWebServer;
    private OkHttpClient httpClient;
    private ArxivSourceClient client;

    private static final String SAMPLE_ATOM = """
            <?xml version="1.0" encoding="UTF-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
              <entry>
                <published>2025-01-01T12:00:00Z</published>
              </entry>
              <entry>
                <published>2025-01-01T14:00:00Z</published>
              </entry>
              <entry>
                <published>2025-01-02T10:00:00Z</published>
              </entry>
            </feed>
            """;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        httpClient = new OkHttpClient.Builder().build();
        client = new ArxivSourceClient(httpClient, mockWebServer.url("/").toString(), 0);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void testUrlConstruction() throws Exception {
        mockWebServer.enqueue(new MockResponse()
                .setBody("<feed xmlns=\"http://www.w3.org/2005/Atom\"></feed>")
                .setResponseCode(200));

        client.fetchDailyTimeSeries("machine learning",
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 1));

        RecordedRequest request = mockWebServer.takeRequest();
        String path = request.getPath();

        assertTrue(path.contains("search_query=all:machine"));
        assertTrue(path.contains("sortBy=submittedDate"));
        assertTrue(path.contains("sortOrder=descending"));
    }

    @Test
    void testResponseParsing() {
        mockWebServer.enqueue(new MockResponse()
                .setBody(SAMPLE_ATOM)
                .setResponseCode(200));

        TimeSeries result = client.fetchDailyTimeSeries("quantum",
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 2));

        assertEquals("arxiv", result.source());
        assertEquals(2, result.buckets().size());
        assertEquals(2, result.buckets().get(0).count());
        assertEquals(1, result.buckets().get(1).count());
    }

    @Test
    void testEmptyResponse() {
        mockWebServer.enqueue(new MockResponse()
                .setBody("<feed xmlns=\"http://www.w3.org/2005/Atom\"></feed>")
                .setResponseCode(200));

        TimeSeries result = client.fetchDailyTimeSeries("nonexistent",
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 1));

        assertEquals(1, result.buckets().size());
        assertEquals(0, result.buckets().get(0).count());
    }

    @Test
    void testHttpError() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(503));

        assertThrows(SourceClientException.class, () ->
                client.fetchDailyTimeSeries("quantum",
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
                client.fetchDailyTimeSeries("quantum",
                        LocalDate.of(2025, 1, 3), LocalDate.of(2025, 1, 1))
        );
    }
}
