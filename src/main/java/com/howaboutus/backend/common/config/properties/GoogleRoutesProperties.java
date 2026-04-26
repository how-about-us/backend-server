package com.howaboutus.backend.common.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "google.routes")
public record GoogleRoutesProperties(
        String apiKey,
        String baseUrl,
        String fieldMask
) {
}
