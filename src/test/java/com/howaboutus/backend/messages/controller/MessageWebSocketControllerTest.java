package com.howaboutus.backend.messages.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.howaboutus.backend.common.error.CustomException;
import com.howaboutus.backend.common.error.ErrorCode;
import com.howaboutus.backend.ai.service.AiConversationService;
import com.howaboutus.backend.messages.controller.dto.SendChatMessageRequest;
import com.howaboutus.backend.messages.controller.dto.SendAiMessageRequest;
import com.howaboutus.backend.messages.controller.dto.SendPlaceMessageRequest;
import com.howaboutus.backend.messages.document.MessageType;
import com.howaboutus.backend.messages.service.MessageService;
import com.howaboutus.backend.messages.service.dto.MessageResult;
import com.howaboutus.backend.messages.service.dto.SendAiMessageCommand;
import com.howaboutus.backend.messages.service.dto.SendChatMessageCommand;
import com.howaboutus.backend.messages.service.dto.SendPlaceMessageCommand;
import com.howaboutus.backend.realtime.event.MessageSendFailedEvent;
import java.lang.reflect.Method;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;

@ExtendWith(MockitoExtension.class)
class MessageWebSocketControllerTest {

    @Mock
    private MessageService messageService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private AiConversationService aiConversationService;

    private MessageWebSocketController controller;

    @BeforeEach
    void setUp() {
        controller = new MessageWebSocketController(messageService, eventPublisher, aiConversationService);
    }

    @Test
    @DisplayName("일반 채팅 전송은 chat 전용 STOMP destination을 사용한다")
    void sendUsesChatMessageDestination() throws NoSuchMethodException {
        Method send = MessageWebSocketController.class.getDeclaredMethod(
                "send",
                UUID.class,
                SendChatMessageRequest.class,
                SimpMessageHeaderAccessor.class
        );

        MessageMapping messageMapping = send.getAnnotation(MessageMapping.class);

        assertThat(messageMapping.value()).containsExactly("/rooms/{roomId}/messages/chat");
    }

    @Test
    @DisplayName("장소 공유 메시지 전송은 place 전용 STOMP destination을 사용한다")
    void sharePlaceUsesPlaceMessageDestination() throws NoSuchMethodException {
        Method send = MessageWebSocketController.class.getDeclaredMethod(
                "sharePlace",
                UUID.class,
                SendPlaceMessageRequest.class,
                SimpMessageHeaderAccessor.class
        );

        MessageMapping messageMapping = send.getAnnotation(MessageMapping.class);

        assertThat(messageMapping.value()).containsExactly("/rooms/{roomId}/messages/place");
    }

    @Test
    @DisplayName("AI 요청 메시지 전송은 ai 전용 STOMP destination을 사용한다")
    void requestAiUsesAiMessageDestination() throws NoSuchMethodException {
        Method send = MessageWebSocketController.class.getDeclaredMethod(
                "requestAi",
                UUID.class,
                SendAiMessageRequest.class,
                SimpMessageHeaderAccessor.class
        );

        MessageMapping messageMapping = send.getAnnotation(MessageMapping.class);

        assertThat(messageMapping.value()).containsExactly("/rooms/{roomId}/messages/ai");
    }

    @Test
    @DisplayName("메시지 전송 성공 시 서비스에 위임하고 컨트롤러에서는 이벤트를 발행하지 않는다")
    void sendDelegatesToServiceWithoutPublishingControllerEvent() {
        UUID roomId = UUID.randomUUID();
        SendChatMessageRequest request = new SendChatMessageRequest("client-1", "안녕");
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
        given(messageService.send(eq(roomId), any(SendChatMessageCommand.class), eq(42L))).willReturn(result);

        controller.send(roomId, request, accessorWithUserId(42L));

        verify(messageService).send(eq(roomId), eq(new SendChatMessageCommand("client-1", "안녕")), eq(42L));
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("장소 공유 메시지 전송 성공 시 서비스에 위임하고 컨트롤러에서는 이벤트를 발행하지 않는다")
    void sharePlaceDelegatesToServiceWithoutPublishingControllerEvent() {
        UUID roomId = UUID.randomUUID();
        SendPlaceMessageRequest request = new SendPlaceMessageRequest(
                "client-1",
                "google-place-1",
                "광안리 해수욕장",
                "부산 수영구 광안해변로",
                35.1532,
                129.1186,
                4.6,
                "places/google-place-1/photos/photo-1"
        );
        MessageResult result = new MessageResult(
                "6628f5f4c49a9f7b3772c222",
                "client-1",
                roomId,
                42L,
                MessageType.PLACE_SHARE,
                "광안리 해수욕장",
                Map.of("googlePlaceId", "google-place-1"),
                Instant.parse("2026-04-28T00:00:00Z")
        );
        given(messageService.sharePlace(eq(roomId), any(SendPlaceMessageCommand.class), eq(42L))).willReturn(result);

        controller.sharePlace(roomId, request, accessorWithUserId(42L));

        verify(messageService).sharePlace(eq(roomId), eq(new SendPlaceMessageCommand(
                "client-1",
                "google-place-1",
                "광안리 해수욕장",
                "부산 수영구 광안해변로",
                35.1532,
                129.1186,
                4.6,
                "places/google-place-1/photos/photo-1"
        )), eq(42L));
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("AI 요청 성공 시 AI 서비스에 위임하고 컨트롤러에서는 이벤트를 발행하지 않는다")
    void requestAiDelegatesToAiServiceWithoutPublishingControllerEvent() {
        UUID roomId = UUID.randomUUID();
        SendAiMessageRequest request = new SendAiMessageRequest("client-ai-1", "애월 카페 추천해줘");

        controller.requestAi(roomId, request, accessorWithUserId(42L));

        verify(aiConversationService).requestPlan(
                eq(roomId),
                eq(new SendAiMessageCommand("client-ai-1", "애월 카페 추천해줘")),
                eq(42L)
        );
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("메시지 전송 실패 시 실패 이벤트를 발행한다")
    void sendPublishesFailureEventWhenMessageServiceFails() {
        UUID roomId = UUID.randomUUID();
        SendChatMessageRequest request = new SendChatMessageRequest("client-1", "   ");
        given(messageService.send(eq(roomId), any(SendChatMessageCommand.class), eq(42L)))
                .willThrow(new CustomException(ErrorCode.MESSAGE_CONTENT_BLANK));

        controller.send(roomId, request, accessorWithUserId(42L));

        verify(eventPublisher).publishEvent(MessageSendFailedEvent.messageSendFailure(
                42L,
                "client-1",
                ErrorCode.MESSAGE_CONTENT_BLANK
        ));
    }

    @Test
    @DisplayName("장소 공유 메시지 전송 실패 시 실패 이벤트를 발행한다")
    void sharePlacePublishesFailureEventWhenMessageServiceFails() {
        UUID roomId = UUID.randomUUID();
        SendPlaceMessageRequest request = new SendPlaceMessageRequest(
                "client-1",
                " ",
                "광안리 해수욕장",
                "부산 수영구 광안해변로",
                35.1532,
                129.1186,
                4.6,
                "places/google-place-1/photos/photo-1"
        );
        given(messageService.sharePlace(eq(roomId), any(SendPlaceMessageCommand.class), eq(42L)))
                .willThrow(new CustomException(ErrorCode.MESSAGE_PLACE_ID_BLANK));

        controller.sharePlace(roomId, request, accessorWithUserId(42L));

        verify(eventPublisher).publishEvent(MessageSendFailedEvent.messageSendFailure(
                42L,
                "client-1",
                ErrorCode.MESSAGE_PLACE_ID_BLANK
        ));
    }

    @Test
    @DisplayName("예상하지 못한 메시지 전송 실패 시 재시도 가능한 실패 이벤트를 발행한다")
    void sendPublishesRetryableFailureEventWhenUnexpectedErrorOccurs() {
        UUID roomId = UUID.randomUUID();
        SendChatMessageRequest request = new SendChatMessageRequest("client-1", "안녕");
        given(messageService.send(eq(roomId), any(SendChatMessageCommand.class), eq(42L)))
                .willThrow(new IllegalStateException("temporary failure"));

        controller.send(roomId, request, accessorWithUserId(42L));

        verify(eventPublisher).publishEvent(
                MessageSendFailedEvent.retryableMessageSendFailure(42L, "client-1")
        );
    }

    private SimpMessageHeaderAccessor accessorWithUserId(Long userId) {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create();
        Map<String, Object> sessionAttributes = new HashMap<>();
        sessionAttributes.put("userId", userId);
        accessor.setSessionAttributes(sessionAttributes);
        return accessor;
    }
}
