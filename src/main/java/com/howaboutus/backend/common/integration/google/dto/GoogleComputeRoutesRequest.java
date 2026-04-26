package com.howaboutus.backend.common.integration.google.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record GoogleComputeRoutesRequest(
        Waypoint origin,
        Waypoint destination,
        String travelMode,
        String routingPreference
) {

    public record Waypoint(String placeId) {}

    public static GoogleComputeRoutesRequest of(String originPlaceId, String destPlaceId, String travelMode) {
        String googleMode = toGoogleTravelMode(travelMode);
        String routingPref = "DRIVE".equals(googleMode) ? "TRAFFIC_AWARE" : null;
        return new GoogleComputeRoutesRequest(
                new Waypoint(originPlaceId),
                new Waypoint(destPlaceId),
                googleMode,
                routingPref
        );
    }

    private static String toGoogleTravelMode(String travelMode) {
        if (travelMode == null) return "DRIVE";
        return switch (travelMode.toUpperCase()) {
            case "WALKING" -> "WALK";
            case "BICYCLING" -> "BICYCLE";
            case "TRANSIT" -> "TRANSIT";
            default -> "DRIVE";
        };
    }
}
