package com.howaboutus.backend.realtime.service;

import static org.mockito.Mockito.verify;

import com.howaboutus.backend.common.error.ErrorCode;
import com.howaboutus.backend.messages.document.MessageType;
import com.howaboutus.backend.realtime.event.MessageSendFailedEvent;
import com.howaboutus.backend.realtime.event.MessageSentEvent;
import com.howaboutus.backend.realtime.service.dto.MessagePayload;
import com.howaboutus.backend.realtime.service.dto.UserErrorPayload;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@ExtendWith(MockitoExtension.class)
class RoomMessageBroadcasterTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private RoomMessageBroadcaster broadcaster;

    @BeforeEach
    void setUp() {
        broadcaster = new RoomMessageBroadcaster(messagingTemplate);
    }

    @Test
    @DisplayName("메시지 전송 성공 이벤트를 방 messages topic으로 브로드캐스트한다")
    void broadcastsMessageSentEventToRoomTopic() {
        UUID roomId = UUID.randomUUID();
        MessageSentEvent event = new MessageSentEvent(
                "6628f5f4c49a9f7b3772c111",
                "client-1",
                roomId,
                42L,
                MessageType.CHAT,
                "안녕",
                Map.of(),
                Instant.parse("2026-04-28T00:00:00Z")
        );

        broadcaster.handleMessageSent(event);

        verify(messagingTemplate).convertAndSend(
                "/topic/rooms/" + roomId + "/messages",
                MessagePayload.from(event)
        );
    }

    @Test
    @DisplayName("메시지 전송 실패 이벤트를 user queue errors로 전송한다")
    void sendsMessageFailureEventToUserQueue() {
        MessageSendFailedEvent event = MessageSendFailedEvent.messageSendFailure(
                42L,
                "client-1",
                ErrorCode.MESSAGE_CONTENT_BLANK
        );

        broadcaster.handleMessageSendFailed(event);

        verify(messagingTemplate).convertAndSendToUser("42", "/queue/errors", UserErrorPayload.from(event));
    }
}
