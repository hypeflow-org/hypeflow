package com.hypeflow.service;

import com.hypeflow.api.TimeseriesRequest;
import com.hypeflow.api.TimeseriesResponse;
import org.springframework.stereotype.Service;

@Service
public class TimeseriesService {

    public TimeseriesResponse query(TimeseriesRequest req) {
        return new TimeseriesResponse(
                req.word(),
                req.startDate(),
                req.endDate(),
                0,
                java.util.List.of(),
                req.sources(),
                false
        );
    }
}