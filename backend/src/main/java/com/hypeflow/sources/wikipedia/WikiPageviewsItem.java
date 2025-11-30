package com.hypeflow.sources.wikipedia;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * DTO for a single Wikimedia pageviews item.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WikiPageviewsItem(
        String project,
        String article,
        String granularity,
        String timestamp,
        String access,
        String agent,
        long views
) {}
