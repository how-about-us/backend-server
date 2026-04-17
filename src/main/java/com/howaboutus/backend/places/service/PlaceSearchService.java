package com.howaboutus.backend.places.service;

import com.howaboutus.backend.common.integration.google.GooglePlaceSearchClient;
import com.howaboutus.backend.places.service.dto.PlaceSearchResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PlaceSearchService {

    private final GooglePlaceSearchClient googlePlaceSearchClient;

    public List<PlaceSearchResult> search(String query, Double latitude, Double longitude, Double radius) {
        return googlePlaceSearchClient.search(query, latitude, longitude, radius)
                .stream()
                .map(PlaceSearchResult::from)
                .toList();
    }
}
