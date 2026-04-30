package com.howaboutus.backend.messages.controller.dto;

import com.howaboutus.backend.messages.service.dto.SendChatMessageCommand;

public record SendChatMessageRequest(
        String clientMessageId,
        String content
) {

    public static SendChatMessageCommand toCommand(SendChatMessageRequest request) {
        return new SendChatMessageCommand(request.clientMessageId, request.content);
    }
}
