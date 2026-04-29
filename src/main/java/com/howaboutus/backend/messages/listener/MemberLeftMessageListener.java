package com.howaboutus.backend.messages.listener;

import com.howaboutus.backend.messages.service.MessageService;
import com.howaboutus.backend.realtime.event.MemberLeftEvent;
import com.howaboutus.backend.realtime.service.RoomPresenceService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class MemberLeftMessageListener {

    private final MessageService messageService;
    private final RoomPresenceService roomPresenceService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handle(MemberLeftEvent event) {
        removePresenceSafe(event.roomId(), event.leftUserId());
        messageService.sendMemberLeftSystemMessage(
                event.roomId(), event.leftUserId(),
                event.nickname(), event.profileImageUrl());
    }

    private void removePresenceSafe(UUID roomId, long userId) {
        try {
            roomPresenceService.removeAllSessions(roomId, userId);
        } catch (Exception e) {
            log.warn("Redis 접속 상태 제거 실패: roomId={}, userId={}", roomId, userId, e);
        }
    }
}
