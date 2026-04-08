package com.howaboutus.backend.common.client.google.places;

import java.util.List;

public record GooglePlaceSummary(
        String googlePlaceId,
        String displayName,
        String formattedAddress,
        Double latitude,
        Double longitude,
        Double rating,
        List<String> photoNames
) {
}
