package com.howaboutus.backend.ai.service;

import com.howaboutus.backend.common.integration.ai.TravelAiClient;
import com.howaboutus.backend.common.integration.ai.dto.AiChatContext;
import com.howaboutus.backend.common.integration.ai.dto.AiChatMessage;
import com.howaboutus.backend.common.integration.ai.dto.AiChatPlanRequest;
import com.howaboutus.backend.common.integration.ai.dto.AiChatPlanResponse;
import com.howaboutus.backend.common.integration.ai.dto.AiRecommendedPlace;
import com.howaboutus.backend.common.integration.ai.dto.AiRoomContext;
import com.howaboutus.backend.common.integration.ai.dto.AiTravelDateRange;
import com.howaboutus.backend.messages.service.MessageService;
import com.howaboutus.backend.messages.service.dto.MessageResult;
import com.howaboutus.backend.messages.service.dto.SendAiResponseCommand;
import com.howaboutus.backend.rooms.entity.Room;
import com.howaboutus.backend.rooms.entity.RoomRole;
import com.howaboutus.backend.rooms.repository.RoomMemberRepository;
import com.howaboutus.backend.rooms.repository.RoomRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiPlanAsyncService {

    private static final int RECENT_MESSAGE_LIMIT = 3;
    private static final String FAILURE_MESSAGE = "AI 응답을 생성하지 못했습니다. 잠시 후 다시 시도해 주세요.";

    private final TravelAiClient travelAiClient;
    private final MessageService messageService;
    private final AiSummaryService aiSummaryService;
    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;

    @Async
    public void generatePlan(UUID roomId, MessageResult aiRequestMessage) {
        try {
            AiChatPlanResponse response = travelAiClient.chatPlan(buildRequest(roomId, aiRequestMessage));
            messageService.sendAiResponse(roomId, new SendAiResponseCommand(
                    aiRequestMessage.id(),
                    response.answerText(),
                    response.intent(),
                    toRecommendedPlaceMetadata(response.recommendedPlaces())
            ));
            aiSummaryService.completeSummary(roomId, response.updatedSummary(), aiRequestMessage.id());
        } catch (RuntimeException exception) {
            log.warn("AI 응답 생성 실패: roomId={}, requestMessageId={}", roomId, aiRequestMessage.id(), exception);
            messageService.sendAiResponse(roomId, new SendAiResponseCommand(
                    aiRequestMessage.id(),
                    FAILURE_MESSAGE,
                    "unsupported",
                    List.of()
            ));
        }
    }

    private AiChatPlanRequest buildRequest(UUID roomId, MessageResult aiRequestMessage) {
        Room room = roomRepository.findById(roomId).orElse(null);
        List<AiChatMessage> messagesSinceLastSummary =
                aiSummaryService.toAiMessages(aiSummaryService.findMessagesSinceLastSummary(roomId, 30));
        List<AiChatMessage> recentMessages =
                aiSummaryService.toAiMessages(aiSummaryService.findRecentMessages(roomId, RECENT_MESSAGE_LIMIT));
        AiChatMessage requestMessage = new AiChatMessage(
                aiRequestMessage.id(),
                aiRequestMessage.senderId() == null ? null : String.valueOf(aiRequestMessage.senderId()),
                "사용자 " + aiRequestMessage.senderId(),
                aiRequestMessage.createdAt() == null ? null : aiRequestMessage.createdAt().toString(),
                aiRequestMessage.content()
        );

        return new AiChatPlanRequest(
                roomId.toString(),
                roomId.toString(),
                requestMessage,
                toRoomContext(roomId, room),
                new AiChatContext(
                        aiSummaryService.getOrCreate(roomId).summary(),
                        messagesSinceLastSummary,
                        recentMessages
                )
        );
    }

    private AiRoomContext toRoomContext(UUID roomId, Room room) {
        if (room == null) {
            return new AiRoomContext(null, null, null, List.of(), List.of());
        }
        Integer participantsCount = (int) roomMemberRepository.countByRoom_IdAndRoleIn(
                roomId,
                List.of(RoomRole.HOST, RoomRole.MEMBER)
        );
        AiTravelDateRange travelDates = null;
        if (room.getStartDate() != null || room.getEndDate() != null) {
            travelDates = new AiTravelDateRange(
                    room.getStartDate() == null ? null : room.getStartDate().toString(),
                    room.getEndDate() == null ? null : room.getEndDate().toString()
            );
        }
        return new AiRoomContext(room.getDestination(), travelDates, participantsCount, List.of(), List.of());
    }

    private List<Map<String, Object>> toRecommendedPlaceMetadata(List<AiRecommendedPlace> recommendedPlaces) {
        return recommendedPlaces.stream()
                .map(this::toRecommendedPlaceMetadata)
                .toList();
    }

    private Map<String, Object> toRecommendedPlaceMetadata(AiRecommendedPlace place) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        putIfPresent(metadata, "placeId", place.placeId());
        putIfPresent(metadata, "name", place.name());
        putIfPresent(metadata, "address", place.address());
        putIfPresent(metadata, "lat", place.lat());
        putIfPresent(metadata, "lng", place.lng());
        putIfPresent(metadata, "primaryType", place.primaryType());
        putIfPresent(metadata, "reason", place.reason());
        putIfPresent(metadata, "googleMapsUri", place.googleMapsUri());
        return Map.copyOf(metadata);
    }

    private void putIfPresent(Map<String, Object> metadata, String key, Object value) {
        if (value != null) {
            metadata.put(key, value);
        }
    }
}
