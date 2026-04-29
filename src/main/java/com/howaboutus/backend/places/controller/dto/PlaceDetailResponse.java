package com.howaboutus.backend.places.controller.dto;

import com.howaboutus.backend.places.service.dto.PlaceDetailResult;

import java.util.List;

public record PlaceDetailResponse(
        String googlePlaceId,
        String name,
        String formattedAddress,
        Location location,
        String primaryType,
        String primaryTypeDisplayName,
        Double rating,
        Integer userRatingCount,
        String phoneNumber,
        String websiteUri,
        String googleMapsUri,
        RegularOpeningHours regularOpeningHours,
        List<String> weekdayDescriptions,
        List<String> photoNames,
        String reviewSummary,
        List<Review> reviews
) {
    public static PlaceDetailResponse from(PlaceDetailResult result) {
        Location location = null;
        if (result.location() != null) {
            location = new Location(result.location().lat(), result.location().lng());
        }

        RegularOpeningHours regularOpeningHours = null;
        if (result.regularOpeningHours() != null) {
            regularOpeningHours = toRegularOpeningHours(result.regularOpeningHours());
        }

        return new PlaceDetailResponse(
                result.googlePlaceId(),
                result.name(),
                result.formattedAddress(),
                location,
                result.primaryType(),
                result.primaryTypeDisplayName(),
                result.rating(),
                result.userRatingCount(),
                result.phoneNumber(),
                result.websiteUri(),
                result.googleMapsUri(),
                regularOpeningHours,
                result.weekdayDescriptions(),
                result.photoNames(),
                result.reviewSummary(),
                toReviews(result.reviews())
        );
    }

    private static RegularOpeningHours toRegularOpeningHours(
            PlaceDetailResult.RegularOpeningHours regularOpeningHours) {
        return new RegularOpeningHours(
                regularOpeningHours.openNow(),
                regularOpeningHours.secondaryHoursType(),
                toSpecialDays(regularOpeningHours.specialDays()),
                toPeriods(regularOpeningHours.periods()),
                regularOpeningHours.weekdayDescriptions(),
                regularOpeningHours.nextOpenTime(),
                regularOpeningHours.nextCloseTime()
        );
    }

    private static List<SpecialDay> toSpecialDays(List<PlaceDetailResult.SpecialDay> specialDays) {
        if (specialDays == null) {
            return List.of();
        }
        return specialDays.stream()
                .map(specialDay -> new SpecialDay(toDate(specialDay.date())))
                .toList();
    }

    private static List<Period> toPeriods(List<PlaceDetailResult.Period> periods) {
        if (periods == null) {
            return List.of();
        }
        return periods.stream()
                .map(period -> new Period(toTimePoint(period.open()), toTimePoint(period.close())))
                .toList();
    }

    private static TimePoint toTimePoint(PlaceDetailResult.TimePoint timePoint) {
        if (timePoint == null) {
            return null;
        }
        return new TimePoint(
                timePoint.day(),
                timePoint.hour(),
                timePoint.minute(),
                toDate(timePoint.date()),
                timePoint.truncated()
        );
    }

    private static Date toDate(PlaceDetailResult.Date date) {
        if (date == null) {
            return null;
        }
        return new Date(date.year(), date.month(), date.day());
    }

    private static List<Review> toReviews(List<PlaceDetailResult.Review> reviews) {
        if (reviews == null) {
            return List.of();
        }
        return reviews.stream()
                .map(review -> new Review(
                        review.rating(),
                        review.text(),
                        review.authorDisplayName(),
                        review.publishTime(),
                        review.relativePublishTimeDescription()
                ))
                .toList();
    }

    public record Location(Double lat, Double lng) {
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

    public record Review(
            Double rating,
            String text,
            String authorDisplayName,
            String publishTime,
            String relativePublishTimeDescription
    ) {
    }
}
