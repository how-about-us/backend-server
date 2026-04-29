package com.howaboutus.backend.common.integration.google.dto;

import java.util.List;

public record GoogleTextSearchResponse(List<PlaceItem> places) {

    public record PlaceItem(
            String id,
            DisplayName displayName,
            String formattedAddress,
            Location location,
            String primaryType,
            Double rating,
            RegularOpeningHours regularOpeningHours,
            ReviewSummary reviewSummary,
            List<Photo> photos
    ) {
    }

    public record DisplayName(String text, String languageCode) {
    }

    public record Location(Double latitude, Double longitude) {
    }

    public record RegularOpeningHours(Boolean openNow) {
    }

    public record ReviewSummary(LocalizedText text) {
    }

    public record LocalizedText(String text, String languageCode) {
    }

    public record Photo(String name) {
    }
}
