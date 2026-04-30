package com.howaboutus.backend.ai.listener;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.howaboutus.backend.ai.service.AiSummaryService;
import com.howaboutus.backend.messages.document.MessageType;
import com.howaboutus.backend.realtime.event.MessageSentEvent;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AiSummaryTriggerListenerTest {

    @Mock
    private AiSummaryService aiSummaryService;

    private AiSummaryTriggerListener listener;

    @BeforeEach
    void setUp() {
        listener = new AiSummaryTriggerListener(aiSummaryService);
    }

    @Test
    @DisplayName("요약 대상 메시지가 저장되면 자동 요약 체크를 요청한다")
    void triggersAutoSummaryForSummarizableMessage() {
        UUID roomId = UUID.randomUUID();

        listener.handleMessageSent(event(roomId, MessageType.CHAT));

        verify(aiSummaryService).triggerAutoSummary(roomId);
    }

    @Test
    @DisplayName("시스템 메시지는 자동 요약 체크 대상에서 제외한다")
    void skipsSystemMessage() {
        UUID roomId = UUID.randomUUID();

        listener.handleMessageSent(event(roomId, MessageType.SYSTEM));

        verify(aiSummaryService, never()).triggerAutoSummary(roomId);
    }

    private MessageSentEvent event(UUID roomId, MessageType messageType) {
        return new MessageSentEvent(
                "message-1",
                null,
                roomId,
                1L,
                messageType,
                "내용",
                Map.of(),
                Instant.parse("2026-04-30T01:00:00Z")
        );
    }
}
