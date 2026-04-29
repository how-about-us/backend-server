package com.howaboutus.backend.common.integration.google.dto;

import java.util.List;

public record GoogleTextSearchResponse(List<PlaceItem> places) {

    public record PlaceItem(
            String id,
            GooglePlaceDisplayName displayName,
            String formattedAddress,
            GooglePlaceLocation location,
            String primaryType,
            GooglePlaceLocalizedText primaryTypeDisplayName,
            Double rating,
            Integer userRatingCount,
            RegularOpeningHours regularOpeningHours,
            GooglePlaceReviewSummary reviewSummary,
            List<GooglePlacePhoto> photos
    ) {
    }

    public record RegularOpeningHours(Boolean openNow) {
    }
}
