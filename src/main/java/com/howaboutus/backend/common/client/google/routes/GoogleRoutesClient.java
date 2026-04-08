package com.howaboutus.backend.common.client.google.routes;

import com.howaboutus.backend.common.client.google.exception.GoogleApiClientException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@RequiredArgsConstructor
public class GoogleRoutesClient {

    private static final String COMPUTE_ROUTES_PATH = "/directions/v2:computeRoutes";
    private static final String COMPUTE_ROUTES_FIELD_MASK =
            "routes.distanceMeters,routes.duration,routes.polyline.encodedPolyline";

    private final RestClient restClient;
    private final String apiKey;

    public GoogleRouteSummary computeRoute(GoogleComputeRouteRequest request) {
        try {
            GoogleRoutesApiResponse.ComputeRoutesResponse response = restClient.post()
                    .uri(COMPUTE_ROUTES_PATH)
                    .header("X-Goog-Api-Key", apiKey)
                    .header("X-Goog-FieldMask", COMPUTE_ROUTES_FIELD_MASK)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(GoogleRoutesApiRequest.from(request))
                    .retrieve()
                    .body(GoogleRoutesApiResponse.ComputeRoutesResponse.class);

            if (response == null || response.routes() == null || response.routes().isEmpty()) {
                throw new GoogleApiClientException("Google Routes API returned no routes");
            }

            GoogleRoutesApiResponse.Route route = response.routes().getFirst();
            return new GoogleRouteSummary(
                    route.distanceMeters(),
                    route.duration(),
                    route.polyline() != null ? route.polyline().encodedPolyline() : null,
                    request.travelMode()
            );
        } catch (RestClientException exception) {
            throw new GoogleApiClientException("Failed to compute route with Google Routes API", exception);
        }
    }
}
