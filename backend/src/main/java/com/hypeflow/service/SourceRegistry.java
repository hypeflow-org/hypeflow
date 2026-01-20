package com.hypeflow.service;

import com.hypeflow.model.SourceDescriptor;
import com.hypeflow.sources.SourceClient;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class SourceRegistry {

    private final Map<String, SourceDescriptor> descriptors = new HashMap<>();
    private final Set<String> enabledSourceIds;

    public SourceRegistry(List<SourceClient> sourceClients) {
        this.enabledSourceIds = sourceClients.stream()
                .map(SourceClient::sourceId)
                .collect(Collectors.toSet());

        registerDefaults();
    }

    private void registerDefaults() {
        register(new SourceDescriptor(
                "wikipedia",
                "Wikipedia",
                "Daily pageviews of a Wikipedia article (not keyword mentions; depends on article title)",
                "encyclopedia",
                "pageviews",
                null,
                null,
                "https://wikimedia.org/api/rest_v1/",
                1.0,
                enabledSourceIds.contains("wikipedia")
        ));

        register(new SourceDescriptor(
                "newsapi",
                "News API",
                "Daily count of news articles matching the query (requires API key)",
                "news",
                "articles",
                30,
                "100 req/day free tier",
                "https://newsapi.org/docs",
                1.0,
                enabledSourceIds.contains("newsapi")
        ));

        register(new SourceDescriptor(
                "reddit",
                "Reddit",
                "Daily count of Reddit posts matching the query",
                "social",
                "posts",
                null,
                "OAuth rate limits apply",
                "https://www.reddit.com/dev/api",
                1.0,
                enabledSourceIds.contains("reddit")
        ));

        register(new SourceDescriptor(
                "gdelt",
                "GDELT Project",
                "Daily count of news articles matching the query (GDELT 2.1 Doc API)",
                "news",
                "articles",
                null,
                "No strict limits",
                "https://blog.gdeltproject.org/gdelt-doc-2-0-api-debuts/",
                1.0,
                enabledSourceIds.contains("gdelt")
        ));

        register(new SourceDescriptor(
                "hackernews",
                "Hacker News",
                "Daily count of Hacker News items matching the query (Algolia search)",
                "tech",
                "stories",
                31,
                "Algolia API limits",
                "https://hn.algolia.com/api",
                1.0,
                enabledSourceIds.contains("hackernews")
        ));

        register(new SourceDescriptor(
                "stackexchange",
                "Stack Overflow",
                "Daily count of StackExchange questions matching the query",
                "tech",
                "questions",
                30,
                "Daily quota applies; backoff may be returned",
                "https://api.stackexchange.com/docs",
                1.0,
                enabledSourceIds.contains("stackexchange")
        ));

        register(new SourceDescriptor(
                "arxiv",
                "arXiv",
                "Daily count of arXiv papers matching the query",
                "academic",
                "papers",
                null,
                "3 sec delay between requests recommended",
                "https://arxiv.org/help/api",
                1.0,
                enabledSourceIds.contains("arxiv")
        ));
    }

    public void register(SourceDescriptor descriptor) {
        descriptors.put(descriptor.id(), descriptor);
    }

    public List<SourceDescriptor> list() {
        return descriptors.values().stream()
                .sorted(Comparator.comparing(SourceDescriptor::title))
                .toList();
    }

    public SourceDescriptor get(String id) {
        return descriptors.get(id);
    }
}
