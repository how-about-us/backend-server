package com.howaboutus.backend.realtime.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.howaboutus.backend.realtime.event.RoomScheduleChangedEvent;
import com.howaboutus.backend.realtime.service.dto.RoomScheduleEventType;
import com.howaboutus.backend.realtime.service.dto.RoomSchedulePayload;
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
class RoomScheduleBroadcasterTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private RoomScheduleBroadcaster broadcaster;

    @Test
    @DisplayName("일정 변경 이벤트를 방 schedules topic으로 브로드캐스트한다")
    void broadcastsScheduleChangedEvent() {
        UUID roomId = UUID.randomUUID();
        RoomScheduleChangedEvent event = new RoomScheduleChangedEvent(
                roomId,
                1L,
                RoomScheduleEventType.SCHEDULE_ITEM_CREATED,
                10L,
                20L
        );

        broadcaster.handleScheduleChanged(event);

        ArgumentCaptor<RoomSchedulePayload> eventCaptor = ArgumentCaptor.forClass(RoomSchedulePayload.class);
        verify(messagingTemplate).convertAndSend(Mockito.eq("/topic/rooms/" + roomId + "/schedules"),
                eventCaptor.capture());
        assertThat(eventCaptor.getValue())
                .isEqualTo(new RoomSchedulePayload(roomId, 1L, RoomScheduleEventType.SCHEDULE_ITEM_CREATED, 10L, 20L));
    }
}
