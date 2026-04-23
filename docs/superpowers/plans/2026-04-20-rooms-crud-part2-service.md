# Rooms CRUD Part 2: 서비스 계층

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** InviteCodeGenerator, 서비스 DTO, RoomService(방 생성/상세/수정/삭제/목록) 구현 + 단위 테스트

**Architecture:** RoomService가 RoomRepository, RoomMemberRepository, UserRepository, InviteCodeGenerator에 의존. 권한 체크는 서비스 내 private 메서드로 직접 수행.

**Tech Stack:** Spring Boot 4.0, Java 21, JUnit 5, Mockito

**선행:** Part 1(엔티티/리포지토리) 완료 필수

**시리즈:** Part 1(엔티티) → **Part 2/3** → Part 3(컨트롤러+문서)

---

## Task 5: InviteCodeGenerator + 서비스 DTO

**Files:**
- Create: `src/main/java/com/howaboutus/backend/rooms/service/InviteCodeGenerator.java`
- Create: `src/main/java/com/howaboutus/backend/rooms/service/dto/RoomCreateCommand.java`
- Create: `src/main/java/com/howaboutus/backend/rooms/service/dto/RoomUpdateCommand.java`
- Create: `src/main/java/com/howaboutus/backend/rooms/service/dto/RoomDetailResult.java`
- Create: `src/main/java/com/howaboutus/backend/rooms/service/dto/RoomListResult.java`

- [ ] **Step 1: InviteCodeGenerator 작성**

```java
package com.howaboutus.backend.rooms.service;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

@Component
public class InviteCodeGenerator {

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int CODE_LENGTH = 10;
    private final SecureRandom random = new SecureRandom();

    public String generate() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
```

- [ ] **Step 2: RoomCreateCommand**

```java
package com.howaboutus.backend.rooms.service.dto;

import java.time.LocalDate;

public record RoomCreateCommand(
        String title,
        String destination,
        LocalDate startDate,
        LocalDate endDate
) {
}
```

- [ ] **Step 3: RoomUpdateCommand**

```java
package com.howaboutus.backend.rooms.service.dto;

import java.time.LocalDate;

public record RoomUpdateCommand(
        String title,
        String destination,
        LocalDate startDate,
        LocalDate endDate
) {
}
```

- [ ] **Step 4: RoomDetailResult**

```java
package com.howaboutus.backend.rooms.service.dto;

import com.howaboutus.backend.rooms.entity.Room;
import com.howaboutus.backend.rooms.entity.RoomRole;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record RoomDetailResult(
        UUID id,
        String title,
        String destination,
        LocalDate startDate,
        LocalDate endDate,
        String inviteCode,
        long memberCount,
        RoomRole role,
        Instant createdAt
) {
    public static RoomDetailResult of(Room room, RoomRole role, long memberCount) {
        return new RoomDetailResult(
                room.getId(), room.getTitle(), room.getDestination(),
                room.getStartDate(), room.getEndDate(), room.getInviteCode(),
                memberCount, role, room.getCreatedAt());
    }
}
```

- [ ] **Step 5: RoomListResult**

```java
package com.howaboutus.backend.rooms.service.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record RoomListResult(
        List<RoomSummary> rooms,
        Instant nextCursor,
        boolean hasNext
) {
    public record RoomSummary(
            UUID id,
            String title,
            String destination,
            LocalDate startDate,
            LocalDate endDate,
            long memberCount,
            String role,
            Instant joinedAt
    ) {
    }
}
```

- [ ] **Step 6: 빌드 확인**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: 커밋**

```bash
git add src/main/java/com/howaboutus/backend/rooms/service/InviteCodeGenerator.java \
        src/main/java/com/howaboutus/backend/rooms/service/dto/
git commit -m "feat: InviteCodeGenerator, Room 서비스 DTO 추가"
```

---

## Task 6: RoomService — 방 생성

**Files:**
- Create: `src/main/java/com/howaboutus/backend/rooms/service/RoomService.java`
- Create: `src/test/java/com/howaboutus/backend/rooms/service/RoomServiceTest.java`

- [ ] **Step 1: 방 생성 테스트 작성**

```java
package com.howaboutus.backend.rooms.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.howaboutus.backend.auth.entity.User;
import com.howaboutus.backend.auth.repository.UserRepository;
import com.howaboutus.backend.common.error.CustomException;
import com.howaboutus.backend.common.error.ErrorCode;
import com.howaboutus.backend.rooms.entity.Room;
import com.howaboutus.backend.rooms.entity.RoomMember;
import com.howaboutus.backend.rooms.entity.RoomRole;
import com.howaboutus.backend.rooms.repository.RoomMemberRepository;
import com.howaboutus.backend.rooms.repository.RoomRepository;
import com.howaboutus.backend.rooms.service.dto.RoomCreateCommand;
import com.howaboutus.backend.rooms.service.dto.RoomDetailResult;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RoomServiceTest {

    @Mock private RoomRepository roomRepository;
    @Mock private RoomMemberRepository roomMemberRepository;
    @Mock private UserRepository userRepository;
    @Mock private InviteCodeGenerator inviteCodeGenerator;

    private RoomService roomService;

    @BeforeEach
    void setUp() {
        roomService = new RoomService(roomRepository, roomMemberRepository,
                userRepository, inviteCodeGenerator);
    }

    @Test
    @DisplayName("방 생성 시 Room과 HOST RoomMember를 저장하고 결과를 반환한다")
    void createRoomSavesRoomAndHostMember() {
        Long userId = 1L;
        User user = User.ofGoogle("google-id", "test@test.com", "테스터", null);
        ReflectionTestUtils.setField(user, "id", userId);

        Room savedRoom = Room.create("부산 여행", "부산",
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 3),
                "aB3xK9mQ2w", userId);
        ReflectionTestUtils.setField(savedRoom, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(savedRoom, "createdAt", Instant.now());

        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(inviteCodeGenerator.generate()).willReturn("aB3xK9mQ2w");
        given(roomRepository.save(any(Room.class))).willReturn(savedRoom);

        RoomCreateCommand command = new RoomCreateCommand("부산 여행", "부산",
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 3));

        RoomDetailResult result = roomService.create(command, userId);

        assertThat(result.title()).isEqualTo("부산 여행");
        assertThat(result.inviteCode()).isEqualTo("aB3xK9mQ2w");
        assertThat(result.role()).isEqualTo(RoomRole.HOST);
        assertThat(result.memberCount()).isEqualTo(1);

        ArgumentCaptor<RoomMember> memberCaptor = ArgumentCaptor.forClass(RoomMember.class);
        verify(roomMemberRepository).save(memberCaptor.capture());
        assertThat(memberCaptor.getValue().getRole()).isEqualTo(RoomRole.HOST);
    }

    @Test
    @DisplayName("방 생성 시 startDate > endDate면 INVALID_DATE_RANGE 예외")
    void createRoomThrowsWhenStartDateAfterEndDate() {
        RoomCreateCommand command = new RoomCreateCommand("부산 여행", "부산",
                LocalDate.of(2026, 5, 5), LocalDate.of(2026, 5, 3));

        assertThatThrownBy(() -> roomService.create(command, 1L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_DATE_RANGE);
    }
}
```

- [ ] **Step 2: 테스트 실행 — 실패 확인**

Run: `./gradlew test --tests "com.howaboutus.backend.rooms.service.RoomServiceTest"`
Expected: FAIL (RoomService 없음)

- [ ] **Step 3: RoomService 작성 (create + private helpers)**

```java
package com.howaboutus.backend.rooms.service;

import com.howaboutus.backend.auth.entity.User;
import com.howaboutus.backend.auth.repository.UserRepository;
import com.howaboutus.backend.common.error.CustomException;
import com.howaboutus.backend.common.error.ErrorCode;
import com.howaboutus.backend.rooms.entity.Room;
import com.howaboutus.backend.rooms.entity.RoomMember;
import com.howaboutus.backend.rooms.entity.RoomRole;
import com.howaboutus.backend.rooms.repository.RoomMemberRepository;
import com.howaboutus.backend.rooms.repository.RoomRepository;
import com.howaboutus.backend.rooms.service.dto.RoomCreateCommand;
import com.howaboutus.backend.rooms.service.dto.RoomDetailResult;
import com.howaboutus.backend.rooms.service.dto.RoomListResult;
import com.howaboutus.backend.rooms.service.dto.RoomUpdateCommand;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoomService {

    private static final List<RoomRole> ACTIVE_ROLES = List.of(RoomRole.HOST, RoomRole.MEMBER);

    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final UserRepository userRepository;
    private final InviteCodeGenerator inviteCodeGenerator;

    @Transactional
    public RoomDetailResult create(RoomCreateCommand command, Long userId) {
        validateDateRange(command.startDate(), command.endDate());

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        String inviteCode = inviteCodeGenerator.generate();
        Room room = Room.create(command.title(), command.destination(),
                command.startDate(), command.endDate(), inviteCode, userId);
        room = roomRepository.save(room);

        RoomMember hostMember = RoomMember.of(room, user, RoomRole.HOST);
        roomMemberRepository.save(hostMember);

        return RoomDetailResult.of(room, RoomRole.HOST, 1);
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new CustomException(ErrorCode.INVALID_DATE_RANGE);
        }
    }

    private Room getActiveRoom(UUID roomId) {
        return roomRepository.findByIdAndDeletedAtIsNull(roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.ROOM_NOT_FOUND));
    }

    private RoomMember getActiveMember(UUID roomId, Long userId) {
        RoomMember member = roomMemberRepository.findByRoom_IdAndUser_Id(roomId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_ROOM_MEMBER));
        if (member.getRole() == RoomRole.PENDING) {
            throw new CustomException(ErrorCode.NOT_ROOM_MEMBER);
        }
        return member;
    }

    private RoomMember getHostMember(UUID roomId, Long userId) {
        RoomMember member = getActiveMember(roomId, userId);
        if (member.getRole() != RoomRole.HOST) {
            throw new CustomException(ErrorCode.NOT_ROOM_HOST);
        }
        return member;
    }
}
```

- [ ] **Step 4: 테스트 실행 — 성공 확인**

Run: `./gradlew test --tests "com.howaboutus.backend.rooms.service.RoomServiceTest"`
Expected: PASS (2 tests)

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/howaboutus/backend/rooms/service/RoomService.java \
        src/test/java/com/howaboutus/backend/rooms/service/RoomServiceTest.java
git commit -m "feat: RoomService 방 생성 구현"
```

---

## Task 7: RoomService — 방 상세 조회

**Files:**
- Modify: `src/main/java/com/howaboutus/backend/rooms/service/RoomService.java`
- Modify: `src/test/java/com/howaboutus/backend/rooms/service/RoomServiceTest.java`

- [ ] **Step 1: 상세 조회 테스트 추가** (RoomServiceTest에 추가)

```java
@Test
@DisplayName("방 상세 조회 시 방 정보와 요청자 역할, 멤버 수를 반환한다")
void getDetailReturnsRoomInfoWithRoleAndMemberCount() {
    UUID roomId = UUID.randomUUID();
    Long userId = 1L;
    Room room = Room.create("부산 여행", "부산",
            LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 3), "aB3xK9mQ2w", userId);
    ReflectionTestUtils.setField(room, "id", roomId);
    ReflectionTestUtils.setField(room, "createdAt", Instant.now());

    User user = User.ofGoogle("google-id", "test@test.com", "테스터", null);
    ReflectionTestUtils.setField(user, "id", userId);
    RoomMember member = RoomMember.of(room, user, RoomRole.HOST);

    given(roomRepository.findByIdAndDeletedAtIsNull(roomId)).willReturn(Optional.of(room));
    given(roomMemberRepository.findByRoom_IdAndUser_Id(roomId, userId)).willReturn(Optional.of(member));
    given(roomMemberRepository.countByRoom_IdAndRoleIn(roomId, List.of(RoomRole.HOST, RoomRole.MEMBER)))
            .willReturn(3L);

    RoomDetailResult result = roomService.getDetail(roomId, userId);

    assertThat(result.title()).isEqualTo("부산 여행");
    assertThat(result.role()).isEqualTo(RoomRole.HOST);
    assertThat(result.memberCount()).isEqualTo(3);
}

@Test
@DisplayName("삭제된 방을 조회하면 ROOM_NOT_FOUND 예외")
void getDetailThrowsWhenRoomDeleted() {
    UUID roomId = UUID.randomUUID();
    given(roomRepository.findByIdAndDeletedAtIsNull(roomId)).willReturn(Optional.empty());

    assertThatThrownBy(() -> roomService.getDetail(roomId, 1L))
            .isInstanceOf(CustomException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.ROOM_NOT_FOUND);
}

@Test
@DisplayName("방 멤버가 아닌 사용자가 상세 조회하면 NOT_ROOM_MEMBER 예외")
void getDetailThrowsWhenNotMember() {
    UUID roomId = UUID.randomUUID();
    Room room = Room.create("부산 여행", "부산", null, null, "aB3xK9mQ2w", 1L);
    ReflectionTestUtils.setField(room, "id", roomId);

    given(roomRepository.findByIdAndDeletedAtIsNull(roomId)).willReturn(Optional.of(room));
    given(roomMemberRepository.findByRoom_IdAndUser_Id(roomId, 99L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> roomService.getDetail(roomId, 99L))
            .isInstanceOf(CustomException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.NOT_ROOM_MEMBER);
}

@Test
@DisplayName("PENDING 상태 사용자가 상세 조회하면 NOT_ROOM_MEMBER 예외")
void getDetailThrowsWhenPendingMember() {
    UUID roomId = UUID.randomUUID();
    Long userId = 2L;
    Room room = Room.create("부산 여행", "부산", null, null, "aB3xK9mQ2w", 1L);
    ReflectionTestUtils.setField(room, "id", roomId);

    User user = User.ofGoogle("google-id", "pending@test.com", "대기자", null);
    ReflectionTestUtils.setField(user, "id", userId);
    RoomMember pendingMember = RoomMember.of(room, user, RoomRole.PENDING);

    given(roomRepository.findByIdAndDeletedAtIsNull(roomId)).willReturn(Optional.of(room));
    given(roomMemberRepository.findByRoom_IdAndUser_Id(roomId, userId)).willReturn(Optional.of(pendingMember));

    assertThatThrownBy(() -> roomService.getDetail(roomId, userId))
            .isInstanceOf(CustomException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.NOT_ROOM_MEMBER);
}
```

import 추가: `import java.util.List;`

- [ ] **Step 2: 테스트 실행 — 실패 확인**

Run: `./gradlew test --tests "com.howaboutus.backend.rooms.service.RoomServiceTest"`
Expected: FAIL (getDetail 없음)

- [ ] **Step 3: RoomService에 getDetail 추가**

```java
public RoomDetailResult getDetail(UUID roomId, Long userId) {
    Room room = getActiveRoom(roomId);
    RoomMember member = getActiveMember(roomId, userId);
    long memberCount = roomMemberRepository.countByRoom_IdAndRoleIn(roomId, ACTIVE_ROLES);
    return RoomDetailResult.of(room, member.getRole(), memberCount);
}
```

- [ ] **Step 4: 테스트 실행 — 성공 확인**

Run: `./gradlew test --tests "com.howaboutus.backend.rooms.service.RoomServiceTest"`
Expected: PASS (6 tests)

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/howaboutus/backend/rooms/service/RoomService.java \
        src/test/java/com/howaboutus/backend/rooms/service/RoomServiceTest.java
git commit -m "feat: RoomService 방 상세 조회 구현"
```

---

## Task 8: RoomService — 방 수정 + 삭제

**Files:**
- Modify: `src/main/java/com/howaboutus/backend/rooms/service/RoomService.java`
- Modify: `src/test/java/com/howaboutus/backend/rooms/service/RoomServiceTest.java`

- [ ] **Step 1: 수정/삭제 테스트 추가**

```java
@Test
@DisplayName("HOST가 방 수정 시 전달된 필드만 업데이트한다")
void updateRoomUpdatesOnlyProvidedFields() {
    UUID roomId = UUID.randomUUID();
    Long userId = 1L;
    Room room = Room.create("부산 여행", "부산",
            LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 3), "aB3xK9mQ2w", userId);
    ReflectionTestUtils.setField(room, "id", roomId);
    ReflectionTestUtils.setField(room, "createdAt", Instant.now());

    User user = User.ofGoogle("google-id", "test@test.com", "테스터", null);
    ReflectionTestUtils.setField(user, "id", userId);
    RoomMember hostMember = RoomMember.of(room, user, RoomRole.HOST);

    given(roomRepository.findByIdAndDeletedAtIsNull(roomId)).willReturn(Optional.of(room));
    given(roomMemberRepository.findByRoom_IdAndUser_Id(roomId, userId)).willReturn(Optional.of(hostMember));
    given(roomMemberRepository.countByRoom_IdAndRoleIn(roomId, List.of(RoomRole.HOST, RoomRole.MEMBER)))
            .willReturn(1L);

    RoomUpdateCommand command = new RoomUpdateCommand("부산 맛집 여행", null, null, null);
    RoomDetailResult result = roomService.update(roomId, command, userId);

    assertThat(result.title()).isEqualTo("부산 맛집 여행");
    assertThat(result.destination()).isEqualTo("부산");
}

@Test
@DisplayName("MEMBER가 방 수정하면 NOT_ROOM_HOST 예외")
void updateRoomThrowsWhenNotHost() {
    UUID roomId = UUID.randomUUID();
    Long userId = 2L;
    Room room = Room.create("부산 여행", "부산", null, null, "aB3xK9mQ2w", 1L);
    ReflectionTestUtils.setField(room, "id", roomId);

    User user = User.ofGoogle("google-id", "member@test.com", "멤버", null);
    ReflectionTestUtils.setField(user, "id", userId);
    RoomMember normalMember = RoomMember.of(room, user, RoomRole.MEMBER);

    given(roomRepository.findByIdAndDeletedAtIsNull(roomId)).willReturn(Optional.of(room));
    given(roomMemberRepository.findByRoom_IdAndUser_Id(roomId, userId)).willReturn(Optional.of(normalMember));

    RoomUpdateCommand command = new RoomUpdateCommand("변경", null, null, null);

    assertThatThrownBy(() -> roomService.update(roomId, command, userId))
            .isInstanceOf(CustomException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.NOT_ROOM_HOST);
}

@Test
@DisplayName("방 수정 시 startDate > endDate면 INVALID_DATE_RANGE 예외")
void updateRoomThrowsWhenInvalidDateRange() {
    UUID roomId = UUID.randomUUID();
    Long userId = 1L;
    Room room = Room.create("부산 여행", "부산", null, null, "aB3xK9mQ2w", userId);
    ReflectionTestUtils.setField(room, "id", roomId);

    User user = User.ofGoogle("google-id", "test@test.com", "테스터", null);
    ReflectionTestUtils.setField(user, "id", userId);
    RoomMember hostMember = RoomMember.of(room, user, RoomRole.HOST);

    given(roomRepository.findByIdAndDeletedAtIsNull(roomId)).willReturn(Optional.of(room));
    given(roomMemberRepository.findByRoom_IdAndUser_Id(roomId, userId)).willReturn(Optional.of(hostMember));

    RoomUpdateCommand command = new RoomUpdateCommand(null, null,
            LocalDate.of(2026, 5, 5), LocalDate.of(2026, 5, 3));

    assertThatThrownBy(() -> roomService.update(roomId, command, userId))
            .isInstanceOf(CustomException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.INVALID_DATE_RANGE);
}

@Test
@DisplayName("HOST가 방 삭제 시 soft delete 처리된다")
void deleteRoomSoftDeletes() {
    UUID roomId = UUID.randomUUID();
    Long userId = 1L;
    Room room = Room.create("부산 여행", "부산", null, null, "aB3xK9mQ2w", userId);
    ReflectionTestUtils.setField(room, "id", roomId);

    User user = User.ofGoogle("google-id", "test@test.com", "테스터", null);
    ReflectionTestUtils.setField(user, "id", userId);
    RoomMember hostMember = RoomMember.of(room, user, RoomRole.HOST);

    given(roomRepository.findByIdAndDeletedAtIsNull(roomId)).willReturn(Optional.of(room));
    given(roomMemberRepository.findByRoom_IdAndUser_Id(roomId, userId)).willReturn(Optional.of(hostMember));

    roomService.delete(roomId, userId);

    assertThat(room.isDeleted()).isTrue();
    assertThat(room.getDeletedAt()).isNotNull();
}

@Test
@DisplayName("MEMBER가 방 삭제하면 NOT_ROOM_HOST 예외")
void deleteRoomThrowsWhenNotHost() {
    UUID roomId = UUID.randomUUID();
    Long userId = 2L;
    Room room = Room.create("부산 여행", "부산", null, null, "aB3xK9mQ2w", 1L);
    ReflectionTestUtils.setField(room, "id", roomId);

    User user = User.ofGoogle("google-id", "member@test.com", "멤버", null);
    ReflectionTestUtils.setField(user, "id", userId);
    RoomMember normalMember = RoomMember.of(room, user, RoomRole.MEMBER);

    given(roomRepository.findByIdAndDeletedAtIsNull(roomId)).willReturn(Optional.of(room));
    given(roomMemberRepository.findByRoom_IdAndUser_Id(roomId, userId)).willReturn(Optional.of(normalMember));

    assertThatThrownBy(() -> roomService.delete(roomId, userId))
            .isInstanceOf(CustomException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.NOT_ROOM_HOST);
}
```

import 추가: `import com.howaboutus.backend.rooms.service.dto.RoomUpdateCommand;`

- [ ] **Step 2: 테스트 실행 — 실패 확인**

Run: `./gradlew test --tests "com.howaboutus.backend.rooms.service.RoomServiceTest"`
Expected: FAIL (update, delete 없음)

- [ ] **Step 3: RoomService에 update, delete 추가**

```java
@Transactional
public RoomDetailResult update(UUID roomId, RoomUpdateCommand command, Long userId) {
    Room room = getActiveRoom(roomId);
    getHostMember(roomId, userId);

    LocalDate newStart = command.startDate() != null ? command.startDate() : room.getStartDate();
    LocalDate newEnd = command.endDate() != null ? command.endDate() : room.getEndDate();
    validateDateRange(newStart, newEnd);

    room.update(command.title(), command.destination(), command.startDate(), command.endDate());

    long memberCount = roomMemberRepository.countByRoom_IdAndRoleIn(roomId, ACTIVE_ROLES);
    return RoomDetailResult.of(room, RoomRole.HOST, memberCount);
}

@Transactional
public void delete(UUID roomId, Long userId) {
    Room room = getActiveRoom(roomId);
    getHostMember(roomId, userId);
    room.delete();
}
```

- [ ] **Step 4: 테스트 실행 — 성공 확인**

Run: `./gradlew test --tests "com.howaboutus.backend.rooms.service.RoomServiceTest"`
Expected: PASS (11 tests)

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/howaboutus/backend/rooms/service/RoomService.java \
        src/test/java/com/howaboutus/backend/rooms/service/RoomServiceTest.java
git commit -m "feat: RoomService 방 수정, 삭제 구현"
```

---

## Task 9: RoomService — 내 방 목록 조회

**Files:**
- Modify: `src/main/java/com/howaboutus/backend/rooms/service/RoomService.java`
- Modify: `src/test/java/com/howaboutus/backend/rooms/service/RoomServiceTest.java`

- [ ] **Step 1: 목록 조회 테스트 추가**

```java
@Test
@DisplayName("내 방 목록 조회 시 joinedAt 역순으로 반환하고 hasNext를 판단한다")
void getMyRoomsReturnsCursorPaginatedResults() {
    Long userId = 1L;
    int size = 2;

    Room room1 = Room.create("방1", null, null, null, "code1", userId);
    Room room2 = Room.create("방2", null, null, null, "code2", userId);
    Room room3 = Room.create("방3", null, null, null, "code3", userId);
    ReflectionTestUtils.setField(room1, "id", UUID.randomUUID());
    ReflectionTestUtils.setField(room2, "id", UUID.randomUUID());
    ReflectionTestUtils.setField(room3, "id", UUID.randomUUID());

    User user = User.ofGoogle("google-id", "test@test.com", "테스터", null);
    ReflectionTestUtils.setField(user, "id", userId);

    RoomMember m1 = RoomMember.of(room1, user, RoomRole.HOST);
    RoomMember m2 = RoomMember.of(room2, user, RoomRole.MEMBER);
    RoomMember m3 = RoomMember.of(room3, user, RoomRole.HOST);
    ReflectionTestUtils.setField(m1, "joinedAt", Instant.parse("2026-04-20T03:00:00Z"));
    ReflectionTestUtils.setField(m2, "joinedAt", Instant.parse("2026-04-20T02:00:00Z"));
    ReflectionTestUtils.setField(m3, "joinedAt", Instant.parse("2026-04-20T01:00:00Z"));

    given(roomMemberRepository.findMyRooms(eq(userId), eq(List.of(RoomRole.HOST, RoomRole.MEMBER)),
            eq(null), any(PageRequest.class)))
            .willReturn(List.of(m1, m2, m3));
    given(roomMemberRepository.countByRoom_IdAndRoleIn(any(UUID.class),
            eq(List.of(RoomRole.HOST, RoomRole.MEMBER))))
            .willReturn(4L);

    RoomListResult result = roomService.getMyRooms(userId, null, size);

    assertThat(result.rooms()).hasSize(2);
    assertThat(result.hasNext()).isTrue();
    assertThat(result.nextCursor()).isEqualTo(Instant.parse("2026-04-20T02:00:00Z"));
    assertThat(result.rooms().get(0).title()).isEqualTo("방1");
    assertThat(result.rooms().get(1).title()).isEqualTo("방2");
}

@Test
@DisplayName("내 방 목록이 size 이하이면 hasNext가 false다")
void getMyRoomsReturnsFalseHasNextWhenNoMore() {
    Long userId = 1L;
    int size = 20;

    Room room1 = Room.create("방1", null, null, null, "code1", userId);
    ReflectionTestUtils.setField(room1, "id", UUID.randomUUID());

    User user = User.ofGoogle("google-id", "test@test.com", "테스터", null);
    ReflectionTestUtils.setField(user, "id", userId);

    RoomMember m1 = RoomMember.of(room1, user, RoomRole.HOST);
    ReflectionTestUtils.setField(m1, "joinedAt", Instant.parse("2026-04-20T03:00:00Z"));

    given(roomMemberRepository.findMyRooms(eq(userId), eq(List.of(RoomRole.HOST, RoomRole.MEMBER)),
            eq(null), any(PageRequest.class)))
            .willReturn(List.of(m1));
    given(roomMemberRepository.countByRoom_IdAndRoleIn(any(UUID.class),
            eq(List.of(RoomRole.HOST, RoomRole.MEMBER))))
            .willReturn(1L);

    RoomListResult result = roomService.getMyRooms(userId, null, size);

    assertThat(result.rooms()).hasSize(1);
    assertThat(result.hasNext()).isFalse();
    assertThat(result.nextCursor()).isNull();
}
```

import 추가:
```java
import static org.mockito.ArgumentMatchers.eq;
import com.howaboutus.backend.rooms.service.dto.RoomListResult;
import org.springframework.data.domain.PageRequest;
```

- [ ] **Step 2: 테스트 실행 — 실패 확인**

Run: `./gradlew test --tests "com.howaboutus.backend.rooms.service.RoomServiceTest"`
Expected: FAIL (getMyRooms 없음)

- [ ] **Step 3: RoomService에 getMyRooms 추가**

```java
public RoomListResult getMyRooms(Long userId, Instant cursor, int size) {
    List<RoomMember> members = roomMemberRepository.findMyRooms(
            userId, ACTIVE_ROLES, cursor, PageRequest.of(0, size + 1));

    boolean hasNext = members.size() > size;
    List<RoomMember> page = hasNext ? members.subList(0, size) : members;

    List<RoomListResult.RoomSummary> rooms = page.stream()
            .map(m -> {
                Room room = m.getRoom();
                long memberCount = roomMemberRepository.countByRoom_IdAndRoleIn(room.getId(), ACTIVE_ROLES);
                return new RoomListResult.RoomSummary(
                        room.getId(), room.getTitle(), room.getDestination(),
                        room.getStartDate(), room.getEndDate(),
                        memberCount, m.getRole().name(), m.getJoinedAt());
            })
            .toList();

    Instant nextCursor = hasNext ? page.getLast().getJoinedAt() : null;
    return new RoomListResult(rooms, nextCursor, hasNext);
}
```

- [ ] **Step 4: 테스트 실행 — 성공 확인**

Run: `./gradlew test --tests "com.howaboutus.backend.rooms.service.RoomServiceTest"`
Expected: PASS (13 tests)

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/howaboutus/backend/rooms/service/RoomService.java \
        src/test/java/com/howaboutus/backend/rooms/service/RoomServiceTest.java
git commit -m "feat: RoomService 내 방 목록 조회 (커서 페이지네이션) 구현"
```
