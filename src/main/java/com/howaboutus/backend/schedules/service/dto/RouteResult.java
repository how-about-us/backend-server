package com.howaboutus.backend.schedules.service.dto;

public record RouteResult(
        int distanceMeters,
        int durationSeconds,
        String travelMode
) {
}
