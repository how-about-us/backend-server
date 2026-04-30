package com.howaboutus.backend.messages.controller.dto;

import com.howaboutus.backend.messages.service.dto.SendAiMessageCommand;

public record SendAiMessageRequest(
        String clientMessageId,
        String content
) {

    public static SendAiMessageCommand toCommand(SendAiMessageRequest request) {
        return new SendAiMessageCommand(request.clientMessageId, request.content);
    }
}
