# Rooms CRUD Part 1: 엔티티/리포지토리 계층

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Room soft delete 추가, RoomMember/RoomRole 엔티티 생성, Repository 메서드 추가, ErrorCode 확장

**Architecture:** 기존 `rooms` 패키지에 엔티티와 리포지토리를 추가한다. 설계 문서 기준 역할은 HOST/MEMBER/PENDING.

**Tech Stack:** Spring Boot 4.0, Java 21, JPA/Hibernate 7, PostgreSQL 17

**참조:** `docs/superpowers/specs/2026-04-19-rooms-design.md`, `docs/ai/erd.md`

**시리즈:** Part 1/3 → Part 2(서비스) → Part 3(컨트롤러+문서)

---

## Task 1: RoomRole enum + RoomMember 엔티티

**Files:**
- Create: `src/main/java/com/howaboutus/backend/rooms/entity/RoomRole.java`
- Create: `src/main/java/com/howaboutus/backend/rooms/entity/RoomMember.java`

- [ ] **Step 1: RoomRole enum 작성**

```java
package com.howaboutus.backend.rooms.entity;

public enum RoomRole {
    HOST, MEMBER, PENDING
}
```

- [ ] **Step 2: RoomMember 엔티티 작성**

```java
package com.howaboutus.backend.rooms.entity;

import com.howaboutus.backend.auth.entity.User;
import com.howaboutus.backend.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "room_members",
       uniqueConstraints = @UniqueConstraint(columns = {"room_id", "user_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoomMember extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RoomRole role;

    @Column(nullable = false)
    private Instant joinedAt;

    private RoomMember(Room room, User user, RoomRole role) {
        this.room = room;
        this.user = user;
        this.role = role;
        this.joinedAt = Instant.now();
    }

    public static RoomMember of(Room room, User user, RoomRole role) {
        return new RoomMember(room, user, role);
    }

    public void approve() {
        this.role = RoomRole.MEMBER;
    }
}
```

- [ ] **Step 3: 빌드 확인**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 커밋**

```bash
git add src/main/java/com/howaboutus/backend/rooms/entity/RoomRole.java \
        src/main/java/com/howaboutus/backend/rooms/entity/RoomMember.java
git commit -m "feat: RoomRole enum, RoomMember 엔티티 추가"
```

---

## Task 2: Room 엔티티 수정

**Files:**
- Modify: `src/main/java/com/howaboutus/backend/rooms/entity/Room.java`

변경 사항: `@UuidGenerator(style = TIME)` 적용, `deletedAt` 필드 추가, `update()`, `delete()`, `isDeleted()`, `regenerateInviteCode()` 메서드 추가.

- [ ] **Step 1: Room 엔티티 전체 교체**

```java
package com.howaboutus.backend.rooms.entity;

import com.howaboutus.backend.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "rooms")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Room extends BaseTimeEntity {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(length = 200)
    private String destination;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "invite_code", nullable = false, unique = true, length = 50)
    private String inviteCode;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    private Room(String title, String destination, LocalDate startDate,
                 LocalDate endDate, String inviteCode, Long createdBy) {
        this.title = title;
        this.destination = destination;
        this.startDate = startDate;
        this.endDate = endDate;
        this.inviteCode = inviteCode;
        this.createdBy = createdBy;
    }

    public static Room create(String title, String destination, LocalDate startDate,
                              LocalDate endDate, String inviteCode, Long createdBy) {
        return new Room(title, destination, startDate, endDate, inviteCode, createdBy);
    }

    public void update(String title, String destination, LocalDate startDate, LocalDate endDate) {
        if (title != null) {
            this.title = title;
        }
        if (destination != null) {
            this.destination = destination;
        }
        if (startDate != null) {
            this.startDate = startDate;
        }
        if (endDate != null) {
            this.endDate = endDate;
        }
    }

    public void delete() {
        this.deletedAt = Instant.now();
    }

    public boolean isDeleted() {
        return this.deletedAt != null;
    }

    public void regenerateInviteCode(String newCode) {
        this.inviteCode = newCode;
    }
}
```

- [ ] **Step 2: 빌드 확인**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add src/main/java/com/howaboutus/backend/rooms/entity/Room.java
git commit -m "feat: Room 엔티티에 soft delete, update, regenerateInviteCode 추가"
```

---

## Task 3: Repository 계층

**Files:**
- Modify: `src/main/java/com/howaboutus/backend/rooms/repository/RoomRepository.java`
- Create: `src/main/java/com/howaboutus/backend/rooms/repository/RoomMemberRepository.java`

- [ ] **Step 1: RoomRepository에 soft delete 조회 메서드 추가**

```java
package com.howaboutus.backend.rooms.repository;

import com.howaboutus.backend.rooms.entity.Room;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomRepository extends JpaRepository<Room, UUID> {

    Optional<Room> findByIdAndDeletedAtIsNull(UUID id);

    Optional<Room> findByInviteCodeAndDeletedAtIsNull(String inviteCode);
}
```

- [ ] **Step 2: RoomMemberRepository 작성**

```java
package com.howaboutus.backend.rooms.repository;

import com.howaboutus.backend.rooms.entity.RoomMember;
import com.howaboutus.backend.rooms.entity.RoomRole;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoomMemberRepository extends JpaRepository<RoomMember, Long> {

    Optional<RoomMember> findByRoom_IdAndUser_Id(UUID roomId, Long userId);

    @Query("""
           SELECT rm FROM RoomMember rm
           JOIN FETCH rm.room r
           WHERE rm.user.id = :userId
             AND rm.role IN (:roles)
             AND r.deletedAt IS NULL
             AND (:cursor IS NULL OR rm.joinedAt < :cursor)
           ORDER BY rm.joinedAt DESC
           """)
    List<RoomMember> findMyRooms(
            @Param("userId") Long userId,
            @Param("roles") List<RoomRole> roles,
            @Param("cursor") Instant cursor,
            Pageable pageable);

    long countByRoom_IdAndRoleIn(UUID roomId, List<RoomRole> roles);
}
```

- [ ] **Step 3: 빌드 확인**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 커밋**

```bash
git add src/main/java/com/howaboutus/backend/rooms/repository/RoomRepository.java \
        src/main/java/com/howaboutus/backend/rooms/repository/RoomMemberRepository.java
git commit -m "feat: RoomRepository soft delete 조회, RoomMemberRepository 추가"
```

---

## Task 4: ErrorCode 추가

**Files:**
- Modify: `src/main/java/com/howaboutus/backend/common/error/ErrorCode.java`

- [ ] **Step 1: Room 관련 ErrorCode 추가**

기존 `ROOM_NOT_FOUND` 메시지를 `"존재하지 않는 방입니다"`로 변경하고, 아래 코드를 추가한다.

```java
// 400 BAD REQUEST
INVALID_DATE_RANGE(HttpStatus.BAD_REQUEST, "시작일이 종료일보다 늦을 수 없습니다"),

// 403 FORBIDDEN
NOT_ROOM_MEMBER(HttpStatus.FORBIDDEN, "방의 멤버가 아닙니다"),
NOT_ROOM_HOST(HttpStatus.FORBIDDEN, "호스트 권한이 필요합니다"),

// 404 NOT FOUND (기존 ROOM_NOT_FOUND 메시지 변경)
ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 방입니다"),
USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다"),
```

- [ ] **Step 2: 빌드 확인**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add src/main/java/com/howaboutus/backend/common/error/ErrorCode.java
git commit -m "feat: Room 관련 ErrorCode 추가 (INVALID_DATE_RANGE, NOT_ROOM_MEMBER, NOT_ROOM_HOST, USER_NOT_FOUND)"
```
