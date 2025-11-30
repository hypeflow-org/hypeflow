package com.hypeflow.sources;

import com.hypeflow.model.TimeSeries;

import java.time.LocalDate;

/**
 * Interface for fetching time series data from various sources.
 */
public interface SourceClient {

    /**
     * Returns the unique identifier for this source (e.g., "newsapi", "wikipedia").
     */
    String sourceId();

    /**
     * Fetches daily time series data for the given topic and date range.
     *
     * @param topic The topic/keyword to search for
     * @param startInclusive Start date (inclusive)
     * @param endInclusive End date (inclusive)
     * @return TimeSeries with daily buckets for the specified range
     * @throws SourceClientException if there's an error fetching data
     * @throws IllegalArgumentException if startInclusive is after endInclusive
     */
    TimeSeries fetchDailyTimeSeries(
            String topic,
            LocalDate startInclusive,
            LocalDate endInclusive
    ) throws SourceClientException;
}