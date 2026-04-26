package com.howaboutus.backend.common.integration.google;

import com.howaboutus.backend.common.config.properties.GoogleRoutesProperties;
import com.howaboutus.backend.common.error.ExternalApiException;
import com.howaboutus.backend.common.integration.google.dto.GoogleComputeRoutesRequest;
import com.howaboutus.backend.common.integration.google.dto.GoogleComputeRoutesResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@RequiredArgsConstructor
public class GoogleRoutesClient {

    private static final String FIELD_MASK = "routes.distanceMeters,routes.duration";

    private final RestClient googleRoutesRestClient;
    private final GoogleRoutesProperties properties;

    public GoogleComputeRoutesResponse computeRoutes(String originPlaceId, String destPlaceId, String travelMode) {
        GoogleComputeRoutesRequest request = GoogleComputeRoutesRequest.of(originPlaceId, destPlaceId, travelMode);
        try {
            return googleRoutesRestClient.post()
                    .uri("/directions/v2:computeRoutes")
                    .header("X-Goog-Api-Key", properties.apiKey())
                    .header("X-Goog-FieldMask", FIELD_MASK)
                    .body(request)
                    .retrieve()
                    .body(GoogleComputeRoutesResponse.class);
        } catch (RestClientException e) {
            throw new ExternalApiException(e);
        }
    }
}
