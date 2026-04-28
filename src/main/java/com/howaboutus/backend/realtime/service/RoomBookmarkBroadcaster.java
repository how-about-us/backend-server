package com.howaboutus.backend.realtime.service;

import com.howaboutus.backend.realtime.event.RoomBookmarkChangedEvent;
import com.howaboutus.backend.realtime.service.dto.RoomBookmarkPayload;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class RoomBookmarkBroadcaster {

    private final SimpMessagingTemplate messagingTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleBookmarkChanged(RoomBookmarkChangedEvent event) {
        messagingTemplate.convertAndSend(
                "/topic/rooms/" + event.roomId() + "/bookmarks",
                new RoomBookmarkPayload(
                        event.roomId(),
                        event.actorUserId(),
                        event.type(),
                        event.bookmarkId(),
                        event.categoryId()
                )
        );
    }
}
