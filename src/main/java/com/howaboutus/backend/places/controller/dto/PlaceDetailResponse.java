package com.howaboutus.backend.places.controller.dto;

import com.howaboutus.backend.places.service.dto.PlaceDetailResult;
import java.util.List;

public record PlaceDetailResponse(
        String googlePlaceId,
        String name,
        String formattedAddress,
        PlaceDetailResult.Location location,
        String primaryType,
        String primaryTypeDisplayName,
        Double rating,
        Integer userRatingCount,
        String phoneNumber,
        String websiteUri,
        String googleMapsUri,
        PlaceDetailResult.RegularOpeningHours regularOpeningHours,
        List<String> weekdayDescriptions,
        List<String> photoNames,
        String reviewSummary,
        List<PlaceDetailResult.Review> reviews
) {
    public static PlaceDetailResponse from(PlaceDetailResult result) {
        return new PlaceDetailResponse(
                result.googlePlaceId(),
                result.name(),
                result.formattedAddress(),
                result.location(),
                result.primaryType(),
                result.primaryTypeDisplayName(),
                result.rating(),
                result.userRatingCount(),
                result.phoneNumber(),
                result.websiteUri(),
                result.googleMapsUri(),
                result.regularOpeningHours(),
                result.weekdayDescriptions(),
                result.photoNames(),
                result.reviewSummary(),
                result.reviews()
        );
    }
}
