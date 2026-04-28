package com.howaboutus.backend.messages.repository;

import com.howaboutus.backend.messages.document.ChatMessage;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ChatMessageRepository extends MongoRepository<ChatMessage, String> {

    List<ChatMessage> findByRoomIdOrderByCreatedAtDescIdDesc(UUID roomId, Pageable pageable);

    List<ChatMessage> findByRoomIdAndIdGreaterThanOrderByCreatedAtAscIdAsc(UUID roomId, String id, Pageable pageable);
}
