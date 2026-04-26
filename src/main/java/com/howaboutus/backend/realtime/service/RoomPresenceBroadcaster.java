package com.howaboutus.backend.realtime.service;

import com.howaboutus.backend.realtime.event.RoomPresenceChangedEvent;
import com.howaboutus.backend.realtime.service.dto.RoomPresenceEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RoomPresenceBroadcaster {

    private final SimpMessagingTemplate messagingTemplate;

    @EventListener
    public void handlePresenceChanged(RoomPresenceChangedEvent event) {
        messagingTemplate.convertAndSend(
                "/topic/rooms/" + event.roomId(),
                new RoomPresenceEvent(event.roomId(), event.userId(), event.type())
        );
    }
}
