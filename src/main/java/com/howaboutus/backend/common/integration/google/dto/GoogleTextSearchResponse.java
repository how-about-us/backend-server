package com.howaboutus.backend.common.integration.google.dto;

import java.util.List;

public record GoogleTextSearchResponse(List<PlaceItem> places) {

    public record PlaceItem(
            String id,
            GooglePlaceDetailResponse.DisplayName displayName,
            String formattedAddress,
            GooglePlaceDetailResponse.Location location,
            String primaryType,
            GooglePlaceDetailResponse.LocalizedText primaryTypeDisplayName,
            Double rating,
            Integer userRatingCount,
            RegularOpeningHours regularOpeningHours,
            GooglePlaceDetailResponse.ReviewSummary reviewSummary,
            List<GooglePlaceDetailResponse.Photo> photos
    ) {
    }

    public record RegularOpeningHours(Boolean openNow) {
    }
}
