package com.howaboutus.backend.common.integration.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AiSummaryUpdateResponse(
        @JsonProperty("room_id")
        String roomId,
        AiStructuredSummary summary
) {
}
