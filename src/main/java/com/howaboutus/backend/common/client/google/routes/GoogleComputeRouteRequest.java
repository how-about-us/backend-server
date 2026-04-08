package com.howaboutus.backend.common.client.google.routes;

public record GoogleComputeRouteRequest(
        double originLatitude,
        double originLongitude,
        double destinationLatitude,
        double destinationLongitude,
        String travelMode,
        String languageCode
) {
}
