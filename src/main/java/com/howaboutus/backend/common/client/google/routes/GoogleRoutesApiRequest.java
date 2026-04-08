package com.howaboutus.backend.common.client.google.routes;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class GoogleRoutesApiRequest {

    static ComputeRoutesRequest from(GoogleComputeRouteRequest request) {
        return new ComputeRoutesRequest(
                new Waypoint(new Location(new LatLng(request.originLatitude(), request.originLongitude()))),
                new Waypoint(new Location(new LatLng(request.destinationLatitude(), request.destinationLongitude()))),
                request.travelMode(),
                request.languageCode()
        );
    }

    record ComputeRoutesRequest(
            Waypoint origin,
            Waypoint destination,
            String travelMode,
            String languageCode
    ) {
    }

    record Waypoint(Location location) {
    }

    record Location(LatLng latLng) {
    }

    record LatLng(double latitude, double longitude) {
    }
}
