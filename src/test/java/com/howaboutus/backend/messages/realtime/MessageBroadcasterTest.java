package com.howaboutus.backend.messages.realtime;

import static org.mockito.Mockito.verify;

import com.howaboutus.backend.messages.document.MessageType;
import com.howaboutus.backend.messages.service.dto.MessageResult;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@ExtendWith(MockitoExtension.class)
class MessageBroadcasterTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private MessageBroadcaster broadcaster;

    @BeforeEach
    void setUp() {
        broadcaster = new MessageBroadcaster(messagingTemplate);
    }

    @Test
    @DisplayName("저장된 메시지를 방 messages topic으로 브로드캐스트한다")
    void broadcastsMessageToRoomTopic() {
        UUID roomId = UUID.randomUUID();
        MessageResult result = new MessageResult(
                "6628f5f4c49a9f7b3772c111",
                "client-1",
                roomId,
                42L,
                MessageType.CHAT,
                "안녕",
                Map.of(),
                Instant.parse("2026-04-28T00:00:00Z")
        );

        broadcaster.broadcast(result);

        verify(messagingTemplate).convertAndSend(
                "/topic/rooms/" + roomId + "/messages",
                MessagePayload.from(result)
        );
    }

    @Test
    @DisplayName("개인 에러를 user queue errors로 전송한다")
    void sendsErrorToUserQueue() {
        UserErrorPayload payload = new UserErrorPayload(
                "MESSAGE",
                "SEND",
                "client-1",
                "MESSAGE_CONTENT_BLANK",
                "메시지는 공백일 수 없습니다",
                false
        );

        broadcaster.sendError(42L, payload);

        verify(messagingTemplate).convertAndSendToUser("42", "/queue/errors", payload);
    }
}
