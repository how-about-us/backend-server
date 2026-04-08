package com.howaboutus.backend.common.client.google.places;

import com.howaboutus.backend.common.client.google.exception.GoogleApiClientException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

@RequiredArgsConstructor
public class GooglePlacesClient {

    private static final String SEARCH_TEXT_PATH = "/v1/places:searchText";
    private static final String SEARCH_TEXT_FIELD_MASK =
            "places.id,places.displayName,places.formattedAddress,places.location,places.rating,places.photos.name";
    private static final String PLACE_DETAILS_FIELD_MASK =
            "id,displayName,formattedAddress,location,rating,photos.name";

    private final RestClient restClient;
    private final String apiKey;

    public List<GooglePlaceSummary> searchText(GooglePlacesSearchRequest request) {
        try {
            GooglePlacesApiResponse.SearchTextResponse response = restClient.post()
                    .uri(SEARCH_TEXT_PATH)
                    .header("X-Goog-Api-Key", apiKey)
                    .header("X-Goog-FieldMask", SEARCH_TEXT_FIELD_MASK)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(GooglePlacesApiResponse.SearchTextResponse.class);

            if (response == null || response.places() == null) {
                return List.of();
            }

            return response.places().stream()
                    .map(this::toPlaceSummary)
                    .toList();
        } catch (RestClientException exception) {
            throw new GoogleApiClientException("Failed to search places with Google Places API", exception);
        }
    }

    public GooglePlaceDetails getPlaceDetails(String placeId) {
        try {
            GooglePlacesApiResponse.Place response = restClient.get()
                    .uri("/v1/places/{placeId}", placeId)
                    .header("X-Goog-Api-Key", apiKey)
                    .header("X-Goog-FieldMask", PLACE_DETAILS_FIELD_MASK)
                    .retrieve()
                    .body(GooglePlacesApiResponse.Place.class);

            if (response == null) {
                throw new GoogleApiClientException("Google Places API returned an empty place details response");
            }

            return toPlaceDetails(response);
        } catch (RestClientException exception) {
            throw new GoogleApiClientException("Failed to get place details with Google Places API", exception);
        }
    }

    private GooglePlaceSummary toPlaceSummary(GooglePlacesApiResponse.Place place) {
        return new GooglePlaceSummary(
                place.id(),
                place.displayName() != null ? place.displayName().text() : null,
                place.formattedAddress(),
                place.location() != null ? place.location().latitude() : null,
                place.location() != null ? place.location().longitude() : null,
                place.rating(),
                place.photos() == null ? List.of() : place.photos().stream()
                        .map(GooglePlacesApiResponse.Photo::name)
                        .toList()
        );
    }

    private GooglePlaceDetails toPlaceDetails(GooglePlacesApiResponse.Place place) {
        return new GooglePlaceDetails(
                place.id(),
                place.displayName() != null ? place.displayName().text() : null,
                place.formattedAddress(),
                place.location() != null ? place.location().latitude() : null,
                place.location() != null ? place.location().longitude() : null,
                place.rating(),
                place.photos() == null ? List.of() : place.photos().stream()
                        .map(GooglePlacesApiResponse.Photo::name)
                        .toList()
        );
    }
}
