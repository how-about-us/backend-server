package com.howaboutus.backend.realtime.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.howaboutus.backend.realtime.event.RoomBookmarkChangedEvent;
import com.howaboutus.backend.realtime.service.dto.RoomBookmarkEventType;
import com.howaboutus.backend.realtime.service.dto.RoomBookmarkPayload;
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
class RoomBookmarkBroadcasterTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private RoomBookmarkBroadcaster broadcaster;

    @Test
    @DisplayName("북마크 변경 이벤트를 방 bookmarks topic으로 브로드캐스트한다")
    void broadcastsBookmarkChangedEvent() {
        UUID roomId = UUID.randomUUID();
        RoomBookmarkChangedEvent event = new RoomBookmarkChangedEvent(
                roomId,
                1L,
                RoomBookmarkEventType.BOOKMARK_CREATED,
                10L,
                20L
        );

        broadcaster.handleBookmarkChanged(event);

        ArgumentCaptor<RoomBookmarkPayload> eventCaptor = ArgumentCaptor.forClass(RoomBookmarkPayload.class);
        verify(messagingTemplate).convertAndSend(Mockito.eq("/topic/rooms/" + roomId + "/bookmarks"),
                eventCaptor.capture());
        assertThat(eventCaptor.getValue())
                .isEqualTo(new RoomBookmarkPayload(roomId, 1L, RoomBookmarkEventType.BOOKMARK_CREATED, 10L, 20L));
    }
}
