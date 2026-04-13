package com.howaboutus.backend.places.service;

import com.howaboutus.backend.common.config.CachePolicy;
import com.howaboutus.backend.common.integration.google.GooglePlaceSearchClient;
import com.howaboutus.backend.common.integration.google.dto.GoogleTextSearchResponse;
import com.howaboutus.backend.places.service.dto.PlaceSearchResult;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PlaceSearchService {

    private final GooglePlaceSearchClient googlePlaceSearchClient;

    @Cacheable(cacheNames = CachePolicy.Keys.PLACES_SEARCH, keyGenerator = "placeSearchKeyGenerator")
    public List<PlaceSearchResult> search(String query) {
        List<GoogleTextSearchResponse.PlaceItem> places = googlePlaceSearchClient.search(query);

        return places.stream()
                .map(this::toResult)
                .toList();
    }

    private PlaceSearchResult toResult(GoogleTextSearchResponse.PlaceItem place) {
        String name = null;
        if (place.displayName() != null) {
            name = place.displayName().text();
        }

        PlaceSearchResult.Location location = null;
        if (place.location() != null) {
            location = new PlaceSearchResult.Location(place.location().latitude(), place.location().longitude());
        }

        String photoName = null;
        if (place.photos() != null && !place.photos().isEmpty()) {
            photoName = place.photos().getFirst().name();
        }

        return new PlaceSearchResult(
                place.id(),
                name,
                place.formattedAddress(),
                location,
                place.primaryType(),
                place.rating(),
                photoName
        );
    }

}
