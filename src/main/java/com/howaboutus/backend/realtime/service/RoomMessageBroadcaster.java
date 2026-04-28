package com.howaboutus.backend.realtime.service;

import com.howaboutus.backend.realtime.event.MessageSendFailedEvent;
import com.howaboutus.backend.realtime.event.MessageSentEvent;
import com.howaboutus.backend.realtime.service.dto.MessagePayload;
import com.howaboutus.backend.realtime.service.dto.UserErrorPayload;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RoomMessageBroadcaster {

    private final SimpMessagingTemplate messagingTemplate;

    @EventListener
    public void handleMessageSent(MessageSentEvent event) {
        messagingTemplate.convertAndSend(
                "/topic/rooms/" + event.roomId() + "/messages",
                MessagePayload.from(event)
        );
    }

    @EventListener
    public void handleMessageSendFailed(MessageSendFailedEvent event) {
        messagingTemplate.convertAndSendToUser(
                String.valueOf(event.userId()),
                "/queue/errors",
                UserErrorPayload.from(event)
        );
    }
}
