# Room Realtime Connection Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add weakly coupled SockJS + STOMP room realtime connection with cookie-based authentication and room-level subscription authorization.

**Architecture:** REST remains responsible for initial room data. WebSocket handshake authenticates the existing HTTP-only cookie and stores the user id in the WebSocket session; STOMP channel interceptors enforce authenticated `CONNECT` and room-member `SUBSCRIBE`; Redis tracks online users after subscription succeeds.

**Tech Stack:** Spring Boot 4.0.5, Spring WebSocket/STOMP, SockJS, Spring Security, Redis, JUnit.

---

## File Structure

- Create `src/main/java/com/howaboutus/backend/realtime/config/WebSocketConfig.java`: STOMP broker endpoint and SockJS setup.
- Create `src/main/java/com/howaboutus/backend/realtime/config/WebSocketHandshakeInterceptor.java`: extract and validate `access_token` cookie during handshake.
- Create `src/main/java/com/howaboutus/backend/realtime/config/StompAuthenticationInterceptor.java`: validate authenticated STOMP `CONNECT`.
- Create `src/main/java/com/howaboutus/backend/realtime/config/RoomSubscriptionInterceptor.java`: validate `/topic/rooms/{roomId}` subscriptions and mark users online.
- Create `src/main/java/com/howaboutus/backend/realtime/service/RoomPresenceService.java`: online user add/remove/query boundary.
- Create `src/main/java/com/howaboutus/backend/realtime/service/RedisRoomPresenceService.java`: Redis-backed presence implementation.
- Create `src/main/java/com/howaboutus/backend/realtime/service/RoomPresenceBroadcaster.java`: STOMP room topic presence event broadcaster.
- Create `src/main/java/com/howaboutus/backend/realtime/service/dto/RoomPresenceEvent.java`: outbound presence event payload.
- Create `src/main/java/com/howaboutus/backend/realtime/listener/WebSocketDisconnectListener.java`: clean up Redis presence on disconnect.
- Modify `src/main/java/com/howaboutus/backend/common/config/SecurityConfig.java`: allow SockJS endpoint prefix through the HTTP filter chain.
- Modify `docs/ai/features.md`: mark realtime room presence-related items as implemented or partially implemented.
- Test `src/test/java/com/howaboutus/backend/realtime/config/WebSocketHandshakeInterceptorTest.java`.
- Test `src/test/java/com/howaboutus/backend/realtime/config/StompAuthenticationInterceptorTest.java`.
- Test `src/test/java/com/howaboutus/backend/realtime/config/RoomSubscriptionInterceptorTest.java`.
- Test `src/test/java/com/howaboutus/backend/realtime/service/RoomPresenceServiceTest.java`.

## Chunk 1: Handshake Authentication

### Task 1: Cookie Handshake Authentication

- [ ] **Step 1: Write the failing test**

Test that a valid `access_token` cookie causes `WebSocketHandshakeInterceptor` to place `userId` in handshake attributes.

- [ ] **Step 2: Run the focused test**

Run: `GRADLE_USER_HOME=/tmp/.gradle ./gradlew --no-daemon test --tests com.howaboutus.backend.realtime.config.WebSocketHandshakeInterceptorTest`

Expected: FAIL because the interceptor does not exist.

- [ ] **Step 3: Implement the interceptor**

Create `WebSocketHandshakeInterceptor` using `JwtProvider.extractUserId(token)`. Missing or invalid tokens should leave no `userId`; `CONNECT` will reject later.

- [ ] **Step 4: Verify green**

Run the same focused test and expect PASS.

## Chunk 2: STOMP Connection and Subscription

### Task 2: STOMP CONNECT Authentication

- [ ] **Step 1: Write the failing test**

Test that `StompAuthenticationInterceptor` rejects `CONNECT` when no `userId` exists in session attributes and allows it when present.

- [ ] **Step 2: Run the focused test**

Run: `GRADLE_USER_HOME=/tmp/.gradle ./gradlew --no-daemon test --tests com.howaboutus.backend.realtime.config.StompAuthenticationInterceptorTest`

Expected: FAIL because the interceptor does not exist.

- [ ] **Step 3: Implement minimal `CONNECT` validation**

Create the interceptor and throw `CustomException(ErrorCode.INVALID_TOKEN)` for unauthenticated `CONNECT`.

- [ ] **Step 4: Verify green**

Run the focused test and expect PASS.

### Task 3: Room SUBSCRIBE Authorization and Presence

- [ ] **Step 1: Write the failing test**

Test that subscribing to `/topic/rooms/{roomId}` requires an authenticated active room member and calls `RoomPresenceService.connect(roomId, userId, sessionId)`.

- [ ] **Step 2: Run the focused test**

Run: `GRADLE_USER_HOME=/tmp/.gradle ./gradlew --no-daemon test --tests com.howaboutus.backend.realtime.config.RoomSubscriptionInterceptorTest`

Expected: FAIL because the interceptor does not exist.

- [ ] **Step 3: Implement room subscription validation**

Use `RoomAuthorizationService.requireActiveMember(roomId, userId)` and only write presence after it succeeds.

- [ ] **Step 4: Verify green**

Run the focused test and expect PASS.

## Chunk 3: Redis Presence Lifecycle

### Task 4: Redis Presence Service

- [ ] **Step 1: Write the failing test**

Test that `RedisRoomPresenceService` stores online users under `room:{roomId}:connected_users`, stores session ids under `room:{roomId}:sessions:{userId}`, and only removes a user from `connected_users` after the last session ends.

- [ ] **Step 2: Run the focused test**

Run: `GRADLE_USER_HOME=/tmp/.gradle ./gradlew --no-daemon test --tests com.howaboutus.backend.realtime.service.RoomPresenceServiceTest`

Expected: FAIL because the service does not exist.

- [ ] **Step 3: Implement Redis set operations**

Store user ids as strings in Redis set `room:{roomId}:connected_users`. Store active session ids in `room:{roomId}:sessions:{userId}` to keep multi-tab presence correct. Keep subscribed room ids in the WebSocket session attributes for disconnect cleanup.

- [ ] **Step 4: Verify green**

Run the focused test and expect PASS.

### Task 5: Disconnect Cleanup

- [ ] **Step 1: Write or extend tests**

Test that `WebSocketDisconnectListener` removes the session's subscribed room presence.

- [ ] **Step 2: Run focused tests**

Run realtime tests and expect the new disconnect test to fail first.

- [ ] **Step 3: Implement listener**

Handle `SessionDisconnectEvent`, read `roomId`, `userId`, and `sessionId` from STOMP session attributes, and remove online presence.

- [ ] **Step 4: Verify green**

Run realtime tests and expect PASS.

## Chunk 4: Configuration, Docs, and Regression

### Task 6: Wire STOMP/SockJS and Security

- [ ] **Step 1: Add focused configuration test or context load coverage**

Verify the application context can load with the WebSocket configuration.

- [ ] **Step 2: Implement configuration**

Register `/ws` with SockJS, configure simple broker `/topic`, application prefix `/app`, and inbound channel interceptors. Permit `/ws/**` in HTTP security.

- [ ] **Step 3: Update docs**

Update `docs/ai/features.md` to reflect realtime room presence support.

- [ ] **Step 4: Run verification**

Run: `GRADLE_USER_HOME=/tmp/.gradle ./gradlew --no-daemon test`

Expected: PASS.

- [ ] **Step 5: Run markdown conflict check**

Run the checks described in `.claude/skills/checking-md-conflicts/SKILL.md`.
