---
name: backend-implementer
description: >
  Use when implementing new features, services, controllers, or repositories in this Spring Boot project.
  Covers N+1 query prevention, transaction boundary design, DTO/Entity separation, and Lombok conventions.
  Make sure to use this skill whenever the user asks to implement a feature, add a service method, write
  a controller, create a repository, or modify business logic.
---

# Backend Implementer

## 역할 (Role)

대규모 트래픽 환경을 경험한 시니어 백엔드 엔지니어 관점에서, how-about-us-backend의 Spring Boot / JPA 스택에 맞는 올바른 구현을 수행한다. 빠른 기능 완성보다 **운영 안전성과 유지보수성**을 우선한다.

---

## 원칙 (Principles)

1. **트랜잭션 경계를 서비스 레이어에서 결정한다** — `open-in-view=false`이므로 트랜잭션이 닫힌 뒤에는 Lazy 컬렉션 접근이 불가하다. 서비스 메서드 단위로 `@Transactional`을 명시하고, 읽기 전용 메서드에는 `@Transactional(readOnly = true)`를 반드시 붙인다.

2. **N+1 쿼리를 코드 작성 시점에 차단한다** — 루프 안에서 연관 엔티티를 조회하거나 Lazy 컬렉션을 초기화하면 쿼리가 N번 발생한다. `JOIN FETCH`, `@EntityGraph`, 또는 배치 쿼리로 미리 해결한다.

3. **DTO와 Entity를 분리한다** — Entity를 컨트롤러 응답으로 직접 반환하면 양방향 관계에서 무한 직렬화, 불필요한 필드 노출, 스펙 변경 시 연쇄 수정이 발생한다. 요청/응답 DTO를 별도 클래스로 정의한다.

4. **Lombok 규칙을 지킨다** — `@Data`는 `equals/hashCode`가 모든 필드를 포함해 JPA 엔티티에서 문제를 일으킨다. Entity에는 `@Getter` + 필요한 경우 `@Builder`만 사용한다. DTO에는 `@Data` 또는 record를 사용한다.

5. **도메인 로직은 엔티티 안에 둔다** — 상태 변경, 유효성 검사, 비즈니스 규칙은 서비스가 아닌 엔티티 메서드에 캡슐화한다. 서비스는 조율(orchestration) 역할만 한다.

---

## 작업 전 체크리스트

- [ ] 구현할 기능의 요구사항을 `docs/ai/features.md`에서 확인한다.
- [ ] 관련 테이블/관계를 `docs/ai/erd.md`에서 확인한다.
- [ ] API 스펙이 이미 정의된 경우 `docs/ai/api-spec.md`를 확인한다.
- [ ] 이 구현이 기존 트랜잭션 경계에 영향을 주는지 검토한다.

## 작업 후 체크리스트

- [ ] 모든 쓰기 서비스 메서드에 `@Transactional` 붙어 있는지 확인한다.
- [ ] 모든 읽기 서비스 메서드에 `@Transactional(readOnly = true)` 붙어 있는지 확인한다.
- [ ] N+1 쿼리 발생 가능성이 있는 연관 조회를 점검한다.
- [ ] 컨트롤러가 DTO를 반환하고 Entity를 직접 노출하지 않는지 확인한다.
- [ ] 기능이 변경되었으면 `docs/ai/features.md`를 갱신한다.

---

## 금지 사항 (Never Do)

| 금지 행동 | 이유 |
|---|---|
| Entity를 컨트롤러 응답으로 직접 반환 | 양방향 관계 무한 직렬화, 불필요한 필드 노출 |
| `@Transactional` 없이 Lazy 컬렉션 접근 | `open-in-view=false`이므로 `LazyInitializationException` 발생 |
| 루프 안에서 `repository.find*()` 호출 | N+1 쿼리 발생으로 성능 저하 |
| Entity에 `@Data` 사용 | JPA 엔티티의 `equals/hashCode` 오동작, 프록시 관련 문제 |
| 서비스에서 직접 엔티티 필드 변경 (`setter` 나열) | 도메인 규칙이 서비스에 흩어져 유지보수 어려움 |
| `findAll()` 후 인메모리 필터 | 전체 테이블 로드 → OOM / 풀스캔 위험 |

---

## 코드 패턴

### 서비스 트랜잭션 경계

```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)  // 클래스 기본값: 읽기 전용
public class MeetingService {

    private final MeetingRepository meetingRepository;

    public MeetingResponse getMeeting(Long id) {  // readOnly 상속
        Meeting meeting = meetingRepository.findById(id)
            .orElseThrow(() -> new MeetingNotFoundException(id));
        return MeetingResponse.from(meeting);
    }

    @Transactional  // 쓰기는 명시적으로 오버라이드
    public MeetingResponse createMeeting(CreateMeetingRequest request, Long userId) {
        Meeting meeting = Meeting.create(request, userId);
        return MeetingResponse.from(meetingRepository.save(meeting));
    }
}
```

### N+1 방지 — JOIN FETCH

```java
// 안티패턴: 루프 안에서 Lazy 컬렉션 초기화
List<Meeting> meetings = meetingRepository.findAll();
for (Meeting m : meetings) {
    m.getParticipants().size();  // 미팅 수만큼 쿼리 발생!
}

// 올바른 패턴: JOIN FETCH
@Query("SELECT m FROM Meeting m JOIN FETCH m.participants WHERE m.status = :status")
List<Meeting> findAllWithParticipants(@Param("status") MeetingStatus status);
```

### DTO ↔ Entity 변환

```java
// DTO에 정적 팩토리 메서드 사용
public record MeetingResponse(Long id, String title, String status) {
    public static MeetingResponse from(Meeting meeting) {
        return new MeetingResponse(meeting.getId(), meeting.getTitle(), meeting.getStatus().name());
    }
}
```

### Lombok Entity 규칙

```java
@Entity
@Getter                          // ✅ Getter만
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "meetings")
public class Meeting {
    // @Data 사용 금지 — JPA 프록시 문제
}
```

---

## 참고 문서

| 문서 | 경로 | 읽는 시점 |
|------|------|-----------|
| 기능 명세서 | `docs/ai/features.md` | 구현할 기능 파악 시 |
| ERD 명세서 | `docs/ai/erd.md` | 테이블 구조, 관계 파악 시 |
| API 명세서 | `docs/ai/api-spec.md` | 요청/응답 스펙 확인 시 |

---

## 이 프로젝트 특이사항

- **`open-in-view=false`** — 모든 Lazy 로딩은 서비스 트랜잭션 안에서만 가능하다.
- **PostGIS `Point` 타입** — 공간 데이터는 `hibernate-spatial` 타입 사용, Java 내 거리 계산 금지.
- **Spring Boot 4 / Jakarta** — `javax.*` → `jakarta.*` 패키지 사용.
- **Redis 8** — 캐시 사용 시 TTL 필수, `@Cacheable` 사용 시 캐시 매니저 설정 확인.
