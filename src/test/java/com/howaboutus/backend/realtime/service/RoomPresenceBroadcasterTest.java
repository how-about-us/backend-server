package com.howaboutus.backend.realtime.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.howaboutus.backend.realtime.event.RoomPresenceChangedEvent;
import com.howaboutus.backend.realtime.service.dto.RoomPresenceEventType;
import com.howaboutus.backend.realtime.service.dto.RoomPresencePayload;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@ExtendWith(MockitoExtension.class)
class RoomPresenceBroadcasterTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private RoomPresenceBroadcaster broadcaster;

    @Test
    @DisplayName("접속 이벤트를 방 topic으로 브로드캐스트한다")
    void broadcastsConnectedEvent() {
        UUID roomId = UUID.randomUUID();

        broadcaster.handlePresenceChanged(
                new RoomPresenceChangedEvent(roomId, 42L, RoomPresenceEventType.USER_CONNECTED)
        );

        ArgumentCaptor<RoomPresencePayload> eventCaptor = ArgumentCaptor.forClass(RoomPresencePayload.class);
        verify(messagingTemplate).convertAndSend(Mockito.eq("/topic/rooms/" + roomId + "/presence"),
                eventCaptor.capture());
        assertThat(eventCaptor.getValue())
                .isEqualTo(new RoomPresencePayload(roomId, 42L, RoomPresenceEventType.USER_CONNECTED));
    }

    @Test
    @DisplayName("접속 해제 이벤트를 방 topic으로 브로드캐스트한다")
    void broadcastsDisconnectedEvent() {
        UUID roomId = UUID.randomUUID();

        broadcaster.handlePresenceChanged(
                new RoomPresenceChangedEvent(roomId, 42L, RoomPresenceEventType.USER_DISCONNECTED)
        );

        ArgumentCaptor<RoomPresencePayload> eventCaptor = ArgumentCaptor.forClass(RoomPresencePayload.class);
        verify(messagingTemplate).convertAndSend(Mockito.eq("/topic/rooms/" + roomId + "/presence"),
                eventCaptor.capture());
        assertThat(eventCaptor.getValue())
                .isEqualTo(new RoomPresencePayload(roomId, 42L, RoomPresenceEventType.USER_DISCONNECTED));
    }
}
