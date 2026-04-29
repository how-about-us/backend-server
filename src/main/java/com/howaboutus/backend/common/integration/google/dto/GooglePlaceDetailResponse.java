package com.howaboutus.backend.common.integration.google.dto;

import java.util.List;

public record GooglePlaceDetailResponse(
        String id,
        GooglePlaceDisplayName displayName,
        String formattedAddress,
        GooglePlaceLocation location,
        String primaryType,
        GooglePlaceLocalizedText primaryTypeDisplayName,
        Double rating,
        Integer userRatingCount,
        String nationalPhoneNumber,
        String websiteUri,
        String googleMapsUri,
        RegularOpeningHours regularOpeningHours,
        List<GooglePlacePhoto> photos,
        GooglePlaceReviewSummary reviewSummary,
        List<Review> reviews
) {

    public record RegularOpeningHours(
            Boolean openNow,
            String secondaryHoursType,
            List<SpecialDay> specialDays,
            List<Period> periods,
            List<String> weekdayDescriptions,
            String nextOpenTime,
            String nextCloseTime
    ) {
    }

    public record Period(TimePoint open, TimePoint close) {
    }

    public record TimePoint(Integer day, Integer hour, Integer minute, Date date, Boolean truncated) {
    }

    public record SpecialDay(Date date) {
    }

    public record Date(Integer year, Integer month, Integer day) {
    }

    public record Review(
            String name,
            String relativePublishTimeDescription,
            Double rating,
            GooglePlaceLocalizedText text,
            GooglePlaceLocalizedText originalText,
            GooglePlaceAuthorAttribution authorAttribution,
            String publishTime
    ) {
    }
}
