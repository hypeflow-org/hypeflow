package com.hypeflow.sources.wikipedia;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * DTO for Wikimedia Pageviews API response.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WikiPageviewsResponse(
        List<WikiPageviewsItem> items
) {}
