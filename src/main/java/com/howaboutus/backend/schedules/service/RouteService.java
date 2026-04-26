package com.howaboutus.backend.schedules.service;

import com.howaboutus.backend.common.config.CachePolicy;
import com.howaboutus.backend.common.error.CustomException;
import com.howaboutus.backend.common.error.ErrorCode;
import com.howaboutus.backend.common.integration.google.GoogleRoutesClient;
import com.howaboutus.backend.common.integration.google.dto.GoogleComputeRoutesResponse;
import com.howaboutus.backend.schedules.service.dto.RouteResult;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RouteService {

    private final GoogleRoutesClient googleRoutesClient;

    @Cacheable(cacheNames = CachePolicy.Keys.ROUTE, key = "#origin + ':' + #destination + ':' + (#travelMode ?: 'DRIVING')")
    public RouteResult computeRoute(String origin, String destination, String travelMode) {
        String mode;
        if (travelMode != null) {
            mode = travelMode;
        } else {
            mode = "DRIVING";
        }
        GoogleComputeRoutesResponse response = googleRoutesClient.computeRoutes(origin, destination, mode);
        if (response == null || response.routes() == null || response.routes().isEmpty()) {
            throw new CustomException(ErrorCode.EXTERNAL_API_ERROR);
        }
        GoogleComputeRoutesResponse.Route route = response.routes().getFirst();
        int distanceMeters;
        if (route.distanceMeters() != null) {
            distanceMeters = route.distanceMeters();
        } else {
            distanceMeters = 0;
        }
        return new RouteResult(distanceMeters, route.durationSeconds(), mode);
    }
}
