package com.howaboutus.backend.messages.controller;

import com.howaboutus.backend.common.error.CustomException;
import com.howaboutus.backend.common.error.ErrorCode;
import com.howaboutus.backend.messages.controller.dto.SendMessageRequest;
import com.howaboutus.backend.messages.realtime.MessageBroadcaster;
import com.howaboutus.backend.messages.realtime.UserErrorPayload;
import com.howaboutus.backend.messages.service.MessageService;
import com.howaboutus.backend.messages.service.dto.MessageResult;
import com.howaboutus.backend.messages.service.dto.SendMessageCommand;
import com.howaboutus.backend.realtime.config.WebSocketSessionAttributes;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

@Slf4j
@Controller
@RequiredArgsConstructor
public class MessageWebSocketController {

    private final MessageService messageService;
    private final MessageBroadcaster messageBroadcaster;

    @MessageMapping("/rooms/{roomId}/messages")
    public void send(@DestinationVariable UUID roomId,
                     SendMessageRequest request,
                     SimpMessageHeaderAccessor accessor) {
        long userId = extractUserId(accessor);
        String clientMessageId = request == null ? null : request.clientMessageId();
        try {
            MessageResult result = messageService.send(roomId, toCommand(request), userId);
            messageBroadcaster.broadcast(result);
        } catch (CustomException e) {
            messageBroadcaster.sendError(
                    userId,
                    UserErrorPayload.messageSendFailure(clientMessageId, e.getErrorCode())
            );
        } catch (RuntimeException e) {
            log.warn("Failed to send chat message. roomId={}, userId={}", roomId, userId, e);
            messageBroadcaster.sendError(
                    userId,
                    UserErrorPayload.retryableMessageSendFailure(clientMessageId)
            );
        }
    }

    private SendMessageCommand toCommand(SendMessageRequest request) {
        if (request == null) {
            return new SendMessageCommand(null, null, Map.of());
        }
        return request.toCommand();
    }

    private long extractUserId(SimpMessageHeaderAccessor accessor) {
        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        if (sessionAttributes == null) {
            throw new CustomException(ErrorCode.INVALID_TOKEN);
        }
        Object userId = sessionAttributes.get(WebSocketSessionAttributes.USER_ID);
        if (!(userId instanceof Long value)) {
            throw new CustomException(ErrorCode.INVALID_TOKEN);
        }
        return value;
    }
}
