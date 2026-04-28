package com.howaboutus.backend.messages.controller.dto;

import com.howaboutus.backend.messages.service.dto.SendMessageCommand;
import java.util.Map;

public record SendMessageRequest(
        String clientMessageId,
        String content,
        Map<String, Object> metadata
) {

    public static SendMessageCommand toCommand(SendMessageRequest request) {
        if (request == null) {
            return new SendMessageCommand(null, null, Map.of());
        }
        return new SendMessageCommand(request.clientMessageId, request.content, request.metadata);
    }
}
