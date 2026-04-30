package com.howaboutus.backend.common.config.properties;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai")
public record TravelAiProperties(
        String baseUrl,
        Duration timeout,
        int summaryBatchSize
) {
    public TravelAiProperties {
        if (timeout == null) {
            timeout = Duration.ofSeconds(30);
        }
        if (summaryBatchSize == 0) {
            summaryBatchSize = 30;
        }
    }
}
