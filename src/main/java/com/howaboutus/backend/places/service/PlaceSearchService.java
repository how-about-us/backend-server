package com.howaboutus.backend.places.service;

import com.howaboutus.backend.common.error.ExternalApiException;
import com.howaboutus.backend.common.integration.google.GooglePlacePhotoClient;
import com.howaboutus.backend.common.integration.google.GooglePlaceSearchClient;
import com.howaboutus.backend.common.integration.google.dto.GoogleTextSearchResponse;
import com.howaboutus.backend.places.service.dto.PlaceSearchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlaceSearchService {

    private final GooglePlaceSearchClient googlePlaceSearchClient;
    private final GooglePlacePhotoClient googlePlacePhotoClient;

    public List<PlaceSearchResult> search(String query, double latitude, double longitude, double radius) {
        return googlePlaceSearchClient.search(query, latitude, longitude, radius)
                .stream()
                .map(place -> PlaceSearchResult.from(place, resolveFirstPhotoUrl(place.photos())))
                .toList();
    }

    private String resolveFirstPhotoUrl(List<GoogleTextSearchResponse.Photo> photos) {
        if (photos == null || photos.isEmpty()) {
            return null;
        }
        try {
            return googlePlacePhotoClient.getPhotoUri(photos.getFirst().name());
        } catch (ExternalApiException e) {
            log.warn("장소 사진 URL 조회 실패: {}", e.getMessage());
            return null;
        }
    }
}
