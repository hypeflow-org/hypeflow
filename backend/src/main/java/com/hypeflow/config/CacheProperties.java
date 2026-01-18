package com.hypeflow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "hypeflow.cache")
public class CacheProperties {

    private int timeseriesTtlHours = 12;
    private int errorTtlMinutes = 5;

    public int getTimeseriesTtlHours() {
        return timeseriesTtlHours;
    }

    public void setTimeseriesTtlHours(int timeseriesTtlHours) {
        this.timeseriesTtlHours = timeseriesTtlHours;
    }

    public int getErrorTtlMinutes() {
        return errorTtlMinutes;
    }

    public void setErrorTtlMinutes(int errorTtlMinutes) {
        this.errorTtlMinutes = errorTtlMinutes;
    }

}
