package com.hypeflow.api;

import com.hypeflow.service.TimeseriesService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class TimeseriesController {

    private final TimeseriesService service;

    public TimeseriesController(TimeseriesService service) {
        this.service = service;
    }

    @PostMapping("/timeseries")
    public TimeseriesResponse getTimeseries(@RequestBody TimeseriesRequest request) {
        return service.query(request);
    }
}