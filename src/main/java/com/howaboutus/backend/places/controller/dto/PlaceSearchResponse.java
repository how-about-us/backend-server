package com.howaboutus.backend.places.controller.dto;

import com.howaboutus.backend.places.service.dto.PlaceSearchResult;

public record PlaceSearchResponse(
        Long placeId,
        String googlePlaceId,
        String name,
        String formattedAddress,
        Location location,
        String primaryType,
        Double rating,
        String photoName
) {
    public record Location(Double lat, Double lng) {
    }

    public static PlaceSearchResponse from(PlaceSearchResult result) {
        Location location = null;
        if (result.location() != null) {
            location = new Location(result.location().lat(), result.location().lng());
        }

        return new PlaceSearchResponse(
                result.placeId(),
                result.googlePlaceId(),
                result.name(),
                result.formattedAddress(),
                location,
                result.primaryType(),
                result.rating(),
                result.photoName()
        );
    }
}
