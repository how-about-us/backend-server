package com.howaboutus.backend.messages.controller;

import com.howaboutus.backend.common.error.CustomException;
import com.howaboutus.backend.messages.controller.dto.SendChatMessageRequest;
import com.howaboutus.backend.messages.service.MessageService;
import com.howaboutus.backend.realtime.config.WebSocketSessionAttributes;
import com.howaboutus.backend.realtime.event.MessageSendFailedEvent;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

@Slf4j
@Controller
@RequiredArgsConstructor
public class MessageWebSocketController {

    private final MessageService messageService;
    private final ApplicationEventPublisher eventPublisher;

    @MessageMapping("/rooms/{roomId}/messages/chat")
    public void send(@DestinationVariable UUID roomId,
                     @Payload SendChatMessageRequest request,
                     SimpMessageHeaderAccessor accessor) {
        long userId = extractUserId(accessor);
        String clientMessageId = request == null ? null : request.clientMessageId();
        try {
            messageService.send(roomId, SendChatMessageRequest.toCommand(request), userId);
        } catch (CustomException e) {
            eventPublisher.publishEvent(
                    MessageSendFailedEvent.messageSendFailure(userId, clientMessageId, e.getErrorCode())
            );
        } catch (RuntimeException e) {
            log.warn("Failed to send chat message. roomId={}, userId={}", roomId, userId, e);
            eventPublisher.publishEvent(MessageSendFailedEvent.retryableMessageSendFailure(userId, clientMessageId));
        }
    }

    private long extractUserId(SimpMessageHeaderAccessor accessor) {
        return WebSocketSessionAttributes.requireUserId(accessor.getSessionAttributes());
    }
}
