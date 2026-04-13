package com.howaboutus.backend.places.service.dto;

public record PlaceSearchResult(
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
}
