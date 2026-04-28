package com.howaboutus.backend.messages.realtime;

import com.howaboutus.backend.messages.document.MessageType;
import com.howaboutus.backend.messages.service.dto.MessageResult;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record MessagePayload(
        String id,
        String clientMessageId,
        UUID roomId,
        Long senderId,
        MessageType messageType,
        String content,
        Map<String, Object> metadata,
        Instant createdAt
) {
    public static MessagePayload from(MessageResult result) {
        return new MessagePayload(
                result.id(),
                result.clientMessageId(),
                result.roomId(),
                result.senderId(),
                result.messageType(),
                result.content(),
                result.metadata(),
                result.createdAt()
        );
    }
}
