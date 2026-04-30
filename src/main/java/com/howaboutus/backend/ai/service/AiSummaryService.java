package com.howaboutus.backend.ai.service;

import com.howaboutus.backend.ai.document.AiContextSummary;
import com.howaboutus.backend.ai.document.AiSummaryStatus;
import com.howaboutus.backend.ai.repository.AiContextSummaryRepository;
import com.howaboutus.backend.common.config.properties.TravelAiProperties;
import com.howaboutus.backend.common.integration.ai.TravelAiClient;
import com.howaboutus.backend.common.integration.ai.dto.AiChatMessage;
import com.howaboutus.backend.common.integration.ai.dto.AiStructuredSummary;
import com.howaboutus.backend.common.integration.ai.dto.AiSummaryUpdateRequest;
import com.howaboutus.backend.common.integration.ai.dto.AiSummaryUpdateResponse;
import com.howaboutus.backend.messages.document.ChatMessage;
import com.howaboutus.backend.messages.document.MessageType;
import com.howaboutus.backend.messages.repository.ChatMessageRepository;
import com.howaboutus.backend.user.entity.User;
import com.howaboutus.backend.user.repository.UserRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiSummaryService {

    private static final List<MessageType> SUMMARIZABLE_TYPES = List.of(
            MessageType.CHAT,
            MessageType.AI_REQUEST,
            MessageType.AI_RESPONSE,
            MessageType.PLACE_SHARE
    );

    private final AiContextSummaryRepository summaryRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;
    private final TravelAiClient travelAiClient;
    private final TravelAiProperties properties;

    public AiContextSummary getOrCreate(UUID roomId) {
        return summaryRepository.findById(roomId)
                .orElseGet(() -> AiContextSummary.init(roomId));
    }

    public void completeSummary(UUID roomId, AiStructuredSummary summary, String fallbackLastMessageId) {
        String lastMessageId = summary.lastMessageId();
        if (lastMessageId == null || lastMessageId.isBlank()) {
            lastMessageId = fallbackLastMessageId;
        }
        AiContextSummary current = getOrCreate(roomId);
        summaryRepository.save(current.complete(summary, lastMessageId));
    }

    public void triggerAutoSummary(UUID roomId) {
        while (true) {
            AiContextSummary current = getOrCreate(roomId);
            if (current.summaryStatus() == AiSummaryStatus.RUNNING && !isStale(current)) {
                return;
            }

            List<ChatMessage> messages = findNextSummarizableMessages(roomId, current.lastMessageId(), properties.summaryBatchSize());
            if (messages.size() < properties.summaryBatchSize()) {
                return;
            }

            String untilMessageId = messages.getLast().getId();
            AiContextSummary runningState;
            try {
                runningState = summaryRepository.save(current.running(messages.getFirst().getId(), untilMessageId));
            } catch (OptimisticLockingFailureException | DuplicateKeyException e) {
                log.warn("요약 동시성 충돌 감지, 스킵. roomId={}", roomId);
                return;
            }
            try {
                AiSummaryUpdateResponse response = travelAiClient.updateSummary(new AiSummaryUpdateRequest(
                        roomId.toString(),
                        roomId.toString(),
                        toAiMessages(messages),
                        current.summary()
                ));
                completeSummary(roomId, response.summary(), untilMessageId);
            } catch (RuntimeException exception) {
                summaryRepository.save(runningState.complete(current.summary(), current.lastMessageId()));
                throw exception;
            }
        }
    }

    public List<ChatMessage> findMessagesSinceLastSummary(UUID roomId, int limit) {
        AiContextSummary current = getOrCreate(roomId);
        return findNextSummarizableMessages(roomId, current.lastMessageId(), limit);
    }

    public List<ChatMessage> findRecentMessages(UUID roomId, int limit) {
        return chatMessageRepository.findByRoomIdOrderByCreatedAtDescIdDesc(roomId, PageRequest.of(0, limit))
                .stream()
                .filter(message -> SUMMARIZABLE_TYPES.contains(message.getMessageType()))
                .sorted(Comparator.comparing(ChatMessage::getCreatedAt).thenComparing(ChatMessage::getId))
                .toList();
    }

    public List<AiChatMessage> toAiMessages(List<ChatMessage> messages) {
        Map<Long, User> users = userRepository.findAllById(senderIds(messages))
                .stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        return messages.stream()
                .map(message -> toAiMessage(message, users))
                .toList();
    }

    private List<ChatMessage> findNextSummarizableMessages(UUID roomId, String lastMessageId, int limit) {
        if (lastMessageId == null || lastMessageId.isBlank()) {
            return chatMessageRepository.findByRoomIdAndMessageTypeInOrderByIdAsc(
                    roomId,
                    SUMMARIZABLE_TYPES,
                    PageRequest.of(0, limit)
            );
        }
        return chatMessageRepository.findByRoomIdAndIdGreaterThanAndMessageTypeInOrderByIdAsc(
                roomId,
                lastMessageId,
                SUMMARIZABLE_TYPES,
                PageRequest.of(0, limit)
        );
    }

    private AiChatMessage toAiMessage(ChatMessage message, Map<Long, User> users) {
        String senderName = "AI";
        if (message.getSenderId() != null) {
            User user = users.get(message.getSenderId());
            senderName = user == null ? "사용자 " + message.getSenderId() : user.getNickname();
        }
        return new AiChatMessage(
                message.getId(),
                message.getSenderId() == null ? null : String.valueOf(message.getSenderId()),
                senderName,
                message.getCreatedAt() == null ? null : message.getCreatedAt().toString(),
                message.getContent()
        );
    }

    private Collection<Long> senderIds(List<ChatMessage> messages) {
        return messages.stream()
                .map(ChatMessage::getSenderId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private boolean isStale(AiContextSummary summary) {
        Instant startedAt = summary.summaryStartedAt();
        if (startedAt == null) {
            return true;
        }
        return startedAt.plus(properties.timeout()).isBefore(Instant.now());
    }
}
