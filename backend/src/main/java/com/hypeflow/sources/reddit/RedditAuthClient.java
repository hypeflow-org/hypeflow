package com.hypeflow.sources.reddit;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hypeflow.sources.SourceClientException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.locks.ReentrantLock;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles OAuth token retrieval and caching for Reddit API using the client_credentials flow.
 */
public class RedditAuthClient {

    private static final Logger log = LoggerFactory.getLogger(RedditAuthClient.class);
    private static final String SOURCE_ID = "reddit";
    private static final String TOKEN_URL = "https://www.reddit.com/api/v1/access_token";

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String clientId;
    private final String clientSecret;
    private final String userAgent;

    private final ReentrantLock lock = new ReentrantLock();

    private volatile String accessToken;
    private volatile Instant expiresAt;

    public RedditAuthClient(OkHttpClient httpClient,
                            ObjectMapper objectMapper,
                            String clientId,
                            String clientSecret,
                            String userAgent) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.userAgent = userAgent;
    }

    public String getAccessToken() {
        if (accessToken != null && expiresAt != null && Instant.now().isBefore(expiresAt)) {
            return accessToken;
        }

        lock.lock();
        try {
            if (accessToken != null && expiresAt != null && Instant.now().isBefore(expiresAt)) {
                return accessToken;
            }

            refreshToken();
            return accessToken;
        } finally {
            lock.unlock();
        }
    }

    public String getUserAgent() {
        return userAgent;
    }

    private void refreshToken() {
        String basicAuth = Base64.getEncoder().encodeToString(
                (clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8)
        );

        RequestBody body = new FormBody.Builder()
                .add("grant_type", "client_credentials")
                .build();

        Request request = new Request.Builder()
                .url(TOKEN_URL)
                .header("Authorization", "Basic " + basicAuth)
                .header("User-Agent", userAgent)
                .post(body)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";

            if (!response.isSuccessful()) {
                throw new SourceClientException(
                        SOURCE_ID,
                        "Reddit OAuth error: HTTP " + response.code() + " body: " + responseBody
                );
            }

            RedditTokenResponse tokenResponse = objectMapper.readValue(responseBody, RedditTokenResponse.class);
            this.accessToken = tokenResponse.accessToken;

            long ttlSeconds = Math.max(tokenResponse.expiresIn - 60, 30);
            this.expiresAt = Instant.now().plusSeconds(ttlSeconds);

            log.debug("Obtained Reddit access token expiring in {} seconds", ttlSeconds);
        } catch (IOException e) {
            throw new SourceClientException(SOURCE_ID, "I/O error during Reddit OAuth", e);
        }
    }

    private static class RedditTokenResponse {
        @JsonProperty("access_token")
        public String accessToken;

        @JsonProperty("token_type")
        public String tokenType;

        @JsonProperty("expires_in")
        public long expiresIn;

        @JsonProperty("scope")
        public String scope;
    }
}
