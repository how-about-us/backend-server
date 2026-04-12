package com.howaboutus.backend.places.service.dto;

import java.util.List;

public record GoogleTextSearchResponse(List<PlaceItem> places) {

    public record PlaceItem(
            String id,
            DisplayName displayName,
            String formattedAddress,
            Location location,
            String primaryType,
            Double rating,
            List<Photo> photos
    ) {
    }

    public record DisplayName(String text, String languageCode) {
    }

    public record Location(Double latitude, Double longitude) {
    }

    public record Photo(String name) {
    }
}
