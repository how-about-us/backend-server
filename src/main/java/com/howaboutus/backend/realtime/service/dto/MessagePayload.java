package com.howaboutus.backend.realtime.service.dto;

import com.howaboutus.backend.messages.document.MessageType;
import com.howaboutus.backend.realtime.event.MessageSentEvent;
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
    public static MessagePayload from(MessageSentEvent event) {
        return new MessagePayload(
                event.id(),
                event.clientMessageId(),
                event.roomId(),
                event.senderId(),
                event.messageType(),
                event.content(),
                event.metadata(),
                event.createdAt()
        );
    }
}
