package com.howaboutus.backend.realtime.listener;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.howaboutus.backend.realtime.event.RoomPresenceChangedEvent;
import com.howaboutus.backend.realtime.config.WebSocketSessionAttributes;
import com.howaboutus.backend.realtime.service.RoomPresenceService;
import com.howaboutus.backend.realtime.service.dto.RoomPresenceEventType;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

class WebSocketDisconnectListenerTest {

    private RoomPresenceService roomPresenceService;
    private ApplicationEventPublisher eventPublisher;
    private WebSocketDisconnectListener listener;

    @BeforeEach
    void setUp() {
        roomPresenceService = mock(RoomPresenceService.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        listener = new WebSocketDisconnectListener(roomPresenceService, eventPublisher);
    }

    @Test
    @DisplayName("disconnect 이벤트는 구독했던 모든 방의 presence를 제거한다")
    void disconnectRemovesPresenceForSubscribedRooms() {
        UUID firstRoomId = UUID.randomUUID();
        UUID secondRoomId = UUID.randomUUID();
        Map<String, Object> sessionAttributes = new HashMap<>();
        sessionAttributes.put(WebSocketSessionAttributes.USER_ID, 42L);
        sessionAttributes.put(WebSocketSessionAttributes.SUBSCRIBED_ROOM_IDS, Set.of(firstRoomId, secondRoomId));
        Mockito.when(roomPresenceService.disconnect(firstRoomId, 42L, "session-1")).thenReturn(true);
        Mockito.when(roomPresenceService.disconnect(secondRoomId, 42L, "session-1")).thenReturn(false);

        listener.handleDisconnect(disconnectEvent(sessionAttributes));

        verify(roomPresenceService).disconnect(firstRoomId, 42L, "session-1");
        verify(roomPresenceService).disconnect(secondRoomId, 42L, "session-1");
        verify(eventPublisher).publishEvent(
                new RoomPresenceChangedEvent(firstRoomId, 42L, RoomPresenceEventType.USER_DISCONNECTED)
        );
        verify(eventPublisher, never()).publishEvent(
                new RoomPresenceChangedEvent(secondRoomId, 42L, RoomPresenceEventType.USER_DISCONNECTED)
        );
    }

    @Test
    @DisplayName("인증 정보가 없는 disconnect 이벤트는 presence를 제거하지 않는다")
    void disconnectWithoutUserIdDoesNothing() {
        listener.handleDisconnect(disconnectEvent(new HashMap<>()));

        verify(roomPresenceService, never()).disconnect(Mockito.any(), Mockito.any(), Mockito.any());
        verify(eventPublisher, never()).publishEvent(Mockito.any(RoomPresenceChangedEvent.class));
    }

    private SessionDisconnectEvent disconnectEvent(Map<String, Object> sessionAttributes) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.DISCONNECT);
        accessor.setSessionId("session-1");
        accessor.setSessionAttributes(sessionAttributes);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        return new SessionDisconnectEvent(this, message, "session-1", CloseStatus.NORMAL);
    }
}
