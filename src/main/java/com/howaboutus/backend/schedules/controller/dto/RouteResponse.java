package com.howaboutus.backend.schedules.controller.dto;

import com.howaboutus.backend.schedules.service.dto.RouteResult;

public record RouteResponse(
        int distanceMeters,
        int durationSeconds,
        String travelMode
) {
    public static RouteResponse from(RouteResult result) {
        return new RouteResponse(result.distanceMeters(), result.durationSeconds(), result.travelMode());
    }
}
