package com.howaboutus.backend.common.integration.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record AiStructuredSummary(
        @JsonProperty("summary_text")
        String summaryText,
        @JsonProperty("agreed_points")
        List<String> agreedPoints,
        @JsonProperty("open_questions")
        List<String> openQuestions,
        List<String> preferences,
        List<String> constraints,
        @JsonProperty("mentioned_places")
        List<AiMentionedPlace> mentionedPlaces,
        @JsonProperty("last_message_id")
        String lastMessageId
) {
    public AiStructuredSummary {
        if (summaryText == null) {
            summaryText = "";
        }
        agreedPoints = listOrEmpty(agreedPoints);
        openQuestions = listOrEmpty(openQuestions);
        preferences = listOrEmpty(preferences);
        constraints = listOrEmpty(constraints);
        mentionedPlaces = listOrEmpty(mentionedPlaces);
    }

    private static <T> List<T> listOrEmpty(List<T> value) {
        if (value == null) {
            return List.of();
        }
        return List.copyOf(value);
    }
}
