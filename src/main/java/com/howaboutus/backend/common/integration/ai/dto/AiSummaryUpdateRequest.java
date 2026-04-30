package com.howaboutus.backend.common.integration.ai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AiSummaryUpdateRequest(
        @JsonProperty("team_id")
        String teamId,
        @JsonProperty("room_id")
        String roomId,
        @JsonProperty("messages_since_last_summary")
        List<AiChatMessage> messagesSinceLastSummary,
        @JsonProperty("previous_summary")
        AiStructuredSummary previousSummary
) {
    public AiSummaryUpdateRequest {
        messagesSinceLastSummary = List.copyOf(messagesSinceLastSummary);
    }
}
