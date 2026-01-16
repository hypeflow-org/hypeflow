package com.hypeflow.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hypeflow.sources.SourceClient;
import com.hypeflow.sources.arxiv.ArxivSourceClient;
import com.hypeflow.sources.gdelt.GdeltSourceClient;
import com.hypeflow.sources.hackernews.HackerNewsSourceClient;
import com.hypeflow.sources.newsapi.NewsApiSourceClient;
import com.hypeflow.sources.reddit.RedditAuthClient;
import com.hypeflow.sources.reddit.RedditSourceClient;
import com.hypeflow.sources.stackexchange.StackExchangeSourceClient;
import com.hypeflow.sources.wikipedia.WikipediaSourceClient;
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

    @Bean
    @ConditionalOnProperty(name = "hypeflow.gdelt.enabled", havingValue = "true")
    public SourceClient gdeltSourceClient(
            OkHttpClient httpClient,
            ObjectMapper objectMapper,
            @Value("${hypeflow.gdelt.base-url}") String baseUrl
    ) {
        log.info("Creating GdeltSourceClient (baseUrl={})", baseUrl);
        return new GdeltSourceClient(httpClient, objectMapper, baseUrl);
    }

    @Bean
    @ConditionalOnProperty(name = "hypeflow.hackernews.enabled", havingValue = "true")
    public SourceClient hackerNewsSourceClient(
            OkHttpClient httpClient,
            ObjectMapper objectMapper,
            @Value("${hypeflow.hackernews.base-url}") String baseUrl
    ) {
        log.info("Creating HackerNewsSourceClient (baseUrl={})", baseUrl);
        return new HackerNewsSourceClient(httpClient, objectMapper, baseUrl);
    }

    @Bean
    @ConditionalOnProperty(name = "hypeflow.stackexchange.enabled", havingValue = "true")
    public SourceClient stackExchangeSourceClient(
            OkHttpClient httpClient,
            ObjectMapper objectMapper,
            @Value("${hypeflow.stackexchange.base-url}") String baseUrl,
            @Value("${hypeflow.stackexchange.site}") String site
    ) {
        log.info("Creating StackExchangeSourceClient (baseUrl={}, site={})", baseUrl, site);
        return new StackExchangeSourceClient(httpClient, objectMapper, baseUrl, site);
    }

    @Bean
    @ConditionalOnProperty(name = "hypeflow.arxiv.enabled", havingValue = "true")
    public SourceClient arxivSourceClient(
            OkHttpClient httpClient,
            @Value("${hypeflow.arxiv.base-url}") String baseUrl
    ) {
        log.info("Creating ArxivSourceClient (baseUrl={})", baseUrl);
        return new ArxivSourceClient(httpClient, baseUrl);
    }
}
