package com.howaboutus.backend.messages.listener;

import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doThrow;

import com.howaboutus.backend.messages.service.MessageService;
import com.howaboutus.backend.realtime.event.MemberLeftEvent;
import com.howaboutus.backend.realtime.service.RoomPresenceService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MemberLeftMessageListenerTest {

    private static final UUID ROOM_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock private MessageService messageService;
    @Mock private RoomPresenceService roomPresenceService;

    private MemberLeftMessageListener listener;

    @BeforeEach
    void setUp() {
        listener = new MemberLeftMessageListener(messageService, roomPresenceService);
    }

    @Test
    @DisplayName("이벤트 처리 - Redis 정리 + 시스템 메시지 전송")
    void handleRemovesPresenceAndSendsSystemMessage() {
        MemberLeftEvent event = new MemberLeftEvent(ROOM_ID, 2L, "멤버", "https://img/member.jpg");

        listener.handle(event);

        then(roomPresenceService).should().removeAllSessions(ROOM_ID, 2L);
        then(messageService).should().sendMemberLeftSystemMessage(
                ROOM_ID, 2L, "멤버", "https://img/member.jpg");
    }

    @Test
    @DisplayName("Redis 실패 시에도 시스템 메시지 정상 전송")
    void handleSendsMessageEvenWhenRedisFails() {
        MemberLeftEvent event = new MemberLeftEvent(ROOM_ID, 2L, "멤버", "https://img/member.jpg");

        doThrow(new RuntimeException("Redis connection refused"))
                .when(roomPresenceService).removeAllSessions(ROOM_ID, 2L);

        listener.handle(event);

        then(messageService).should().sendMemberLeftSystemMessage(
                ROOM_ID, 2L, "멤버", "https://img/member.jpg");
    }
}
