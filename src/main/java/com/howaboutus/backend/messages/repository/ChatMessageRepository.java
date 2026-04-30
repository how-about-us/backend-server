package com.howaboutus.backend.messages.repository;

import com.howaboutus.backend.messages.document.ChatMessage;
import com.howaboutus.backend.messages.document.MessageType;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ChatMessageRepository extends MongoRepository<ChatMessage, String> {

    List<ChatMessage> findByRoomIdOrderByCreatedAtDescIdDesc(UUID roomId, Pageable pageable);

    List<ChatMessage> findByRoomIdAndIdGreaterThanOrderByIdAsc(UUID roomId, String id, Pageable pageable);

    List<ChatMessage> findByRoomIdAndMessageTypeInOrderByIdAsc(
            UUID roomId,
            Collection<MessageType> messageTypes,
            Pageable pageable
    );

    List<ChatMessage> findByRoomIdAndIdGreaterThanAndMessageTypeInOrderByIdAsc(
            UUID roomId,
            String id,
            Collection<MessageType> messageTypes,
            Pageable pageable
    );
}
