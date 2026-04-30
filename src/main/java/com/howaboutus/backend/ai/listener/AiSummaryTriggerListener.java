package com.howaboutus.backend.ai.listener;

import com.howaboutus.backend.ai.service.AiSummaryService;
import com.howaboutus.backend.messages.document.MessageType;
import com.howaboutus.backend.realtime.event.MessageSentEvent;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiSummaryTriggerListener {

    private static final Set<MessageType> SUMMARIZABLE_TYPES = Set.of(
            MessageType.CHAT,
            MessageType.AI_REQUEST,
            MessageType.AI_RESPONSE,
            MessageType.PLACE_SHARE
    );

    private final AiSummaryService aiSummaryService;

    @Async
    @EventListener
    public void handleMessageSent(MessageSentEvent event) {
        if (!SUMMARIZABLE_TYPES.contains(event.messageType())) {
            return;
        }
        try {
            aiSummaryService.triggerAutoSummary(event.roomId());
        } catch (RuntimeException exception) {
            log.warn("자동 AI 요약 트리거 실패: roomId={}", event.roomId(), exception);
        }
    }
}
