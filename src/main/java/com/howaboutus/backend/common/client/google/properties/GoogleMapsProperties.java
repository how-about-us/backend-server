package com.howaboutus.backend.common.client.google.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "google.maps")
public class GoogleMapsProperties {

    private String apiKey;
    private String placesBaseUrl = "https://places.googleapis.com";
    private String routesBaseUrl = "https://routes.googleapis.com";
}
