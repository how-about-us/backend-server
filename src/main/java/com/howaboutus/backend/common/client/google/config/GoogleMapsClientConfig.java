package com.howaboutus.backend.common.client.google.config;

import com.howaboutus.backend.common.client.google.places.GooglePlacesClient;
import com.howaboutus.backend.common.client.google.properties.GoogleMapsProperties;
import com.howaboutus.backend.common.client.google.routes.GoogleRoutesClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GoogleMapsProperties.class)
public class GoogleMapsClientConfig {

    @Bean
    @Qualifier("googlePlacesRestClient")
    RestClient googlePlacesRestClient(GoogleMapsProperties properties) {
        return RestClient.builder()
                .baseUrl(properties.getPlacesBaseUrl())
                .build();
    }

    @Bean
    @Qualifier("googleRoutesRestClient")
    RestClient googleRoutesRestClient(GoogleMapsProperties properties) {
        return RestClient.builder()
                .baseUrl(properties.getRoutesBaseUrl())
                .build();
    }

    @Bean
    GooglePlacesClient googlePlacesClient(
            @Qualifier("googlePlacesRestClient") RestClient restClient,
            GoogleMapsProperties properties
    ) {
        return new GooglePlacesClient(restClient, properties.getApiKey());
    }

    @Bean
    GoogleRoutesClient googleRoutesClient(
            @Qualifier("googleRoutesRestClient") RestClient restClient,
            GoogleMapsProperties properties
    ) {
        return new GoogleRoutesClient(restClient, properties.getApiKey());
    }
}
