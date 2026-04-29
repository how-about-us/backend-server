package com.howaboutus.backend.common.integration.google.dto;

import java.util.List;

public record GooglePlaceDetailResponse(
        String id,
        DisplayName displayName,
        String formattedAddress,
        Location location,
        String primaryType,
        LocalizedText primaryTypeDisplayName,
        Double rating,
        Integer userRatingCount,
        String nationalPhoneNumber,
        String websiteUri,
        String googleMapsUri,
        RegularOpeningHours regularOpeningHours,
        List<Photo> photos,
        ReviewSummary reviewSummary,
        List<Review> reviews
) {

    public record DisplayName(String text, String languageCode) {
    }

    public record Location(Double latitude, Double longitude) {
    }

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

    public record Photo(String name) {
    }

    public record ReviewSummary(LocalizedText text) {
    }

    public record Review(
            String name,
            String relativePublishTimeDescription,
            Double rating,
            LocalizedText text,
            LocalizedText originalText,
            AuthorAttribution authorAttribution,
            String publishTime
    ) {
    }

    public record LocalizedText(String text, String languageCode) {
    }

    public record AuthorAttribution(String displayName, String uri, String photoUri) {
    }
}
