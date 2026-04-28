package com.howaboutus.backend.messages.listener;

import com.howaboutus.backend.messages.service.MessageService;
import com.howaboutus.backend.realtime.event.MemberApprovedEvent;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MemberApprovedMessageListenerTest {

    @Mock
    private MessageService messageService;

    @InjectMocks
    private MemberApprovedMessageListener listener;

    @Test
    @DisplayName("MemberApprovedEvent 수신 시 멤버 입장 시스템 메시지를 저장한다")
    void handleSendsSystemMessage() {
        UUID roomId = UUID.randomUUID();
        MemberApprovedEvent event = new MemberApprovedEvent(roomId, 3L, "대기자", "https://example.com/p.png");

        listener.handle(event);

        verify(messageService).sendMemberJoinedSystemMessage(roomId, 3L, "대기자", "https://example.com/p.png");
    }
}
