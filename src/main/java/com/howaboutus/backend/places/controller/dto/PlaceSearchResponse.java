package com.howaboutus.backend.places.controller.dto;

import com.howaboutus.backend.places.service.dto.PlaceSearchResult;

public record PlaceSearchResponse(
        String googlePlaceId,
        String name,
        String formattedAddress,
        PlaceSearchResult.Location location,
        String primaryType,
        String primaryTypeDisplayName,
        Double rating,
        Integer userRatingCount,
        Boolean openNow,
        String photoName
) {
    public static PlaceSearchResponse from(PlaceSearchResult result) {
        return new PlaceSearchResponse(
                result.googlePlaceId(),
                result.name(),
                result.formattedAddress(),
                result.location(),
                result.primaryType(),
                result.primaryTypeDisplayName(),
                result.rating(),
                result.userRatingCount(),
                result.openNow(),
                result.photoName()
        );
    }
}
