package com.howaboutus.backend.common.client.google.places;

public record GooglePlacesSearchRequest(
        String textQuery,
        String languageCode,
        String regionCode,
        Integer maxResultCount
) {
}
