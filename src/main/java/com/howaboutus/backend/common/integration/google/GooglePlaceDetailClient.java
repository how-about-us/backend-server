package com.howaboutus.backend.common.integration.google;

import com.howaboutus.backend.common.config.properties.GooglePlacesProperties;
import com.howaboutus.backend.common.error.ExternalApiException;
import com.howaboutus.backend.common.integration.google.dto.GooglePlaceDetailResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@RequiredArgsConstructor
public class GooglePlaceDetailClient {

    private final RestClient googlePlacesRestClient;
    private final GooglePlacesProperties properties;

    public GooglePlaceDetailResponse getDetail(String googlePlaceId) {
        try {
            return googlePlacesRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v1/places/{placeId}")
                            .queryParam("languageCode", "ko")
                            .build(googlePlaceId))
                    .header("X-Goog-Api-Key", properties.apiKey())
                    .header("X-Goog-FieldMask", properties.detailFieldMask())
                    .retrieve()
                    .body(GooglePlaceDetailResponse.class);
        } catch (RestClientException exception) {
            throw new ExternalApiException(exception);
        }
    }
}
