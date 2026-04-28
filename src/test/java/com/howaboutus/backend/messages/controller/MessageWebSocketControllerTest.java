package com.howaboutus.backend.messages.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.howaboutus.backend.common.error.CustomException;
import com.howaboutus.backend.common.error.ErrorCode;
import com.howaboutus.backend.messages.controller.dto.SendMessageRequest;
import com.howaboutus.backend.messages.document.MessageType;
import com.howaboutus.backend.messages.realtime.MessageBroadcaster;
import com.howaboutus.backend.messages.realtime.UserErrorPayload;
import com.howaboutus.backend.messages.service.MessageService;
import com.howaboutus.backend.messages.service.dto.MessageResult;
import com.howaboutus.backend.messages.service.dto.SendMessageCommand;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;

@ExtendWith(MockitoExtension.class)
class MessageWebSocketControllerTest {

    @Mock
    private MessageService messageService;

    @Mock
    private MessageBroadcaster messageBroadcaster;

    private MessageWebSocketController controller;

    @BeforeEach
    void setUp() {
        controller = new MessageWebSocketController(messageService, messageBroadcaster);
    }

    @Test
    @DisplayName("메시지 전송 성공 시 저장 결과를 방 topic으로 브로드캐스트한다")
    void sendBroadcastsSavedMessage() {
        UUID roomId = UUID.randomUUID();
        SendMessageRequest request = new SendMessageRequest("client-1", "안녕", Map.of());
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
        given(messageService.send(eq(roomId), any(SendMessageCommand.class), eq(42L))).willReturn(result);

        controller.send(roomId, request, accessorWithUserId(42L));

        verify(messageBroadcaster).broadcast(result);
    }

    @Test
    @DisplayName("메시지 전송 실패 시 발신자 개인 error queue로 에러를 전송한다")
    void sendSendsUserErrorWhenMessageServiceFails() {
        UUID roomId = UUID.randomUUID();
        SendMessageRequest request = new SendMessageRequest("client-1", "   ", Map.of());
        given(messageService.send(eq(roomId), any(SendMessageCommand.class), eq(42L)))
                .willThrow(new CustomException(ErrorCode.MESSAGE_CONTENT_BLANK));

        controller.send(roomId, request, accessorWithUserId(42L));

        verify(messageBroadcaster).sendError(42L, new UserErrorPayload(
                "MESSAGE",
                "SEND",
                "client-1",
                "MESSAGE_CONTENT_BLANK",
                "메시지는 공백일 수 없습니다",
                false
        ));
    }

    private SimpMessageHeaderAccessor accessorWithUserId(Long userId) {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create();
        Map<String, Object> sessionAttributes = new HashMap<>();
        sessionAttributes.put("userId", userId);
        accessor.setSessionAttributes(sessionAttributes);
        return accessor;
    }
}
