package com.howaboutus.backend.messages.service.dto;

public record SendChatMessageCommand(
        String clientMessageId,
        String content
) {
}
