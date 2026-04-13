package com.howaboutus.backend.places.service;

import com.howaboutus.backend.common.config.CacheConfig;
import com.howaboutus.backend.common.integration.google.GooglePlaceSearchClient;
import com.howaboutus.backend.common.integration.google.dto.GoogleTextSearchResponse;
import com.howaboutus.backend.places.service.dto.PlaceSearchResult;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PlaceSearchService {

    private final GooglePlaceSearchClient googlePlaceSearchClient;
    private final PlaceReferenceService placeReferenceService;

    @Cacheable(cacheNames = CacheConfig.PLACE_SEARCH_CACHE, keyGenerator = "placeSearchKeyGenerator")
    public List<PlaceSearchResult> search(String query) {
        List<GoogleTextSearchResponse.PlaceItem> places = googlePlaceSearchClient.search(query);
        List<String> googlePlaceIds = places.stream()
                .map(GoogleTextSearchResponse.PlaceItem::id)
                .toList();
        Map<String, Long> placeIds = placeReferenceService.ensurePlaceIds(googlePlaceIds);

        return places.stream()
                .map(place -> toResult(place, placeIds))
                .toList();
    }

    private PlaceSearchResult toResult(GoogleTextSearchResponse.PlaceItem place, Map<String, Long> placeIds) {
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
                placeIds.get(place.id()),
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
