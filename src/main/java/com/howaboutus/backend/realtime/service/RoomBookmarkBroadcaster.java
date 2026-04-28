package com.howaboutus.backend.realtime.service;

import com.howaboutus.backend.realtime.event.RoomBookmarkChangedEvent;
import com.howaboutus.backend.realtime.service.dto.RoomBookmarkPayload;
import io.github.springwolf.bindings.stomp.annotations.StompAsyncOperationBinding;
import io.github.springwolf.core.asyncapi.annotations.AsyncOperation;
import io.github.springwolf.core.asyncapi.annotations.AsyncPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class RoomBookmarkBroadcaster {

    private final SimpMessagingTemplate messagingTemplate;

    @AsyncPublisher(operation = @AsyncOperation(
            channelName = "/topic/rooms/{roomId}/bookmarks",
            description = "북마크 추가/삭제 시 브로드캐스트. roomId에 해당하는 방을 구독 중인 모든 클라이언트에게 전송됩니다."
    ))
    @StompAsyncOperationBinding
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
