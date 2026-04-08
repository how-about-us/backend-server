package com.howaboutus.backend.common.client.google.places;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.List;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class GooglePlacesApiResponse {

    record SearchTextResponse(List<Place> places) {
    }

    record Place(
            String id,
            DisplayName displayName,
            String formattedAddress,
            Location location,
            Double rating,
            List<Photo> photos
    ) {
    }

    record DisplayName(String text) {
    }

    record Location(Double latitude, Double longitude) {
    }

    record Photo(String name) {
    }
}
