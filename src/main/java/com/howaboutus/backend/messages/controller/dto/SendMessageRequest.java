package com.howaboutus.backend.messages.controller.dto;

import com.howaboutus.backend.messages.service.dto.SendMessageCommand;
import java.util.Map;

public record SendMessageRequest(
        String clientMessageId,
        String content,
        Map<String, Object> metadata
) {
    public SendMessageCommand toCommand() {
        return new SendMessageCommand(clientMessageId, content, metadata);
    }
}
