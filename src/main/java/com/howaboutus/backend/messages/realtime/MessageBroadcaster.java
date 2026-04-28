package com.howaboutus.backend.messages.realtime;

import com.howaboutus.backend.messages.service.dto.MessageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MessageBroadcaster {

    private final SimpMessagingTemplate messagingTemplate;

    public void broadcast(MessageResult result) {
        messagingTemplate.convertAndSend(
                "/topic/rooms/" + result.roomId() + "/messages",
                MessagePayload.from(result)
        );
    }

    public void sendError(long userId, UserErrorPayload payload) {
        messagingTemplate.convertAndSendToUser(String.valueOf(userId), "/queue/errors", payload);
    }
}
