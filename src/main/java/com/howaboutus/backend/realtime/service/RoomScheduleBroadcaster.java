package com.howaboutus.backend.realtime.service;

import com.howaboutus.backend.realtime.event.RoomScheduleChangedEvent;
import com.howaboutus.backend.realtime.service.dto.RoomSchedulePayload;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class RoomScheduleBroadcaster {

    private final SimpMessagingTemplate messagingTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleScheduleChanged(RoomScheduleChangedEvent event) {
        messagingTemplate.convertAndSend(
                "/topic/rooms/" + event.roomId() + "/schedules",
                new RoomSchedulePayload(
                        event.roomId(),
                        event.actorUserId(),
                        event.type(),
                        event.scheduleId(),
                        event.itemId()
                )
        );
    }
}
