package com.howaboutus.backend.messages.service.dto;

import com.howaboutus.backend.messages.document.ChatMessage;
import com.howaboutus.backend.messages.document.MessageType;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record MessageResult(
        String id,
        String clientMessageId,
        UUID roomId,
        Long senderId,
        MessageType messageType,
        String content,
        Map<String, Object> metadata,
        Instant createdAt
) {
    public static MessageResult from(ChatMessage message) {
        return from(message, null);
    }

    public static MessageResult from(ChatMessage message, String clientMessageId) {
        return new MessageResult(
                message.getId(),
                clientMessageId,
                message.getRoomId(),
                message.getSenderId(),
                message.getMessageType(),
                message.getContent(),
                message.getMetadata(),
                message.getCreatedAt()
        );
    }
}
