package com.howaboutus.backend.common.client.google.routes;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.List;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class GoogleRoutesApiResponse {

    record ComputeRoutesResponse(List<Route> routes) {
    }

    record Route(Integer distanceMeters, String duration, Polyline polyline) {
    }

    record Polyline(String encodedPolyline) {
    }
}
