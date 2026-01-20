package com.hypeflow.sources.newsapi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * DTO for NewsAPI response.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NewsApiResponse(
        String status,
        Integer totalResults,
        List<NewsApiArticle> articles,
        String code,
        String message
) {}
