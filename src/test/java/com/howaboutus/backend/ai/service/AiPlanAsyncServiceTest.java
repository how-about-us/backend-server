package com.howaboutus.backend.ai.service;

import com.howaboutus.backend.ai.document.AiContextSummary;
import com.howaboutus.backend.common.config.properties.TravelAiProperties;
import com.howaboutus.backend.common.error.ExternalApiException;
import com.howaboutus.backend.common.integration.ai.TravelAiClient;
import com.howaboutus.backend.common.integration.ai.dto.AiChatPlanRequest;
import com.howaboutus.backend.common.integration.ai.dto.AiChatPlanResponse;
import com.howaboutus.backend.common.integration.ai.dto.AiRecommendedPlace;
import com.howaboutus.backend.common.integration.ai.dto.AiStructuredSummary;
import com.howaboutus.backend.messages.document.MessageType;
import com.howaboutus.backend.messages.service.MessageService;
import com.howaboutus.backend.messages.service.dto.MessageResult;
import com.howaboutus.backend.rooms.entity.Room;
import com.howaboutus.backend.rooms.repository.RoomMemberRepository;
import com.howaboutus.backend.rooms.repository.RoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AiPlanAsyncServiceTest {

    @Mock
    private TravelAiClient travelAiClient;

    @Mock
    private MessageService messageService;

    @Mock
    private AiSummaryService aiSummaryService;

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private RoomMemberRepository roomMemberRepository;

    @Mock
    private TravelAiProperties properties;

    private AiPlanAsyncService aiPlanAsyncService;

    @BeforeEach
    void setUp() {
        aiPlanAsyncService = new AiPlanAsyncService(
                travelAiClient,
                messageService,
                aiSummaryService,
                roomRepository,
                roomMemberRepository,
                properties
        );
    }

    @Test
    @DisplayName("AI 서버 응답을 AI_RESPONSE 메시지로 저장하고 요약을 갱신한다")
    void generatesAiResponseAndUpdatesSummary() {
        UUID roomId = UUID.randomUUID();
        MessageResult aiRequest = aiRequest(roomId);
        AiStructuredSummary updatedSummary = new AiStructuredSummary(
                "AI 호출까지 요약",
                List.of(),
                List.of(),
                List.of("조용한 카페"),
                List.of(),
                List.of(),
                "request-1"
        );
        given(roomRepository.findById(roomId)).willReturn(Optional.of(room(roomId)));
        given(roomMemberRepository.countByRoom_IdAndRoleIn(any(), any())).willReturn(3L);
        given(aiSummaryService.findMessagesSinceLastSummary(eq(roomId), anyInt())).willReturn(List.of());
        given(aiSummaryService.findRecentMessages(eq(roomId), anyInt())).willReturn(List.of());
        given(aiSummaryService.toAiMessages(List.of())).willReturn(List.of());
        given(aiSummaryService.getOrCreate(roomId)).willReturn(AiContextSummary.init(roomId));
        given(travelAiClient.chatPlan(any(AiChatPlanRequest.class)))
                .willReturn(new AiChatPlanResponse(
                        "place_recommendation",
                        "애월의 조용한 카페를 추천할게요.",
                        List.of(new AiRecommendedPlace("place-1", "카페 봄날", null, null, null, null, "바다 전망", null)),
                        updatedSummary
                ));

        aiPlanAsyncService.generatePlan(roomId, aiRequest);

        ArgumentCaptor<AiChatPlanRequest> requestCaptor = ArgumentCaptor.forClass(AiChatPlanRequest.class);
        verify(travelAiClient).chatPlan(requestCaptor.capture());
        assertThat(requestCaptor.getValue().teamId()).isEqualTo(roomId.toString());
        assertThat(requestCaptor.getValue().roomContext().destination()).isEqualTo("제주 애월");
        verify(messageService).sendAiResponse(org.mockito.Mockito.eq(roomId), argThat(command ->
                command.requestMessageId().equals("request-1")
                        && command.intent().equals("place_recommendation")
                        && command.recommendedPlaces().size() == 1
        ));
        verify(aiSummaryService).completeSummary(roomId, updatedSummary, "request-1");
    }

    @Test
    @DisplayName("AI 서버 호출 실패 시 실패 안내 AI_RESPONSE를 채팅에 남기고 요약은 갱신하지 않는다")
    void storesFailureAiResponseWhenAiServerFails() {
        UUID roomId = UUID.randomUUID();
        MessageResult aiRequest = aiRequest(roomId);
        given(roomRepository.findById(roomId)).willReturn(Optional.of(room(roomId)));
        given(roomMemberRepository.countByRoom_IdAndRoleIn(any(), any())).willReturn(3L);
        given(aiSummaryService.findMessagesSinceLastSummary(eq(roomId), anyInt())).willReturn(List.of());
        given(aiSummaryService.findRecentMessages(eq(roomId), anyInt())).willReturn(List.of());
        given(aiSummaryService.toAiMessages(List.of())).willReturn(List.of());
        given(aiSummaryService.getOrCreate(roomId)).willReturn(AiContextSummary.idle(roomId, null, null));
        given(travelAiClient.chatPlan(any(AiChatPlanRequest.class)))
                .willThrow(new ExternalApiException(new RuntimeException("timeout")));

        aiPlanAsyncService.generatePlan(roomId, aiRequest);

        verify(messageService).sendAiResponse(org.mockito.Mockito.eq(roomId), argThat(command ->
                command.requestMessageId().equals("request-1")
                        && command.intent().equals("unsupported")
                        && command.content().contains("AI 응답을 생성하지 못했습니다")
        ));
        verify(aiSummaryService, never()).completeSummary(any(), any(), any());
    }

    private MessageResult aiRequest(UUID roomId) {
        return new MessageResult(
                "request-1",
                "client-ai-1",
                roomId,
                42L,
                MessageType.AI_REQUEST,
                "애월 카페 추천해줘",
                Map.of(),
                Instant.parse("2026-04-30T01:00:00Z")
        );
    }

    private Room room(UUID roomId) {
        Room room = Room.create("제주 여행", "제주 애월", LocalDate.parse("2026-05-03"),
                LocalDate.parse("2026-05-05"), "INVITE", 42L);
        ReflectionTestUtils.setField(room, "id", roomId);
        return room;
    }
}
