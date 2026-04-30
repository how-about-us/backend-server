package com.howaboutus.backend.common.integration.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record AiChatPlanResponse(
        String intent,
        @JsonProperty("answer_text")
        String answerText,
        @JsonProperty("recommended_places")
        List<AiRecommendedPlace> recommendedPlaces,
        @JsonProperty("updated_summary")
        AiStructuredSummary updatedSummary
) {
    public AiChatPlanResponse {
        if (recommendedPlaces == null) {
            recommendedPlaces = List.of();
        } else {
            recommendedPlaces = List.copyOf(recommendedPlaces);
        }
    }
}
