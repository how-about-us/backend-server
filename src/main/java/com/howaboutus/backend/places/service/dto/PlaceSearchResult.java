package com.howaboutus.backend.places.service.dto;

import com.howaboutus.backend.common.integration.google.dto.GoogleTextSearchResponse;

public record PlaceSearchResult(
        String googlePlaceId,
        String name,
        String formattedAddress,
        Location location,
        String primaryType,
        Double rating,
        Boolean openNow,
        String reviewSummary,
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

        String reviewSummary = null;
        if (place.reviewSummary() != null && place.reviewSummary().text() != null) {
            reviewSummary = place.reviewSummary().text().text();
        }

        return new PlaceSearchResult(
                place.id(),
                name,
                place.formattedAddress(),
                location,
                place.primaryType(),
                place.rating(),
                openNow,
                reviewSummary,
                photoName
        );
    }

    public record Location(Double lat, Double lng) {
    }
}
