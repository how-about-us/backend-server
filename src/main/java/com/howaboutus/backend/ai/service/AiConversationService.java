package com.howaboutus.backend.ai.service;

import com.howaboutus.backend.messages.service.MessageService;
import com.howaboutus.backend.messages.service.dto.MessageResult;
import com.howaboutus.backend.messages.service.dto.SendAiMessageCommand;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AiConversationService {

    private final MessageService messageService;
    private final AiPlanAsyncService aiPlanAsyncService;

    public void requestPlan(UUID roomId, SendAiMessageCommand command, long userId) {
        MessageResult requestMessage = messageService.sendAiRequest(roomId, command, userId);
        aiPlanAsyncService.generatePlan(roomId, requestMessage);
    }
}
