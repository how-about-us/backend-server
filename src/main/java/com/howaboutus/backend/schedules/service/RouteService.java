package com.howaboutus.backend.schedules.service;

import com.howaboutus.backend.common.config.CachePolicy;
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

    @Cacheable(cacheNames = CachePolicy.Keys.ROUTE, key = "#origin + ':' + #destination + ':' + #travelMode")
    public RouteResult computeRoute(String origin, String destination, String travelMode) {
        String mode = travelMode != null ? travelMode : "DRIVING";
        GoogleComputeRoutesResponse response = googleRoutesClient.computeRoutes(origin, destination, mode);
        GoogleComputeRoutesResponse.Route route = response.routes().getFirst();
        return new RouteResult(
                route.distanceMeters() != null ? route.distanceMeters() : 0,
                route.durationSeconds(),
                mode
        );
    }
}
