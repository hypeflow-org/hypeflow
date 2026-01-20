package com.hypeflow.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "hypeflow.cache")
public class CacheProperties {

    private int timeseriesTtlHours = 12;
    private int errorTtlMinutes = 5;
    private int dayCacheTtlDays = 7;
}
