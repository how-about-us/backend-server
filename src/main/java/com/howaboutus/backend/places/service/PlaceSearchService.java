package com.howaboutus.backend.places.service;

import com.howaboutus.backend.places.service.dto.GoogleTextSearchResponse;
import com.howaboutus.backend.places.service.dto.PlaceSearchResult;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PlaceSearchService {

    private final PlaceSearchCacheService cacheService;
    private final GooglePlaceSearchClient googlePlaceSearchClient;
    private final PlaceReferenceService placeReferenceService;

    public List<PlaceSearchResult> search(String query) {
        List<PlaceSearchResult> cached = cacheService.get(query);
        if (cached != null) {
            return cached;
        }

        List<GoogleTextSearchResponse.PlaceItem> places = googlePlaceSearchClient.search(query);
        List<String> googlePlaceIds = places.stream()
                .map(GoogleTextSearchResponse.PlaceItem::id)
                .toList();
        Map<String, Long> placeIds = placeReferenceService.ensurePlaceIds(googlePlaceIds);

        List<PlaceSearchResult> results = places.stream()
                .map(place -> new PlaceSearchResult(
                        placeIds.get(place.id()),
                        place.id(),
                        place.displayName() == null ? null : place.displayName().text(),
                        place.formattedAddress(),
                        place.location() == null ? null
                                : new PlaceSearchResult.Location(place.location().latitude(), place.location().longitude()),
                        place.primaryType(),
                        place.rating(),
                        place.photos() == null || place.photos().isEmpty() ? null : place.photos().getFirst().name()
                ))
                .toList();

        cacheService.put(query, results);
        return results;
    }
}
