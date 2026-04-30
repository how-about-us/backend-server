package com.howaboutus.backend.common.integration.ai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AiChatMessage(
        @JsonProperty("message_id")
        String messageId,
        @JsonProperty("sender_id")
        String senderId,
        @JsonProperty("sender_name")
        String senderName,
        @JsonProperty("sent_at")
        String sentAt,
        String text
) {
}
