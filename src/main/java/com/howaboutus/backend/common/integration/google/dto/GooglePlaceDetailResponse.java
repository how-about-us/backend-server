package com.howaboutus.backend.common.integration.google.dto;

import java.util.List;

public record GooglePlaceDetailResponse(
        String id,
        DisplayName displayName,
        String formattedAddress,
        Location location,
        String primaryType,
        Double rating,
        String nationalPhoneNumber,
        String websiteUri,
        String googleMapsUri,
        RegularOpeningHours regularOpeningHours,
        List<Photo> photos
) {

    public record DisplayName(String text, String languageCode) {
    }

    public record Location(Double latitude, Double longitude) {
    }

    public record RegularOpeningHours(List<String> weekdayDescriptions) {
    }

    public record Photo(String name) {
    }
}
