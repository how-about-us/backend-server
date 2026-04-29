package com.howaboutus.backend.messages.listener;

import static org.mockito.BDDMockito.then;

import com.howaboutus.backend.messages.service.MessageService;
import com.howaboutus.backend.realtime.event.HostDelegatedEvent;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HostDelegatedMessageListenerTest {

    private static final UUID ROOM_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock private MessageService messageService;

    private HostDelegatedMessageListener listener;

    @BeforeEach
    void setUp() {
        listener = new HostDelegatedMessageListener(messageService);
    }

    @Test
    @DisplayName("이벤트 처리 - 시스템 메시지 전송")
    void handleSendsSystemMessage() {
        HostDelegatedEvent event = new HostDelegatedEvent(
                ROOM_ID, 1L, "호스트", 2L, "타겟");

        listener.handle(event);

        then(messageService).should().sendHostDelegatedSystemMessage(
                ROOM_ID, 1L, "호스트", 2L, "타겟");
    }
}
