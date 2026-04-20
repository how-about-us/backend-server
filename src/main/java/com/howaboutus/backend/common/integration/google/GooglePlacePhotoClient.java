package com.howaboutus.backend.common.integration.google;

import com.howaboutus.backend.common.config.properties.GooglePlacesProperties;
import com.howaboutus.backend.common.error.ExternalApiException;
import com.howaboutus.backend.common.integration.google.dto.GooglePlacePhotoResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@RequiredArgsConstructor
public class GooglePlacePhotoClient {

    private static final int MAX_WIDTH_PX = 400;
    private static final int MAX_HEIGHT_PX = 400;

    private final RestClient googlePlacesRestClient;
    private final GooglePlacesProperties properties;

    public String getPhotoUri(String photoName) {
        String uri = String.format("/v1/%s/media?maxWidthPx=%d&maxHeightPx=%d&skipHttpRedirect=true",
                photoName, MAX_WIDTH_PX, MAX_HEIGHT_PX);
        try {
            GooglePlacePhotoResponse response = googlePlacesRestClient.get()
                    .uri(uri)
                    .header("X-Goog-Api-Key", properties.apiKey())
                    .retrieve()
                    .body(GooglePlacePhotoResponse.class);
            return response != null ? response.photoUri() : null;
        } catch (RestClientException exception) {
            throw new ExternalApiException(exception);
        }
    }
}
