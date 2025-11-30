package com.hypeflow.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hypeflow.sources.SourceClient;
import com.hypeflow.sources.newsapi.NewsApiSourceClient;
import com.hypeflow.sources.wikipedia.WikipediaSourceClient;
import com.hypeflow.sources.reddit.RedditAuthClient;
import com.hypeflow.sources.reddit.RedditSourceClient;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * Configuration for SourceClient beans.
 */
@Configuration
public class SourceClientConfig {

    private static final Logger log = LoggerFactory.getLogger(SourceClientConfig.class);

    @Bean
    public OkHttpClient okHttpClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .build();
    }

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        mapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    @Bean
    public SourceClient newsApiSourceClient(
            OkHttpClient httpClient,
            ObjectMapper objectMapper,
            @Value("${hypeflow.newsapi.api-key}") String apiKey,
            @Value("${hypeflow.newsapi.language}") String language
    ) {
        boolean apiKeyPresent = apiKey != null && !apiKey.isEmpty();
        log.info("Creating NewsApiSourceClient (apiKeyPresent={}, language={})", apiKeyPresent, language);
        return new NewsApiSourceClient(httpClient, objectMapper, apiKey, language);
    }

    @Bean
    public SourceClient wikipediaSourceClient(
            OkHttpClient httpClient,
            ObjectMapper objectMapper,
            @Value("${hypeflow.wikipedia.project}") String project,
            @Value("${hypeflow.wikipedia.access}") String access,
            @Value("${hypeflow.wikipedia.agent}") String agent,
            @Value("${hypeflow.wikipedia.user-agent}") String userAgent
    ) {
        return new WikipediaSourceClient(
                httpClient, objectMapper, project, access, agent, userAgent
        );
    }

    @Bean
    public RedditAuthClient redditAuthClient(
            OkHttpClient httpClient,
            ObjectMapper objectMapper,
            @Value("${hypeflow.reddit.client-id}") String clientId,
            @Value("${hypeflow.reddit.client-secret}") String clientSecret,
            @Value("${hypeflow.reddit.user-agent}") String userAgent
    ) {
        return new RedditAuthClient(httpClient, objectMapper, clientId, clientSecret, userAgent);
    }

    @Bean
    @ConditionalOnProperty(name = "hypeflow.reddit.enabled", havingValue = "true")
    public SourceClient redditSourceClient(
            OkHttpClient httpClient,
            ObjectMapper objectMapper,
            RedditAuthClient redditAuthClient
    ) {
        return new RedditSourceClient(httpClient, objectMapper, redditAuthClient);
    }
}
