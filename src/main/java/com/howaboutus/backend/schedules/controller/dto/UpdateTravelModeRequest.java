package com.howaboutus.backend.schedules.controller.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record UpdateTravelModeRequest(
        @NotNull
        @Pattern(regexp = "DRIVING|WALKING|BICYCLING|TRANSIT", message = "이동 수단은 DRIVING, WALKING, BICYCLING, TRANSIT 중 하나여야 합니다")
        String travelMode
) {
}
