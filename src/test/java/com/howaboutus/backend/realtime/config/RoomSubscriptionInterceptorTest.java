package com.howaboutus.backend.realtime.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.howaboutus.backend.common.error.CustomException;
import com.howaboutus.backend.common.error.ErrorCode;
import com.howaboutus.backend.realtime.event.RoomPresenceChangedEvent;
import com.howaboutus.backend.realtime.service.dto.RoomPresenceEventType;
import com.howaboutus.backend.realtime.service.RoomPresenceService;
import com.howaboutus.backend.rooms.service.RoomAuthorizationService;
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
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;

class RoomSubscriptionInterceptorTest {

    private RoomAuthorizationService roomAuthorizationService;
    private RoomPresenceService roomPresenceService;
    private ApplicationEventPublisher eventPublisher;
    private RoomSubscriptionInterceptor interceptor;
    private MessageChannel channel;

    @BeforeEach
    void setUp() {
        roomAuthorizationService = mock(RoomAuthorizationService.class);
        roomPresenceService = mock(RoomPresenceService.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        interceptor = new RoomSubscriptionInterceptor(
                roomAuthorizationService,
                roomPresenceService,
                eventPublisher
        );
        channel = mock(MessageChannel.class);
    }

    @Test
    @DisplayName("방 topic 구독은 active member 권한을 확인하고 presence를 저장한다")
    void authorizesRoomSubscriptionAndStoresPresence() {
        UUID roomId = UUID.randomUUID();
        Map<String, Object> sessionAttributes = sessionAttributesWithUser(42L);
        Message<byte[]> message = subscribeMessage("/topic/rooms/" + roomId, sessionAttributes);
        Mockito.when(roomPresenceService.connect(roomId, 42L, "session-1")).thenReturn(true);

        Message<?> result = interceptor.preSend(message, channel);

        assertThat(result).isSameAs(message);
        verify(roomAuthorizationService).requireActiveMember(roomId, 42L);
        verify(roomPresenceService).connect(roomId, 42L, "session-1");
        assertThat(sessionAttributes)
                .extracting(WebSocketSessionAttributes.SUBSCRIBED_ROOM_IDS)
                .isInstanceOf(Set.class);
        @SuppressWarnings("unchecked")
        Set<UUID> subscribedRoomIds = (Set<UUID>) sessionAttributes.get(
                WebSocketSessionAttributes.SUBSCRIBED_ROOM_IDS);
        assertThat(subscribedRoomIds)
                .contains(roomId);
        verify(eventPublisher).publishEvent(
                new RoomPresenceChangedEvent(roomId, 42L, RoomPresenceEventType.USER_CONNECTED)
        );
    }

    @Test
    @DisplayName("이미 접속 중인 유저의 추가 구독은 접속 브로드캐스트를 보내지 않는다")
    void doesNotBroadcastWhenUserWasAlreadyOnline() {
        UUID roomId = UUID.randomUUID();
        Map<String, Object> sessionAttributes = sessionAttributesWithUser(42L);
        Message<byte[]> message = subscribeMessage("/topic/rooms/" + roomId, sessionAttributes);
        Mockito.when(roomPresenceService.connect(roomId, 42L, "session-1")).thenReturn(false);

        interceptor.preSend(message, channel);

        verify(eventPublisher, never()).publishEvent(Mockito.any(RoomPresenceChangedEvent.class));
    }

    @Test
    @DisplayName("인증되지 않은 방 topic 구독은 INVALID_TOKEN 예외를 던지고 presence를 저장하지 않는다")
    void rejectsRoomSubscriptionWithoutUserId() {
        UUID roomId = UUID.randomUUID();
        Message<byte[]> message = subscribeMessage("/topic/rooms/" + roomId, new HashMap<>());

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_TOKEN);
        verify(roomAuthorizationService, never()).requireActiveMember(Mockito.any(), Mockito.any());
        verify(roomPresenceService, never()).connect(Mockito.any(), Mockito.any(), Mockito.any());
        verify(eventPublisher, never()).publishEvent(Mockito.any(RoomPresenceChangedEvent.class));
    }

    @Test
    @DisplayName("방 topic 권한 확인이 실패하면 presence를 저장하지 않는다")
    void doesNotStorePresenceWhenAuthorizationFails() {
        UUID roomId = UUID.randomUUID();
        Map<String, Object> sessionAttributes = sessionAttributesWithUser(42L);
        Message<byte[]> message = subscribeMessage("/topic/rooms/" + roomId, sessionAttributes);
        Mockito.doThrow(new CustomException(ErrorCode.NOT_ROOM_MEMBER))
                .when(roomAuthorizationService)
                .requireActiveMember(roomId, 42L);

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_ROOM_MEMBER);
        verify(roomPresenceService, never()).connect(Mockito.any(), Mockito.any(), Mockito.any());
        verify(eventPublisher, never()).publishEvent(Mockito.any(RoomPresenceChangedEvent.class));
    }

    @Test
    @DisplayName("방 topic이 아닌 구독은 권한 확인 없이 통과한다")
    void ignoresNonRoomSubscriptions() {
        Message<byte[]> message = subscribeMessage("/topic/announcements", new HashMap<>());

        Message<?> result = interceptor.preSend(message, channel);

        assertThat(result).isSameAs(message);
        verify(roomAuthorizationService, never()).requireActiveMember(Mockito.any(), Mockito.any());
        verify(roomPresenceService, never()).connect(Mockito.any(), Mockito.any(), Mockito.any());
        verify(eventPublisher, never()).publishEvent(Mockito.any(RoomPresenceChangedEvent.class));
    }

    private Map<String, Object> sessionAttributesWithUser(Long userId) {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(WebSocketSessionAttributes.USER_ID, userId);
        return attributes;
    }

    private Message<byte[]> subscribeMessage(String destination, Map<String, Object> sessionAttributes) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setSessionId("session-1");
        accessor.setDestination(destination);
        accessor.setSessionAttributes(sessionAttributes);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
