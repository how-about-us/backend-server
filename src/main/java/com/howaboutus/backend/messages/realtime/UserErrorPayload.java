package com.howaboutus.backend.messages.realtime;

import com.howaboutus.backend.common.error.ErrorCode;

public record UserErrorPayload(
        String domain,
        String action,
        String clientRequestId,
        String code,
        String message,
        boolean retryable
) {
    public static UserErrorPayload messageSendFailure(String clientMessageId, ErrorCode errorCode) {
        return new UserErrorPayload(
                "MESSAGE",
                "SEND",
                clientMessageId,
                errorCode.name(),
                errorCode.getMessage(),
                false
        );
    }

    public static UserErrorPayload retryableMessageSendFailure(String clientMessageId) {
        return new UserErrorPayload(
                "MESSAGE",
                "SEND",
                clientMessageId,
                "MESSAGE_SEND_FAILED",
                "메시지 전송에 실패했습니다",
                true
        );
    }
}
