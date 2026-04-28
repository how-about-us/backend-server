package com.howaboutus.backend.messages.document;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

@Getter
@Document(collection = "messages")
@CompoundIndexes({
        @CompoundIndex(name = "idx_messages_room_recent", def = "{'roomId': 1, 'createdAt': -1, '_id': -1}"),
        @CompoundIndex(name = "idx_messages_room_id", def = "{'roomId': 1, '_id': 1}")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessage {

    @Id
    private String id;

    private UUID roomId;
    private Long senderId;
    private MessageType messageType;
    private String content;
    private Map<String, Object> metadata;
    private Instant createdAt;

    private ChatMessage(UUID roomId,
                        Long senderId,
                        MessageType messageType,
                        String content,
                        Map<String, Object> metadata,
                        Instant createdAt) {
        this.roomId = roomId;
        this.senderId = senderId;
        this.messageType = messageType;
        this.content = content;
        this.metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        this.createdAt = createdAt;
    }

    public static ChatMessage chat(UUID roomId, Long senderId, String content) {
        return new ChatMessage(roomId, senderId, MessageType.CHAT, content, Map.of(), Instant.now());
    }

    public static ChatMessage placeShare(UUID roomId, Long senderId, String content, Map<String, Object> metadata) {
        return new ChatMessage(roomId, senderId, MessageType.PLACE_SHARE, content, metadata, Instant.now());
    }

    public static ChatMessage system(UUID roomId, String content, Map<String, Object> metadata) {
        return new ChatMessage(roomId, null, MessageType.SYSTEM, content, metadata, Instant.now());
    }
}
