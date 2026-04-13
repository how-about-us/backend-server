package com.howaboutus.backend.common.config;

import com.howaboutus.backend.common.config.properties.GooglePlacesProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class GoogleClientConfig {

    @Bean
    RestClient googleOAuthRestClient() {
        return RestClient.builder()
                .build();
    }

    @Bean
    RestClient googlePlacesRestClient(GooglePlacesProperties properties) {
        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .build();
    }
}
