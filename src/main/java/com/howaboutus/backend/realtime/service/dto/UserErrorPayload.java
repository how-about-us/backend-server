package com.howaboutus.backend.realtime.service.dto;

import com.howaboutus.backend.realtime.event.MessageSendFailedEvent;

public record UserErrorPayload(
        String domain,
        String action,
        String clientRequestId,
        String code,
        String message,
        boolean retryable
) {
    public static UserErrorPayload from(MessageSendFailedEvent event) {
        return new UserErrorPayload(
                "MESSAGE",
                "SEND",
                event.clientMessageId(),
                event.code(),
                event.message(),
                event.retryable()
        );
    }
}
