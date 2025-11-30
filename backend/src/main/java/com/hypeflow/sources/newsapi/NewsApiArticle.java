package com.hypeflow.sources.newsapi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * DTO for a single NewsAPI article.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NewsApiArticle(
        String publishedAt,
        String title,
        String url
) {}
