package com.howaboutus.backend.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "google.places")
public record GooglePlacesProperties(
        String apiKey,
        String baseUrl,
        String fieldMask
) {
}
