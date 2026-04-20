package com.howaboutus.backend.places.service;

import com.howaboutus.backend.common.config.CachePolicy;
import com.howaboutus.backend.common.integration.google.GooglePlaceDetailClient;
import com.howaboutus.backend.places.service.dto.PlaceDetailResult;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PlaceDetailService {

    private final GooglePlaceDetailClient googlePlaceDetailClient;

    @Cacheable(cacheNames = CachePolicy.Keys.PLACE_DETAIL)
    public PlaceDetailResult getDetail(String googlePlaceId) {
        return PlaceDetailResult.from(googlePlaceDetailClient.getDetail(googlePlaceId));
    }
}
