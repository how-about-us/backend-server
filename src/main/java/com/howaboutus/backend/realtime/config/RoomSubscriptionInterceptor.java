package com.howaboutus.backend.realtime.config;

import com.howaboutus.backend.realtime.event.RoomPresenceChangedEvent;
import com.howaboutus.backend.realtime.service.RoomPresenceService;
import com.howaboutus.backend.realtime.service.dto.RoomPresenceEventType;
import com.howaboutus.backend.rooms.service.RoomAuthorizationService;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RoomSubscriptionInterceptor implements ChannelInterceptor {

    private static final Pattern ROOM_TOPIC_PATTERN = Pattern.compile("^/topic/rooms/([^/]+)(?:/.*)?$");

    private final RoomAuthorizationService roomAuthorizationService;
    private final RoomPresenceService roomPresenceService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        if (accessor.getCommand() != StompCommand.SUBSCRIBE) {
            return message;
        }

        UUID roomId = extractRoomId(accessor.getDestination());
        if (roomId == null) {
            return message;
        }

        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        Long userId = extractUserId(sessionAttributes);
        String sessionId = accessor.getSessionId();

        roomAuthorizationService.requireActiveMember(roomId, userId);
        boolean newlyConnected = roomPresenceService.connect(roomId, userId, sessionId);
        rememberSubscribedRoom(sessionAttributes, roomId);
        if (newlyConnected) {
            eventPublisher.publishEvent(
                    new RoomPresenceChangedEvent(roomId, userId, RoomPresenceEventType.USER_CONNECTED)
            );
        }

        return message;
    }

    private UUID extractRoomId(String destination) {
        if (destination == null) {
            return null;
        }
        Matcher matcher = ROOM_TOPIC_PATTERN.matcher(destination);
        if (!matcher.matches()) {
            return null;
        }
        try {
            return UUID.fromString(matcher.group(1));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private Long extractUserId(Map<String, Object> sessionAttributes) {
        return WebSocketSessionAttributes.requireUserId(sessionAttributes);
    }

    @SuppressWarnings("unchecked")
    private void rememberSubscribedRoom(Map<String, Object> sessionAttributes, UUID roomId) {
        Set<UUID> subscribedRoomIds = (Set<UUID>) sessionAttributes.computeIfAbsent(
                WebSocketSessionAttributes.SUBSCRIBED_ROOM_IDS,
                ignored -> new HashSet<UUID>()
        );
        subscribedRoomIds.add(roomId);
    }
}
