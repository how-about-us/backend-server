package com.howaboutus.backend.common.integration.google.dto;

import java.util.List;

public record GoogleComputeRoutesResponse(
        List<Route> routes
) {

    public record Route(
            Integer distanceMeters,
            String duration
    ) {
        // Google returns duration as "Xs" string (e.g. "900s")
        public int durationSeconds() {
            if (duration == null || duration.isEmpty()) return 0;
            return (int) Double.parseDouble(duration.replace("s", ""));
        }
    }
}
