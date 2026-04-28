package com.howaboutus.backend.realtime.listener;

import com.howaboutus.backend.realtime.config.WebSocketSessionAttributes;
import com.howaboutus.backend.realtime.event.RoomPresenceChangedEvent;
import com.howaboutus.backend.realtime.service.RoomPresenceService;
import com.howaboutus.backend.realtime.service.dto.RoomPresenceEventType;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
@RequiredArgsConstructor
public class WebSocketDisconnectListener {

    private final RoomPresenceService roomPresenceService;
    private final ApplicationEventPublisher eventPublisher;

    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        if (sessionAttributes == null) {
            return;
        }

        Object userId = sessionAttributes.get(WebSocketSessionAttributes.USER_ID);
        if (!(userId instanceof Long value)) {
            return;
        }

        String sessionId = accessor.getSessionId();
        subscribedRoomIds(sessionAttributes).forEach(roomId -> {
            boolean disconnected = roomPresenceService.disconnect(roomId, value, sessionId);
            if (disconnected) {
                eventPublisher.publishEvent(
                        new RoomPresenceChangedEvent(roomId, value, RoomPresenceEventType.USER_DISCONNECTED)
                );
            }
        });
    }

    @SuppressWarnings("unchecked")
    private Set<UUID> subscribedRoomIds(Map<String, Object> sessionAttributes) {
        Object roomIds = sessionAttributes.get(WebSocketSessionAttributes.SUBSCRIBED_ROOM_IDS);
        if (roomIds instanceof Set<?>) {
            return (Set<UUID>) roomIds;
        }
        return Set.of();
    }
}
