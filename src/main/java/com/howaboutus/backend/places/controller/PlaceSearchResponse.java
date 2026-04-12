package com.howaboutus.backend.places.controller;

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
}
