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
import com.howaboutus.backend.messages.service.dto.SendMessageCommand;
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
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private RoomAuthorizationService roomAuthorizationService;

    private MessageService messageService;

    @BeforeEach
    void setUp() {
        messageService = new MessageService(chatMessageRepository, roomAuthorizationService);
    }

    @Test
    @DisplayName("활성 방 멤버는 채팅 메시지를 MongoDB에 저장할 수 있다")
    void sendStoresChatMessage() {
        UUID roomId = UUID.randomUUID();
        SendMessageCommand command = new SendMessageCommand("client-1", " 안녕 ", Map.of());

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
    }

    @Test
    @DisplayName("공백 메시지는 MESSAGE_CONTENT_BLANK 예외를 던진다")
    void sendThrowsWhenContentIsBlank() {
        UUID roomId = UUID.randomUUID();
        SendMessageCommand command = new SendMessageCommand("client-1", "   ", Map.of());

        assertThatThrownBy(() -> messageService.send(roomId, command, 42L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MESSAGE_CONTENT_BLANK);
    }

    @Test
    @DisplayName("1000자를 초과한 메시지는 MESSAGE_CONTENT_TOO_LONG 예외를 던진다")
    void sendThrowsWhenContentIsTooLong() {
        UUID roomId = UUID.randomUUID();
        SendMessageCommand command = new SendMessageCommand("client-1", "a".repeat(1001), Map.of());

        assertThatThrownBy(() -> messageService.send(roomId, command, 42L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MESSAGE_CONTENT_TOO_LONG);
    }
}
