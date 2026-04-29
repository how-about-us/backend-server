package com.howaboutus.backend.messages.listener;

import com.howaboutus.backend.messages.service.MessageService;
import com.howaboutus.backend.realtime.event.HostDelegatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class HostDelegatedMessageListener {

    private final MessageService messageService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handle(HostDelegatedEvent event) {
        messageService.sendHostDelegatedSystemMessage(
                event.roomId(),
                event.previousHostUserId(),
                event.previousHostNickname(),
                event.newHostUserId(),
                event.newHostNickname());
    }
}
