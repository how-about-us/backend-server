package com.howaboutus.backend.realtime.event;

import com.howaboutus.backend.common.error.ErrorCode;

public record MessageSendFailedEvent(
        long userId,
        String clientMessageId,
        String code,
        String message,
        boolean retryable
) {
    public static MessageSendFailedEvent messageSendFailure(
            long userId,
            String clientMessageId,
            ErrorCode errorCode
    ) {
        return new MessageSendFailedEvent(
                userId,
                clientMessageId,
                errorCode.name(),
                errorCode.getMessage(),
                false
        );
    }

    public static MessageSendFailedEvent retryableMessageSendFailure(long userId, String clientMessageId) {
        return new MessageSendFailedEvent(
                userId,
                clientMessageId,
                "MESSAGE_SEND_FAILED",
                "메시지 전송에 실패했습니다",
                true
        );
    }
}
