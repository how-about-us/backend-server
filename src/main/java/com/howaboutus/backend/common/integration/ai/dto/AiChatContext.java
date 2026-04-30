package com.howaboutus.backend.common.integration.ai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AiChatContext(
        AiStructuredSummary summary,
        @JsonProperty("messages_since_last_summary")
        List<AiChatMessage> messagesSinceLastSummary,
        @JsonProperty("recent_messages")
        List<AiChatMessage> recentMessages
) {
    public AiChatContext {
        messagesSinceLastSummary = listOrEmpty(messagesSinceLastSummary);
        recentMessages = listOrEmpty(recentMessages);
    }

    private static <T> List<T> listOrEmpty(List<T> value) {
        if (value == null) {
            return List.of();
        }
        return List.copyOf(value);
    }
}
