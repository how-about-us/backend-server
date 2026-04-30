package com.howaboutus.backend.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.howaboutus.backend.ai.document.AiContextSummary;
import com.howaboutus.backend.ai.repository.AiContextSummaryRepository;
import com.howaboutus.backend.common.config.properties.TravelAiProperties;
import com.howaboutus.backend.common.integration.ai.TravelAiClient;
import com.howaboutus.backend.common.integration.ai.dto.AiStructuredSummary;
import com.howaboutus.backend.common.integration.ai.dto.AiSummaryUpdateRequest;
import com.howaboutus.backend.common.integration.ai.dto.AiSummaryUpdateResponse;
import com.howaboutus.backend.messages.document.ChatMessage;
import com.howaboutus.backend.messages.document.MessageType;
import com.howaboutus.backend.messages.repository.ChatMessageRepository;
import com.howaboutus.backend.user.entity.User;
import com.howaboutus.backend.user.repository.UserRepository;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AiSummaryServiceTest {

    @Mock
    private AiContextSummaryRepository summaryRepository;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TravelAiClient travelAiClient;

    private AiSummaryService aiSummaryService;

    @BeforeEach
    void setUp() {
        aiSummaryService = new AiSummaryService(
                summaryRepository,
                chatMessageRepository,
                userRepository,
                travelAiClient,
                new TravelAiProperties("http://localhost:8000", Duration.ofSeconds(30), 30)
        );
    }

    @Test
    @DisplayName("마지막 요약 이후 메시지가 30개 미만이면 자동 요약을 요청하지 않는다")
    void skipsAutoSummaryWhenLessThanBatchSize() {
        UUID roomId = UUID.randomUUID();
        given(summaryRepository.findById(roomId)).willReturn(Optional.empty());
        given(chatMessageRepository.findByRoomIdAndMessageTypeInOrderByIdAsc(
                eq(roomId),
                any(),
                any(Pageable.class)
        )).willReturn(List.of(message(roomId, "msg-1", 1L, MessageType.CHAT, "안녕")));

        aiSummaryService.triggerAutoSummary(roomId);

        verify(travelAiClient, never()).updateSummary(any());
    }

    @Test
    @DisplayName("마지막 요약 이후 메시지가 30개면 AI 서버에 자동 요약을 요청하고 결과를 저장한다")
    void updatesSummaryWhenBatchSizeIsReached() {
        UUID roomId = UUID.randomUUID();
        List<ChatMessage> messages = IntStream.rangeClosed(1, 30)
                .mapToObj(index -> message(roomId, "msg-" + index, 1L, MessageType.CHAT, "메시지 " + index))
                .toList();
        AiStructuredSummary updatedSummary = new AiStructuredSummary(
                "요약됨",
                List.of("애월"),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                "msg-30"
        );

        given(summaryRepository.findById(roomId)).willReturn(Optional.empty());
        given(chatMessageRepository.findByRoomIdAndMessageTypeInOrderByIdAsc(eq(roomId), any(), any(Pageable.class)))
                .willReturn(messages)
                .willReturn(List.of());
        given(userRepository.findAllById(List.of(1L))).willReturn(List.of(user(1L, "민수")));
        given(travelAiClient.updateSummary(any(AiSummaryUpdateRequest.class)))
                .willReturn(new AiSummaryUpdateResponse(roomId.toString(), updatedSummary));

        aiSummaryService.triggerAutoSummary(roomId);

        ArgumentCaptor<AiSummaryUpdateRequest> requestCaptor = ArgumentCaptor.forClass(AiSummaryUpdateRequest.class);
        verify(travelAiClient).updateSummary(requestCaptor.capture());
        assertThat(requestCaptor.getValue().messagesSinceLastSummary()).hasSize(30);
        ArgumentCaptor<AiContextSummary> summaryCaptor = ArgumentCaptor.forClass(AiContextSummary.class);
        verify(summaryRepository, atLeastOnce()).save(summaryCaptor.capture());
        assertThat(summaryCaptor.getAllValues())
                .anySatisfy(summary -> {
                    assertThat(summary.roomId()).isEqualTo(roomId);
                    assertThat(summary.summary()).isEqualTo(updatedSummary);
                    assertThat(summary.lastMessageId()).isEqualTo("msg-30");
                });
    }

    @Test
    @DisplayName("밀린 요약 배치가 많아도 스택을 늘리지 않고 모두 처리한다")
    void processesLargeBacklogWithoutGrowingCallStack() {
        UUID roomId = UUID.randomUUID();
        int messageCount = 5_000;
        aiSummaryService = new AiSummaryService(
                summaryRepository,
                chatMessageRepository,
                userRepository,
                travelAiClient,
                new TravelAiProperties("http://localhost:8000", Duration.ofSeconds(30), 1)
        );
        AtomicReference<AiContextSummary> persistedSummary = new AtomicReference<>(AiContextSummary.init(roomId));

        given(summaryRepository.findById(roomId)).willAnswer(invocation -> Optional.of(persistedSummary.get()));
        given(summaryRepository.save(any(AiContextSummary.class))).willAnswer(invocation -> {
            AiContextSummary saved = invocation.getArgument(0);
            persistedSummary.set(saved);
            return saved;
        });
        given(chatMessageRepository.findByRoomIdAndMessageTypeInOrderByIdAsc(eq(roomId), any(), any(Pageable.class)))
                .willReturn(List.of(message(roomId, "msg-1", null, MessageType.CHAT, "메시지 1")));
        given(chatMessageRepository.findByRoomIdAndIdGreaterThanAndMessageTypeInOrderByIdAsc(
                eq(roomId),
                any(),
                any(),
                any(Pageable.class)
        )).willAnswer(invocation -> {
            String lastMessageId = invocation.getArgument(1);
            int nextIndex = Integer.parseInt(lastMessageId.substring("msg-".length())) + 1;
            if (nextIndex > messageCount) {
                return List.of();
            }
            return List.of(message(roomId, "msg-" + nextIndex, null, MessageType.CHAT, "메시지 " + nextIndex));
        });
        given(travelAiClient.updateSummary(any(AiSummaryUpdateRequest.class))).willAnswer(invocation -> {
            AiSummaryUpdateRequest request = invocation.getArgument(0);
            String lastMessageId = request.messagesSinceLastSummary().getLast().messageId();
            return new AiSummaryUpdateResponse(
                    roomId.toString(),
                    new AiStructuredSummary("요약됨", List.of(), List.of(), List.of(), List.of(), List.of(), lastMessageId)
            );
        });

        assertThatCode(() -> aiSummaryService.triggerAutoSummary(roomId))
                .doesNotThrowAnyException();

        verify(travelAiClient, times(messageCount)).updateSummary(any(AiSummaryUpdateRequest.class));
        assertThat(persistedSummary.get().lastMessageId()).isEqualTo("msg-" + messageCount);
    }

    private ChatMessage message(UUID roomId, String id, Long senderId, MessageType messageType, String content) {
        ChatMessage message = switch (messageType) {
            case AI_REQUEST -> ChatMessage.aiRequest(roomId, senderId, content, Map.of());
            case AI_RESPONSE -> ChatMessage.aiResponse(roomId, content, Map.of());
            case PLACE_SHARE -> ChatMessage.placeShare(roomId, senderId, content, Map.of());
            case SYSTEM -> ChatMessage.system(roomId, content, Map.of());
            case CHAT -> ChatMessage.chat(roomId, senderId, content);
        };
        ReflectionTestUtils.setField(message, "id", id);
        return message;
    }

    private User user(long userId, String nickname) {
        User user = User.ofGoogle("google-" + userId, "u" + userId + "@example.com", nickname, null);
        ReflectionTestUtils.setField(user, "id", userId);
        return user;
    }
}
