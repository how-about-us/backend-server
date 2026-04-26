package com.howaboutus.backend.common.config;

import com.howaboutus.backend.common.config.properties.GooglePlacesProperties;
import com.howaboutus.backend.common.config.properties.GoogleRoutesProperties;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
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

    @Bean
    RestClient googleRoutesRestClient(GoogleRoutesProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));
        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(factory)
                .build();
    }
}
