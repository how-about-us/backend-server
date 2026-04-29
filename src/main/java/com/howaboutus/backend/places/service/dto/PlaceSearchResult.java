package com.howaboutus.backend.places.service.dto;

import com.howaboutus.backend.common.integration.google.dto.GooglePlaceLocalizedText;
import com.howaboutus.backend.common.integration.google.dto.GoogleTextSearchResponse;

public record PlaceSearchResult(
        String googlePlaceId,
        String name,
        String formattedAddress,
        Location location,
        String primaryType,
        String primaryTypeDisplayName,
        Double rating,
        Integer userRatingCount,
        Boolean openNow,
        String photoName
) {
    public static PlaceSearchResult from(GoogleTextSearchResponse.PlaceItem place) {
        String name = null;
        if (place.displayName() != null) {
            name = place.displayName().text();
        }

        Location location = null;
        if (place.location() != null) {
            location = new Location(place.location().latitude(), place.location().longitude());
        }

        String photoName = null;
        if (place.photos() != null && !place.photos().isEmpty()) {
            photoName = place.photos().getFirst().name();
        }

        Boolean openNow = null;
        if (place.regularOpeningHours() != null) {
            openNow = place.regularOpeningHours().openNow();
        }

        return new PlaceSearchResult(
                place.id(),
                name,
                place.formattedAddress(),
                location,
                place.primaryType(),
                toText(place.primaryTypeDisplayName()),
                place.rating(),
                place.userRatingCount(),
                openNow,
                photoName
        );
    }

    private static String toText(GooglePlaceLocalizedText localizedText) {
        if (localizedText == null) {
            return null;
        }
        return localizedText.text();
    }

    public record Location(Double lat, Double lng) {
    }
}
