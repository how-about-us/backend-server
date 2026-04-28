package com.howaboutus.backend.messages.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.howaboutus.backend.common.error.CustomException;
import com.howaboutus.backend.common.error.ErrorCode;
import com.howaboutus.backend.messages.document.ChatMessage;
import com.howaboutus.backend.messages.document.MessageType;
import com.howaboutus.backend.messages.repository.ChatMessageRepository;
import com.howaboutus.backend.messages.service.dto.MessageResult;
import com.howaboutus.backend.messages.service.dto.SendChatMessageCommand;
import com.howaboutus.backend.messages.service.dto.SendPlaceMessageCommand;
import com.howaboutus.backend.realtime.event.MessageSentEvent;
import com.howaboutus.backend.rooms.service.RoomAuthorizationService;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private RoomAuthorizationService roomAuthorizationService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private MessageService messageService;

    @BeforeEach
    void setUp() {
        messageService = new MessageService(chatMessageRepository, roomAuthorizationService, eventPublisher);
    }

    @Test
    @DisplayName("활성 방 멤버는 채팅 메시지를 MongoDB에 저장할 수 있다")
    void sendStoresChatMessage() {
        UUID roomId = UUID.randomUUID();
        SendChatMessageCommand command = new SendChatMessageCommand("client-1", " 안녕 ");

        given(chatMessageRepository.save(any(ChatMessage.class))).willAnswer(invocation -> {
            ChatMessage message = invocation.getArgument(0);
            ReflectionTestUtils.setField(message, "id", "6628f5f4c49a9f7b3772c111");
            return message;
        });

        MessageResult result = messageService.send(roomId, command, 42L);

        assertThat(result.id()).isEqualTo("6628f5f4c49a9f7b3772c111");
        assertThat(result.roomId()).isEqualTo(roomId);
        assertThat(result.senderId()).isEqualTo(42L);
        assertThat(result.messageType()).isEqualTo(MessageType.CHAT);
        assertThat(result.content()).isEqualTo("안녕");
        assertThat(result.clientMessageId()).isEqualTo("client-1");

        verify(roomAuthorizationService).requireActiveMember(roomId, 42L);
        ArgumentCaptor<ChatMessage> messageCaptor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageRepository).save(messageCaptor.capture());
        assertThat(messageCaptor.getValue().getContent()).isEqualTo("안녕");
        assertThat(messageCaptor.getValue().getMetadata()).isEmpty();
        verify(eventPublisher).publishEvent(new MessageSentEvent(
                result.id(),
                result.clientMessageId(),
                result.roomId(),
                result.senderId(),
                result.messageType(),
                result.content(),
                result.metadata(),
                result.createdAt()
        ));
    }

    @Test
    @DisplayName("활성 방 멤버는 장소 공유 메시지를 MongoDB에 저장할 수 있다")
    void sharePlaceStoresPlaceShareMessage() {
        UUID roomId = UUID.randomUUID();
        SendPlaceMessageCommand command = new SendPlaceMessageCommand(
                "client-1",
                "google-place-1",
                "광안리 해수욕장",
                "부산 수영구 광안해변로",
                35.1532,
                129.1186,
                4.6,
                "places/google-place-1/photos/photo-1"
        );

        given(chatMessageRepository.save(any(ChatMessage.class))).willAnswer(invocation -> {
            ChatMessage message = invocation.getArgument(0);
            ReflectionTestUtils.setField(message, "id", "6628f5f4c49a9f7b3772c222");
            return message;
        });

        MessageResult result = messageService.sharePlace(roomId, command, 42L);

        assertThat(result.messageType()).isEqualTo(MessageType.PLACE_SHARE);
        assertThat(result.content()).isEqualTo("광안리 해수욕장");
        assertThat(result.clientMessageId()).isEqualTo("client-1");
        assertThat(result.metadata())
                .containsEntry("googlePlaceId", "google-place-1")
                .containsEntry("name", "광안리 해수욕장")
                .containsEntry("formattedAddress", "부산 수영구 광안해변로")
                .containsEntry("latitude", 35.1532)
                .containsEntry("longitude", 129.1186)
                .containsEntry("rating", 4.6)
                .containsEntry("photoName", "places/google-place-1/photos/photo-1");

        verify(roomAuthorizationService).requireActiveMember(roomId, 42L);
        ArgumentCaptor<ChatMessage> messageCaptor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageRepository).save(messageCaptor.capture());
        assertThat(messageCaptor.getValue().getMessageType()).isEqualTo(MessageType.PLACE_SHARE);
        verify(eventPublisher).publishEvent(MessageSentEvent.from(result));
    }

    @Test
    @DisplayName("멤버 입장 시스템 메시지는 senderId 없이 MongoDB에 저장할 수 있다")
    void sendMemberJoinedSystemMessageStoresSystemMessage() {
        UUID roomId = UUID.randomUUID();

        given(chatMessageRepository.save(any(ChatMessage.class))).willAnswer(invocation -> {
            ChatMessage message = invocation.getArgument(0);
            ReflectionTestUtils.setField(message, "id", "6628f5f4c49a9f7b3772c333");
            return message;
        });

        MessageResult result = messageService.sendMemberJoinedSystemMessage(
                roomId,
                7L,
                "대기자",
                "https://example.com/profile.png"
        );

        assertThat(result.senderId()).isNull();
        assertThat(result.messageType()).isEqualTo(MessageType.SYSTEM);
        assertThat(result.content()).isEqualTo("대기자님이 방에 참여했습니다");
        assertThat(result.metadata())
                .containsEntry("eventType", "MEMBER_JOINED")
                .containsEntry("userId", 7L)
                .containsEntry("nickname", "대기자")
                .containsEntry("profileImageUrl", "https://example.com/profile.png");

        ArgumentCaptor<ChatMessage> messageCaptor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageRepository).save(messageCaptor.capture());
        assertThat(messageCaptor.getValue().getMessageType()).isEqualTo(MessageType.SYSTEM);
        verify(eventPublisher).publishEvent(MessageSentEvent.from(result));
    }

    @Test
    @DisplayName("공백 메시지는 MESSAGE_CONTENT_BLANK 예외를 던진다")
    void sendThrowsWhenContentIsBlank() {
        UUID roomId = UUID.randomUUID();
        SendChatMessageCommand command = new SendChatMessageCommand("client-1", "   ");

        assertThatThrownBy(() -> messageService.send(roomId, command, 42L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MESSAGE_CONTENT_BLANK);
    }

    @Test
    @DisplayName("1000자를 초과한 메시지는 MESSAGE_CONTENT_TOO_LONG 예외를 던진다")
    void sendThrowsWhenContentIsTooLong() {
        UUID roomId = UUID.randomUUID();
        SendChatMessageCommand command = new SendChatMessageCommand("client-1", "a".repeat(1001));

        assertThatThrownBy(() -> messageService.send(roomId, command, 42L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MESSAGE_CONTENT_TOO_LONG);
    }
}
