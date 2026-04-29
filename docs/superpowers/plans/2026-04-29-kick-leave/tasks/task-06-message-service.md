### Task 6: MessageService — 시스템 메시지 메서드 2개 추가

**Files:**
- Modify: `src/main/java/com/howaboutus/backend/messages/service/MessageService.java`

- [ ] **Step 1: sendMemberKickedSystemMessage 추가**

`MessageService.java`에 `sendMemberJoinedSystemMessage()` 아래에 추가. 기존 패턴을 동일하게 따른다:

```java
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
    try {
        eventPublisher.publishEvent(MessageSentEvent.from(result));
    } catch (Exception e) {
        log.warn("브로드캐스트 실패, 메시지 저장은 완료: messageId={}", result.id(), e);
    }
    return result;
}
```

- [ ] **Step 2: sendMemberLeftSystemMessage 추가**

```java
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
    try {
        eventPublisher.publishEvent(MessageSentEvent.from(result));
    } catch (Exception e) {
        log.warn("브로드캐스트 실패, 메시지 저장은 완료: messageId={}", result.id(), e);
    }
    return result;
}
```

- [ ] **Step 3: 빌드 확인**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 커밋**

```bash
git add src/main/java/com/howaboutus/backend/messages/service/MessageService.java
git commit -m "feat: MessageService 추방/탈퇴 시스템 메시지 메서드 추가"
```
