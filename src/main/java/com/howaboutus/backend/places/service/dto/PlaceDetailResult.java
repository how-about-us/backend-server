package com.howaboutus.backend.places.service.dto;

import com.howaboutus.backend.common.integration.google.dto.GooglePlaceDetailResponse;

import java.util.List;

public record PlaceDetailResult(
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
        List<String> photoUrls
) {
    public static PlaceDetailResult from(GooglePlaceDetailResponse place, List<String> photoUrls) {
        String googlePlaceId = place.id();
        if (googlePlaceId != null && googlePlaceId.startsWith("places/")) {
            googlePlaceId = googlePlaceId.substring("places/".length());
        }

        String name = null;
        if (place.displayName() != null) {
            name = place.displayName().text();
        }

        Location location = null;
        if (place.location() != null) {
            location = new Location(place.location().latitude(), place.location().longitude());
        }

        List<String> weekdayDescriptions = List.of();
        if (place.regularOpeningHours() != null && place.regularOpeningHours().weekdayDescriptions() != null) {
            weekdayDescriptions = place.regularOpeningHours().weekdayDescriptions();
        }

        return new PlaceDetailResult(
                googlePlaceId,
                name,
                place.formattedAddress(),
                location,
                place.primaryType(),
                place.rating(),
                place.nationalPhoneNumber(),
                place.websiteUri(),
                place.googleMapsUri(),
                weekdayDescriptions,
                photoUrls
        );
    }

    public record Location(Double lat, Double lng) {
    }
}
