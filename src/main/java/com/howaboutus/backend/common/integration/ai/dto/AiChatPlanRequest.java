package com.howaboutus.backend.common.integration.ai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AiChatPlanRequest(
        @JsonProperty("team_id")
        String teamId,
        @JsonProperty("room_id")
        String roomId,
        @JsonProperty("request_message")
        AiChatMessage requestMessage,
        @JsonProperty("room_context")
        AiRoomContext roomContext,
        @JsonProperty("chat_context")
        AiChatContext chatContext
) {
}
