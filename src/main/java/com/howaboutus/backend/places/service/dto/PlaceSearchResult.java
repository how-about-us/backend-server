package com.howaboutus.backend.places.service.dto;

import java.io.Serializable;

public record PlaceSearchResult(
        Long placeId,
        String googlePlaceId,
        String name,
        String formattedAddress,
        Location location,
        String primaryType,
        Double rating,
        String photoName
) implements Serializable {
    public record Location(Double lat, Double lng) implements Serializable {
    }
}
