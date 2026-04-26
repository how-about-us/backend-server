package com.howaboutus.backend.realtime.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.howaboutus.backend.realtime.event.RoomPresenceChangedEvent;
import com.howaboutus.backend.realtime.service.dto.RoomPresenceEvent;
import com.howaboutus.backend.realtime.service.dto.RoomPresenceEventType;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.messaging.simp.SimpMessagingTemplate;

class RoomPresenceBroadcasterTest {

    private SimpMessagingTemplate messagingTemplate;
    private RoomPresenceBroadcaster broadcaster;

    @BeforeEach
    void setUp() {
        messagingTemplate = Mockito.mock(SimpMessagingTemplate.class);
        broadcaster = new RoomPresenceBroadcaster(messagingTemplate);
    }

    @Test
    @DisplayName("접속 이벤트를 방 topic으로 브로드캐스트한다")
    void broadcastsConnectedEvent() {
        UUID roomId = UUID.randomUUID();

        broadcaster.handlePresenceChanged(
                new RoomPresenceChangedEvent(roomId, 42L, RoomPresenceEventType.USER_CONNECTED)
        );

        ArgumentCaptor<RoomPresenceEvent> eventCaptor = ArgumentCaptor.forClass(RoomPresenceEvent.class);
        verify(messagingTemplate).convertAndSend(Mockito.eq("/topic/rooms/" + roomId), eventCaptor.capture());
        assertThat(eventCaptor.getValue())
                .isEqualTo(new RoomPresenceEvent(roomId, 42L, RoomPresenceEventType.USER_CONNECTED));
    }

    @Test
    @DisplayName("접속 해제 이벤트를 방 topic으로 브로드캐스트한다")
    void broadcastsDisconnectedEvent() {
        UUID roomId = UUID.randomUUID();

        broadcaster.handlePresenceChanged(
                new RoomPresenceChangedEvent(roomId, 42L, RoomPresenceEventType.USER_DISCONNECTED)
        );

        ArgumentCaptor<RoomPresenceEvent> eventCaptor = ArgumentCaptor.forClass(RoomPresenceEvent.class);
        verify(messagingTemplate).convertAndSend(Mockito.eq("/topic/rooms/" + roomId), eventCaptor.capture());
        assertThat(eventCaptor.getValue())
                .isEqualTo(new RoomPresenceEvent(roomId, 42L, RoomPresenceEventType.USER_DISCONNECTED));
    }
}
