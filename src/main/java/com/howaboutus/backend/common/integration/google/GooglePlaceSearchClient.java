package com.howaboutus.backend.common.integration.google;

import com.howaboutus.backend.common.config.properties.GooglePlacesProperties;
import com.howaboutus.backend.common.error.ExternalApiException;
import com.howaboutus.backend.common.integration.google.dto.GoogleTextSearchRequest;
import com.howaboutus.backend.common.integration.google.dto.GoogleTextSearchResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

@Component
@RequiredArgsConstructor
public class GooglePlaceSearchClient {

    private final RestClient googlePlacesRestClient;
    private final GooglePlacesProperties properties;

    public List<GoogleTextSearchResponse.PlaceItem> search(
            String query, double latitude, double longitude, double radius) {
        GoogleTextSearchRequest request = GoogleTextSearchRequest.withKorean(query, latitude, longitude, radius);
        try {
            GoogleTextSearchResponse response = googlePlacesRestClient.post()
                    .uri("/v1/places:searchText")
                    .header("X-Goog-Api-Key", properties.apiKey())
                    .header("X-Goog-FieldMask", properties.searchFieldMask())
                    .body(request)
                    .retrieve()
                    .body(GoogleTextSearchResponse.class);

            if (response == null || response.places() == null) {
                return List.of();
            }
            return response.places();
        } catch (RestClientException exception) {
            throw new ExternalApiException(exception);
        }
    }
}
