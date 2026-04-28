package com.howaboutus.backend.messages.service.dto;

import java.util.Map;

public record SendMessageCommand(
        String clientMessageId,
        String content,
        Map<String, Object> metadata
) {
    public SendMessageCommand {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
