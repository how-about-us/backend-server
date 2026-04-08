package com.howaboutus.backend.common.client.google.routes;

public record GoogleRouteSummary(
        Integer distanceMeters,
        String duration,
        String encodedPolyline,
        String travelMode
) {
}
