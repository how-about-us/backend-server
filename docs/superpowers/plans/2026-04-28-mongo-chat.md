# Mongo Chat Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build Mongo-backed room chat with STOMP send, room broadcast, sender-only error queue, and message history lookup.

**Architecture:** The `messages` domain owns Mongo documents, service commands, STOMP controller endpoints, HTTP history lookup, and realtime payloads. Room membership remains authorized through the existing PostgreSQL-backed `RoomAuthorizationService`. WebSocket sessions receive a user `Principal` so `/user/queue/errors` works with Spring user destinations.

**Tech Stack:** Spring Boot 4.0.5, Spring Data MongoDB, Spring WebSocket/STOMP, Java 21, JUnit 5, Mockito.

---

## Chunk 1: Realtime User Destination

### Task 1: Add WebSocket Principal Support

**Files:**
- Create: `src/main/java/com/howaboutus/backend/realtime/config/WebSocketUserPrincipal.java`
- Create: `src/main/java/com/howaboutus/backend/realtime/config/UserIdHandshakeHandler.java`
- Modify: `src/main/java/com/howaboutus/backend/realtime/config/WebSocketConfig.java`
- Test: `src/test/java/com/howaboutus/backend/realtime/config/UserIdHandshakeHandlerTest.java`

- [ ] Write a failing test that session attributes with `USER_ID` produce principal name `"42"`.
- [ ] Run the test and verify it fails because the handler does not exist.
- [ ] Implement the principal and handshake handler.
- [ ] Configure the STOMP endpoint to use the handler and enable `/queue` plus `/user`.
- [ ] Run realtime config tests.

## Chunk 2: Mongo Message Domain

### Task 2: Add Message Document, Repository, Service, and DTOs

**Files:**
- Create: `src/main/java/com/howaboutus/backend/messages/document/ChatMessage.java`
- Create: `src/main/java/com/howaboutus/backend/messages/document/MessageType.java`
- Create: `src/main/java/com/howaboutus/backend/messages/repository/ChatMessageRepository.java`
- Create: `src/main/java/com/howaboutus/backend/messages/service/MessageService.java`
- Create: `src/main/java/com/howaboutus/backend/messages/service/dto/SendMessageCommand.java`
- Create: `src/main/java/com/howaboutus/backend/messages/service/dto/MessageResult.java`
- Modify: `src/main/java/com/howaboutus/backend/common/error/ErrorCode.java`
- Test: `src/test/java/com/howaboutus/backend/messages/service/MessageServiceTest.java`

- [ ] Write failing tests for successful send, blank content, and overlong content.
- [ ] Run the tests and verify they fail because the domain does not exist.
- [ ] Implement the minimal Mongo document, repository, service, and DTOs.
- [ ] Run message service tests.

## Chunk 3: STOMP and HTTP Surfaces

### Task 3: Add Controllers and Broadcaster

**Files:**
- Create: `src/main/java/com/howaboutus/backend/messages/controller/MessageWebSocketController.java`
- Create: `src/main/java/com/howaboutus/backend/messages/controller/MessageController.java`
- Create: `src/main/java/com/howaboutus/backend/messages/controller/dto/SendMessageRequest.java`
- Create: `src/main/java/com/howaboutus/backend/messages/controller/dto/MessageResponse.java`
- Create: `src/main/java/com/howaboutus/backend/messages/realtime/MessageBroadcaster.java`
- Create: `src/main/java/com/howaboutus/backend/messages/realtime/UserErrorPayload.java`
- Test: `src/test/java/com/howaboutus/backend/messages/controller/MessageWebSocketControllerTest.java`
- Test: `src/test/java/com/howaboutus/backend/messages/realtime/MessageBroadcasterTest.java`

- [ ] Write failing tests for success broadcast and sender-only failure queue.
- [ ] Run the tests and verify they fail because controllers/broadcaster do not exist.
- [ ] Implement STOMP message handling and HTTP history lookup.
- [ ] Run message controller and broadcaster tests.

## Chunk 4: Documentation and Verification

### Task 4: Align Docs With Mongo Chat

**Files:**
- Modify: `AGENTS.md`
- Modify: `docs/ai/features.md`
- Modify: `docs/ai/erd.md`
- Create: `docs/ai/decisions/20260428-mongo-chat-storage.md`

- [ ] Update tech stack and chat feature rows to MongoDB.
- [ ] Replace PostgreSQL `messages` table description with Mongo collection notes.
- [ ] Add a decision record for Mongo chat storage.
- [ ] Run markdown conflict checks.
- [ ] Run targeted tests, then compile or full test as practical.
