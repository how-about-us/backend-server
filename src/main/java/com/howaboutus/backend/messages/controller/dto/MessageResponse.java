package com.howaboutus.backend.messages.controller.dto;

import com.howaboutus.backend.messages.document.MessageType;
import com.howaboutus.backend.messages.service.dto.MessageResult;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record MessageResponse(
        String id,
        UUID roomId,
        Long senderId,
        MessageType messageType,
        String content,
        Map<String, Object> metadata,
        Instant createdAt
) {
    public static MessageResponse from(MessageResult result) {
        return new MessageResponse(
                result.id(),
                result.roomId(),
                result.senderId(),
                result.messageType(),
                result.content(),
                result.metadata(),
                result.createdAt()
        );
    }
}
