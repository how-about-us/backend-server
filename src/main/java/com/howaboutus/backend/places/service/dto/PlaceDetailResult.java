package com.howaboutus.backend.places.service.dto;

import com.howaboutus.backend.common.integration.google.dto.GooglePlaceDetailResponse;
import com.howaboutus.backend.common.integration.google.dto.GooglePlaceLocalizedText;
import com.howaboutus.backend.common.integration.google.dto.GooglePlacePhoto;

import java.util.List;

public record PlaceDetailResult(
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
        List<String> photoNames,
        List<Review> reviews
) {
    public static PlaceDetailResult from(GooglePlaceDetailResponse place) {
        String googlePlaceId = place.id();
        if (googlePlaceId != null && googlePlaceId.startsWith("places/")) {
            googlePlaceId = googlePlaceId.substring("places/".length());
        }

        String name = null;
        if (place.displayName() != null) {
            name = place.displayName().text();
        }

        Location location = null;
        if (place.location() != null) {
            location = new Location(place.location().latitude(), place.location().longitude());
        }

        RegularOpeningHours regularOpeningHours = null;
        if (place.regularOpeningHours() != null) {
            regularOpeningHours = toRegularOpeningHours(place.regularOpeningHours());
        }

        List<String> photoNames = List.of();
        if (place.photos() != null) {
            photoNames = place.photos().stream()
                    .map(GooglePlacePhoto::name)
                    .toList();
        }

        List<Review> reviews = List.of();
        if (place.reviews() != null) {
            reviews = place.reviews().stream()
                    .map(PlaceDetailResult::toReview)
                    .toList();
        }

        return new PlaceDetailResult(
                googlePlaceId,
                name,
                place.formattedAddress(),
                location,
                place.primaryType(),
                toText(place.primaryTypeDisplayName()),
                place.rating(),
                place.userRatingCount(),
                place.nationalPhoneNumber(),
                place.websiteUri(),
                place.googleMapsUri(),
                regularOpeningHours,
                photoNames,
                reviews
        );
    }

    private static String toText(GooglePlaceLocalizedText localizedText) {
        if (localizedText == null) {
            return null;
        }
        return localizedText.text();
    }

    private static RegularOpeningHours toRegularOpeningHours(
            GooglePlaceDetailResponse.RegularOpeningHours regularOpeningHours) {
        List<Period> periods = List.of();
        if (regularOpeningHours.periods() != null) {
            periods = regularOpeningHours.periods().stream()
                    .map(period -> new Period(toTimePoint(period.open()), toTimePoint(period.close())))
                    .toList();
        }

        List<String> weekdayDescriptions = List.of();
        if (regularOpeningHours.weekdayDescriptions() != null) {
            weekdayDescriptions = regularOpeningHours.weekdayDescriptions();
        }

        return new RegularOpeningHours(
                regularOpeningHours.openNow(),
                regularOpeningHours.secondaryHoursType(),
                toSpecialDays(regularOpeningHours.specialDays()),
                periods,
                weekdayDescriptions,
                regularOpeningHours.nextOpenTime(),
                regularOpeningHours.nextCloseTime()
        );
    }

    private static List<SpecialDay> toSpecialDays(List<GooglePlaceDetailResponse.SpecialDay> specialDays) {
        if (specialDays == null) {
            return List.of();
        }
        return specialDays.stream()
                .map(specialDay -> new SpecialDay(toDate(specialDay.date())))
                .toList();
    }

    private static TimePoint toTimePoint(GooglePlaceDetailResponse.TimePoint timePoint) {
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

    private static Date toDate(GooglePlaceDetailResponse.Date date) {
        if (date == null) {
            return null;
        }
        return new Date(date.year(), date.month(), date.day());
    }

    private static Review toReview(GooglePlaceDetailResponse.Review review) {
        String text = null;
        if (review.text() != null) {
            text = review.text().text();
        }

        String authorDisplayName = null;
        if (review.authorAttribution() != null) {
            authorDisplayName = review.authorAttribution().displayName();
        }

        return new Review(
                review.rating(),
                text,
                authorDisplayName,
                review.publishTime(),
                review.relativePublishTimeDescription()
        );
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
