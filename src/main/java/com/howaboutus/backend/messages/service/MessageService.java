package com.howaboutus.backend.messages.service;

import com.howaboutus.backend.common.error.CustomException;
import com.howaboutus.backend.common.error.ErrorCode;
import com.howaboutus.backend.messages.document.ChatMessage;
import com.howaboutus.backend.messages.repository.ChatMessageRepository;
import com.howaboutus.backend.messages.service.dto.MessageResult;
import com.howaboutus.backend.messages.service.dto.SendMessageCommand;
import com.howaboutus.backend.rooms.service.RoomAuthorizationService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MessageService {

    private static final int MAX_CONTENT_LENGTH = 1000;

    private final ChatMessageRepository chatMessageRepository;
    private final RoomAuthorizationService roomAuthorizationService;

    public MessageResult send(UUID roomId, SendMessageCommand command, long userId) {
        roomAuthorizationService.requireActiveMember(roomId, userId);
        String content = normalizeContent(command.content());

        ChatMessage message = ChatMessage.chat(roomId, userId, content, command.metadata());
        ChatMessage savedMessage = chatMessageRepository.save(message);
        return MessageResult.from(savedMessage, command.clientMessageId());
    }

    public List<MessageResult> getRecentMessages(UUID roomId, long userId, int size) {
        roomAuthorizationService.requireActiveMember(roomId, userId);
        validatePageSize(size);
        List<ChatMessage> messages = new ArrayList<>(
                chatMessageRepository.findByRoomIdOrderByCreatedAtDescIdDesc(roomId, PageRequest.of(0, size))
        );
        messages.sort((left, right) -> {
            int createdAtCompare = left.getCreatedAt().compareTo(right.getCreatedAt());
            if (createdAtCompare != 0) {
                return createdAtCompare;
            }
            return left.getId().compareTo(right.getId());
        });
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
        return chatMessageRepository.findByRoomIdAndIdGreaterThanOrderByCreatedAtAscIdAsc(
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

    private void validatePageSize(int size) {
        if (size < 1) {
            throw new CustomException(ErrorCode.INVALID_PAGE_SIZE);
        }
    }
}
