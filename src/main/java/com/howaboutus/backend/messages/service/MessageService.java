package com.howaboutus.backend.messages.service;

import com.howaboutus.backend.common.error.CustomException;
import com.howaboutus.backend.common.error.ErrorCode;
import com.howaboutus.backend.messages.document.ChatMessage;
import com.howaboutus.backend.messages.repository.ChatMessageRepository;
import com.howaboutus.backend.messages.service.dto.MessageResult;
import com.howaboutus.backend.messages.service.dto.SendAiMessageCommand;
import com.howaboutus.backend.messages.service.dto.SendAiResponseCommand;
import com.howaboutus.backend.messages.service.dto.SendChatMessageCommand;
import com.howaboutus.backend.messages.service.dto.SendPlaceMessageCommand;
import com.howaboutus.backend.realtime.event.MessageSentEvent;
import com.howaboutus.backend.rooms.service.RoomAuthorizationService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageService {

    private static final int MAX_CONTENT_LENGTH = 1000;

    private final ChatMessageRepository chatMessageRepository;
    private final RoomAuthorizationService roomAuthorizationService;
    private final ApplicationEventPublisher eventPublisher;

    public MessageResult send(UUID roomId, SendChatMessageCommand command, long userId) {
        roomAuthorizationService.requireActiveMember(roomId, userId);
        String content = normalizeContent(command.content());

        ChatMessage message = ChatMessage.chat(roomId, userId, content);
        ChatMessage savedMessage = chatMessageRepository.save(message);
        MessageResult result = MessageResult.from(savedMessage, command.clientMessageId());
        publishMessageSent(result);
        return result;
    }

    public MessageResult sharePlace(UUID roomId, SendPlaceMessageCommand command, long userId) {
        roomAuthorizationService.requireActiveMember(roomId, userId);
        String googlePlaceId = normalizeGooglePlaceId(command.googlePlaceId());
        String name = normalizeContent(command.name());

        Map<String, Object> metadata = nonNullMetadata(metadataEntries(
                "googlePlaceId", googlePlaceId,
                "name", name,
                "formattedAddress", command.formattedAddress(),
                "latitude", command.latitude(),
                "longitude", command.longitude(),
                "rating", command.rating(),
                "photoName", command.photoName()
        ));

        ChatMessage message = ChatMessage.placeShare(roomId, userId, name, metadata);
        ChatMessage savedMessage = chatMessageRepository.save(message);
        MessageResult result = MessageResult.from(savedMessage, command.clientMessageId());
        publishMessageSent(result);
        return result;
    }

    public MessageResult sendAiRequest(UUID roomId, SendAiMessageCommand command, long userId) {
        roomAuthorizationService.requireActiveMember(roomId, userId);
        String content = normalizeContent(command.content());

        Map<String, Object> metadata = nonNullMetadata(metadataEntries(
                "clientMessageId", command.clientMessageId()
        ));

        ChatMessage message = ChatMessage.aiRequest(roomId, userId, content, metadata);
        ChatMessage savedMessage = chatMessageRepository.save(message);
        MessageResult result = MessageResult.from(savedMessage, command.clientMessageId());
        publishMessageSent(result);
        return result;
    }

    public MessageResult sendAiResponse(UUID roomId, SendAiResponseCommand command) {
        String content = normalizeContent(command.content());
        Map<String, Object> metadata = nonNullMetadata(metadataEntries(
                "requestMessageId", command.requestMessageId(),
                "intent", command.intent(),
                "recommendedPlaces", command.recommendedPlaces()
        ));

        ChatMessage message = ChatMessage.aiResponse(roomId, content, metadata);
        ChatMessage savedMessage = chatMessageRepository.save(message);
        MessageResult result = MessageResult.from(savedMessage);
        publishMessageSent(result);
        return result;
    }

    public MessageResult sendMemberJoinedSystemMessage(UUID roomId,
                                                       long joinedUserId,
                                                       String nickname,
                                                       String profileImageUrl) {
        String normalizedNickname = normalizeContent(nickname);
        Map<String, Object> metadata = nonNullMetadata(metadataEntries(
                "eventType", "MEMBER_JOINED",
                "userId", joinedUserId,
                "nickname", normalizedNickname,
                "profileImageUrl", profileImageUrl
        ));

        ChatMessage message = ChatMessage.system(roomId, normalizedNickname + "님이 방에 참여했습니다", metadata);
        ChatMessage savedMessage = chatMessageRepository.save(message);
        MessageResult result = MessageResult.from(savedMessage);
        publishMessageSent(result);
        return result;
    }

    public MessageResult sendMemberKickedSystemMessage(UUID roomId,
                                                    long kickedUserId,
                                                    String nickname,
                                                    String profileImageUrl) {
        String normalizedNickname = normalizeContent(nickname);
        Map<String, Object> metadata = nonNullMetadata(metadataEntries(
                "eventType", "MEMBER_KICKED",
                "userId", kickedUserId,
                "nickname", normalizedNickname,
                "profileImageUrl", profileImageUrl
        ));

        ChatMessage message = ChatMessage.system(roomId, normalizedNickname + "님이 방에서 내보내졌습니다", metadata);
        ChatMessage savedMessage = chatMessageRepository.save(message);
        MessageResult result = MessageResult.from(savedMessage);
        publishMessageSent(result);
        return result;
    }

    public MessageResult sendMemberLeftSystemMessage(UUID roomId,
                                                  long leftUserId,
                                                  String nickname,
                                                  String profileImageUrl) {
        String normalizedNickname = normalizeContent(nickname);
        Map<String, Object> metadata = nonNullMetadata(metadataEntries(
                "eventType", "MEMBER_LEFT",
                "userId", leftUserId,
                "nickname", normalizedNickname,
                "profileImageUrl", profileImageUrl
        ));

        ChatMessage message = ChatMessage.system(roomId, normalizedNickname + "님이 방을 나갔습니다", metadata);
        ChatMessage savedMessage = chatMessageRepository.save(message);
        MessageResult result = MessageResult.from(savedMessage);
        publishMessageSent(result);
        return result;
    }

    public MessageResult sendHostDelegatedSystemMessage(UUID roomId,
                                                        long previousHostUserId,
                                                        String previousHostNickname,
                                                        long newHostUserId,
                                                        String newHostNickname) {
        String normalizedPrevNickname = normalizeContent(previousHostNickname);
        String normalizedNewNickname = normalizeContent(newHostNickname);
        Map<String, Object> metadata = nonNullMetadata(metadataEntries(
                "eventType", "HOST_DELEGATED",
                "previousHostUserId", previousHostUserId,
                "previousHostNickname", normalizedPrevNickname,
                "newHostUserId", newHostUserId,
                "newHostNickname", normalizedNewNickname
        ));

        ChatMessage message = ChatMessage.system(
                roomId,
                normalizedPrevNickname + "님이 " + normalizedNewNickname + "님에게 방장을 위임했습니다",
                metadata);
        ChatMessage savedMessage = chatMessageRepository.save(message);
        MessageResult result = MessageResult.from(savedMessage);
        publishMessageSent(result);
        return result;
    }

    public List<MessageResult> getRecentMessages(UUID roomId, long userId, int size) {
        roomAuthorizationService.requireActiveMember(roomId, userId);
        validatePageSize(size);
        List<ChatMessage> messages = new ArrayList<>(
                chatMessageRepository.findByRoomIdOrderByCreatedAtDescIdDesc(roomId, PageRequest.of(0, size))
        );
        messages.sort(Comparator.comparing(ChatMessage::getCreatedAt).thenComparing(ChatMessage::getId));
        return messages.stream()
                .map(MessageResult::from)
                .toList();
    }

    public List<MessageResult> getMessagesAfter(UUID roomId, String afterId, long userId, int size) {
        roomAuthorizationService.requireActiveMember(roomId, userId);
        validatePageSize(size);
        if (afterId == null || afterId.isBlank()) {
            return getRecentMessages(roomId, userId, size);
        }
        return chatMessageRepository.findByRoomIdAndIdGreaterThanOrderByIdAsc(
                        roomId,
                        afterId,
                        PageRequest.of(0, size)
                )
                .stream()
                .map(MessageResult::from)
                .toList();
    }

    private String normalizeContent(String content) {
        if (content == null || content.isBlank()) {
            throw new CustomException(ErrorCode.MESSAGE_CONTENT_BLANK);
        }
        String normalized = content.trim();
        if (normalized.length() > MAX_CONTENT_LENGTH) {
            throw new CustomException(ErrorCode.MESSAGE_CONTENT_TOO_LONG);
        }
        return normalized;
    }

    private String normalizeGooglePlaceId(String googlePlaceId) {
        if (googlePlaceId == null || googlePlaceId.isBlank()) {
            throw new CustomException(ErrorCode.MESSAGE_PLACE_ID_BLANK);
        }
        return googlePlaceId.trim();
    }

    private Map<String, Object> nonNullMetadata(Map<String, Object> metadata) {
        Map<String, Object> filtered = new LinkedHashMap<>();
        metadata.forEach((key, value) -> {
            if (value != null) {
                filtered.put(key, value);
            }
        });
        return Map.copyOf(filtered);
    }

    private Map<String, Object> metadataEntries(Object... entries) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        for (int i = 0; i < entries.length; i += 2) {
            metadata.put((String) entries[i], entries[i + 1]);
        }
        return metadata;
    }

    private void publishMessageSent(MessageResult result) {
        try {
            eventPublisher.publishEvent(MessageSentEvent.from(result));
        } catch (Exception e) {
            log.warn("브로드캐스트 실패, 메시지 저장은 완료: messageId={}", result.id(), e);
        }
    }

    private void validatePageSize(int size) {
        if (size < 1) {
            throw new CustomException(ErrorCode.INVALID_PAGE_SIZE);
        }
    }
}
