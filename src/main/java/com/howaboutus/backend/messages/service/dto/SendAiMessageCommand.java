package com.howaboutus.backend.messages.service.dto;

public record SendAiMessageCommand(
        String clientMessageId,
        String content
) {
}
