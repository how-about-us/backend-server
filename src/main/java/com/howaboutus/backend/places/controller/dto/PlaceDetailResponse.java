package com.howaboutus.backend.places.controller.dto;

import com.howaboutus.backend.places.service.dto.PlaceDetailResult;

import java.util.List;

public record PlaceDetailResponse(
        String googlePlaceId,
        String name,
        String formattedAddress,
        Location location,
        String primaryType,
        Double rating,
        String phoneNumber,
        String websiteUri,
        String googleMapsUri,
        List<String> weekdayDescriptions,
        List<String> photoNames
) {
    public static PlaceDetailResponse from(PlaceDetailResult result) {
        Location location = null;
        if (result.location() != null) {
            location = new Location(result.location().lat(), result.location().lng());
        }

        return new PlaceDetailResponse(
                result.googlePlaceId(),
                result.name(),
                result.formattedAddress(),
                location,
                result.primaryType(),
                result.rating(),
                result.phoneNumber(),
                result.websiteUri(),
                result.googleMapsUri(),
                result.weekdayDescriptions(),
                result.photoNames()
        );
    }

    public record Location(Double lat, Double lng) {
    }
}
