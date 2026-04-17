package com.howaboutus.backend.common.integration.google.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record GoogleTextSearchRequest(String textQuery, String languageCode, LocationBias locationBias) {

    public static GoogleTextSearchRequest withKorean(String textQuery) {
        return new GoogleTextSearchRequest(textQuery, "ko", null);
    }

    public static GoogleTextSearchRequest withKoreanAndLocation(
            String textQuery, double latitude, double longitude, double radius) {
        return new GoogleTextSearchRequest(textQuery, "ko",
                new LocationBias(new Circle(new LatLng(latitude, longitude), radius)));
    }

    public record LocationBias(Circle circle) {}

    public record Circle(LatLng center, Double radius) {}

    public record LatLng(Double latitude, Double longitude) {}
}
