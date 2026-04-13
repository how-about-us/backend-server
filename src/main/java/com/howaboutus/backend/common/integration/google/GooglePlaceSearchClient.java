package com.howaboutus.backend.common.integration.google;

import com.howaboutus.backend.common.config.properties.GooglePlacesProperties;
import com.howaboutus.backend.common.error.ExternalApiException;
import com.howaboutus.backend.common.integration.google.dto.GoogleTextSearchRequest;
import com.howaboutus.backend.common.integration.google.dto.GoogleTextSearchResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@RequiredArgsConstructor
public class GooglePlaceSearchClient {

    private final RestClient googlePlacesRestClient;
    private final GooglePlacesProperties properties;

    public List<GoogleTextSearchResponse.PlaceItem> search(String query) {
        try {
            GoogleTextSearchResponse response = googlePlacesRestClient.post()
                    .uri(properties.baseUrl() + "/v1/places:searchText")
                    .header("X-Goog-Api-Key", properties.apiKey())
                    .header("X-Goog-FieldMask", properties.fieldMask())
                    .body(new GoogleTextSearchRequest(query))
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
