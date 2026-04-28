package com.howaboutus.backend.realtime.service;

import com.howaboutus.backend.realtime.event.MessageSendFailedEvent;
import com.howaboutus.backend.realtime.event.MessageSentEvent;
import com.howaboutus.backend.realtime.service.dto.MessagePayload;
import com.howaboutus.backend.realtime.service.dto.UserErrorPayload;
import io.github.springwolf.bindings.stomp.annotations.StompAsyncOperationBinding;
import io.github.springwolf.core.asyncapi.annotations.AsyncOperation;
import io.github.springwolf.core.asyncapi.annotations.AsyncPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RoomMessageBroadcaster {

    private final SimpMessagingTemplate messagingTemplate;

    @AsyncPublisher(operation = @AsyncOperation(
            channelName = "/topic/rooms/{roomId}/messages",
            description = "채팅 메시지 저장 성공 시 브로드캐스트. roomId에 해당하는 방을 구독 중인 모든 클라이언트에게 전송됩니다.",
            payloadType = MessagePayload.class
    ))
    @StompAsyncOperationBinding
    @EventListener
    public void handleMessageSent(MessageSentEvent event) {
        messagingTemplate.convertAndSend(
                "/topic/rooms/" + event.roomId() + "/messages",
                MessagePayload.from(event)
        );
    }

    @AsyncPublisher(operation = @AsyncOperation(
            channelName = "/user/queue/errors",
            description = "채팅 메시지 전송 실패 시 발신자 개인 error queue로 전송됩니다.",
            payloadType = UserErrorPayload.class
    ))
    @StompAsyncOperationBinding
    @EventListener
    public void handleMessageSendFailed(MessageSendFailedEvent event) {
        messagingTemplate.convertAndSendToUser(
                String.valueOf(event.userId()),
                "/queue/errors",
                UserErrorPayload.from(event)
        );
    }
}
