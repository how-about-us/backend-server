package com.howaboutus.backend.places.service;

import com.howaboutus.backend.common.config.CachePolicy;
import com.howaboutus.backend.common.error.ExternalApiException;
import com.howaboutus.backend.common.integration.google.GooglePlaceDetailClient;
import com.howaboutus.backend.common.integration.google.GooglePlacePhotoClient;
import com.howaboutus.backend.common.integration.google.dto.GooglePlaceDetailResponse;
import com.howaboutus.backend.places.service.dto.PlaceDetailResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlaceDetailService {

    private final GooglePlaceDetailClient googlePlaceDetailClient;
    private final GooglePlacePhotoClient googlePlacePhotoClient;

    @Cacheable(cacheNames = CachePolicy.Keys.PLACE_DETAIL)
    public PlaceDetailResult getDetail(String googlePlaceId) {
        GooglePlaceDetailResponse detail = googlePlaceDetailClient.getDetail(googlePlaceId);
        List<String> photoUrls = resolvePhotoUrls(detail.photos());
        return PlaceDetailResult.from(detail, photoUrls);
    }

    private List<String> resolvePhotoUrls(List<GooglePlaceDetailResponse.Photo> photos) {
        if (photos == null || photos.isEmpty()) {
            return List.of();
        }
        return photos.stream()
                .map(photo -> {
                    try {
                        return googlePlacePhotoClient.getPhotoUri(photo.name());
                    } catch (ExternalApiException e) {
                        log.warn("장소 사진 URL 조회 실패 (photoName={}): {}", photo.name(), e.getMessage());
                        return null;
                    }
                })
                .filter(url -> url != null)
                .toList();
    }
}
