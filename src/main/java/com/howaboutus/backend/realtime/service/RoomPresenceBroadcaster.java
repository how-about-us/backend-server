package com.howaboutus.backend.realtime.service;

import com.howaboutus.backend.realtime.event.RoomPresenceChangedEvent;
import com.howaboutus.backend.realtime.service.dto.RoomPresencePayload;
import io.github.springwolf.bindings.stomp.annotations.StompAsyncOperationBinding;
import io.github.springwolf.core.asyncapi.annotations.AsyncOperation;
import io.github.springwolf.core.asyncapi.annotations.AsyncPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RoomPresenceBroadcaster {

    private final SimpMessagingTemplate messagingTemplate;

    @AsyncPublisher(operation = @AsyncOperation(
            channelName = "/topic/rooms/{roomId}/presence",
            description = "방 입장/퇴장 시 브로드캐스트. roomId에 해당하는 방을 구독 중인 모든 클라이언트에게 전송됩니다."
    ))
    @StompAsyncOperationBinding
    @EventListener
    public void handlePresenceChanged(RoomPresenceChangedEvent event) {
        messagingTemplate.convertAndSend(
                "/topic/rooms/" + event.roomId() + "/presence",
                new RoomPresencePayload(event.roomId(), event.userId(), event.type())
        );
    }
}
