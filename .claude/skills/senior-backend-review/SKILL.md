---
name: senior-backend-review
description: Use when reviewing Spring Boot service/controller/repository code for production readiness. Covers error handling gaps, JPA/Hibernate pitfalls, Redis cache patterns, concurrency issues, and scalability bottlenecks specific to this project's stack.
---

# Senior Backend Review

## Overview

시니어 백엔드 엔지니어 관점에서 에러 핸들링, 성능, 확장성을 체계적으로 점검한다.
**자유형 리뷰 금지** — 아래 체크리스트를 순서대로 실행하고 발견 항목을 표에 기록한다.

## 리뷰 순서

```dot
digraph review_flow {
    "코드 수신" [shape=doublecircle];
    "1. 에러 핸들링" [shape=box];
    "2. 트랜잭션/JPA" [shape=box];
    "3. 성능" [shape=box];
    "4. 확장성/동시성" [shape=box];
    "5. 보안/검증" [shape=box];
    "결과 표 작성" [shape=doublecircle];

    "코드 수신" -> "1. 에러 핸들링";
    "1. 에러 핸들링" -> "2. 트랜잭션/JPA";
    "2. 트랜잭션/JPA" -> "3. 성능";
    "3. 성능" -> "4. 확장성/동시성";
    "4. 확장성/동시성" -> "5. 보안/검증";
    "5. 보안/검증" -> "결과 표 작성";
}
```

---

## 1. 에러 핸들링

| 체크 항목 | 안티패턴 | 올바른 패턴 |
|---|---|---|
| `orElseThrow()` 메시지 | `orElseThrow()` — 맥락 없는 예외 | `orElseThrow(() -> new MeetingNotFoundException(id))` |
| 도메인 예외 클래스 | `RuntimeException` 직접 사용 | 도메인별 예외 + `@ExceptionHandler` HTTP 매핑 |
| 상태 전이 검증 | setter로 직접 상태 변경 | 도메인 메서드 내부에서 유효 전이만 허용 |
| 이벤트 원자성 | `save()` 직후 `publishEvent()` | `@TransactionalEventListener(phase = AFTER_COMMIT)` |
| 비즈니스 규칙 검증 | 중복 참가 / 정원 초과 / 주최자 재참가 미검증 | 서비스 진입 시 명시적 조건 확인 + 예외 |

**이 프로젝트 특이사항:**
- 글로벌 예외 핸들러(`@RestControllerAdvice`)가 있는지 확인한다.
- 아웃박스 패턴 미적용 시 이벤트 유실 가능성을 지적한다.

---

## 2. 트랜잭션 / JPA

| 체크 항목 | 확인 방법 |
|---|---|
| `@Transactional` 누락 | 쓰기 메서드에 어노테이션 없으면 지적 |
| `readOnly = true` 누락 | 읽기 전용 메서드에 명시 여부 확인 |
| `open-in-view=false` 충돌 | **이 프로젝트는 `open-in-view=false`** — `@Transactional` 없이 Lazy 컬렉션 접근하면 `LazyInitializationException` 발생 |
| N+1 쿼리 | 루프 안에서 `repository.find*()` 또는 Lazy 컬렉션 초기화 감지 → `JOIN FETCH` / `@EntityGraph` 권고 |
| 영속성 컨텍스트 범위 | 서비스 레이어 밖에서 엔티티 접근 여부 |

```java
// N+1 패턴 예시 — 즉시 지적
for (User participant : meeting.getParticipants()) {
    repository.findByOrganizerId(participant.getId()); // participants 수만큼 쿼리
}
// 권고: 배치 쿼리 또는 JOIN FETCH로 대체
```

---

## 3. 성능

### 3-1. DB 쿼리

| 체크 항목 | 위험 신호 |
|---|---|
| `findAll()` + 인메모리 필터 | 전체 테이블 로드 → OOM / 풀스캔 |
| PostGIS 공간 쿼리 | Java 내 Haversine 연산 → `ST_DWithin` + GIST 인덱스로 DB 위임 |
| 페이지네이션 누락 | 목록 조회에 `Pageable` 없으면 지적 (데이터 증가 시 문제) |
| 인덱스 미명시 | 자주 조회되는 컬럼(`organizer_id`, `status`, 공간 컬럼)에 인덱스 확인 |

```sql
-- PostGIS 공간 쿼리 올바른 패턴 (이 프로젝트 적용)
SELECT * FROM meetings
WHERE ST_DWithin(
    location::geography,
    ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography,
    :radiusMeters
)
-- 전제: location 컬럼에 CREATE INDEX idx_meetings_location USING GIST(location)
```

### 3-2. Redis 캐시

| 체크 항목 | 안티패턴 | 올바른 패턴 |
|---|---|---|
| Cache-Aside 순서 | DB 조회 후 캐시에 쓰기만 함 | 캐시 먼저 확인 → miss 시 DB 조회 → 캐시 저장 |
| TTL 미설정 | `set(key, value)` — 만료 없음 | `set(key, value, duration, TimeUnit)` 또는 `@Cacheable(cacheManager)` |
| 캐시 대상 타입 | 문자열 일부만 캐싱 | 직렬화 가능한 전체 DTO 캐싱 |
| 캐시 무효화 | 업데이트/삭제 시 캐시 삭제 누락 | `@CacheEvict` 또는 명시적 `delete(key)` |

---

## 4. 확장성 / 동시성

| 체크 항목 | 확인 포인트 |
|---|---|
| 낙관적 락 | 경쟁 조건 발생 가능 엔티티에 `@Version` 필드 없으면 지적 |
| 비관적 락 | 정원 제한 등 강한 일관성 필요 시 `@Lock(PESSIMISTIC_WRITE)` 권고 |
| 분산 락 | 다중 인스턴스 환경에서 Redis `SETNX` / Redisson 락 고려 권고 |
| 무상태(Stateless) 설계 | 서비스 레이어에 인스턴스 상태(필드) 저장 여부 확인 |
| 이벤트/메시지 멱등성 | 이벤트 핸들러에 중복 처리 방어 있는지 확인 |
| 벌크 연산 | 루프 내 `save()` 반복 → `saveAll()` 또는 배치 쿼리 권고 |

---

## 5. 보안 / 입력 검증

| 체크 항목 | 확인 포인트 |
|---|---|
| 입력 검증 | Request DTO에 `@Valid` + Bean Validation 없으면 지적 |
| 권한 검증 | 리소스 소유권 확인 없이 ID만으로 수정/삭제 가능한지 확인 |
| SQL 인젝션 | `nativeQuery` 사용 시 파라미터 바인딩(`:param`) 여부 확인 |
| 감사 필드 | `createdAt`, `updatedAt`, `createdBy` 없으면 권고 |

---

## 결과 표 형식

리뷰 완료 후 반드시 아래 형식으로 정리한다:

```
| 카테고리 | 파일:라인 | 문제 요약 | 심각도 | 권고 조치 |
|---|---|---|---|---|
| 성능 | MeetingService:25 | findAll() 풀스캔 | 🔴 상 | ST_DWithin + GIST 인덱스 |
| 트랜잭션 | MeetingService:40 | @Transactional 누락 | 🔴 상 | @Transactional 추가 |
| 캐시 | MeetingService:52 | TTL 미설정 | 🟡 중 | set(key, value, 10, MINUTES) |
```  

심각도: 🔴 상(즉시 수정) / 🟡 중(다음 스프린트) / 🟢 하(리팩토링 시)

---

## 이 프로젝트 특화 함정 (빠르게 체크)

이 리스트는 how-about-us-backend 스택 고유 위험 항목이다:

- `open-in-view=false` → 서비스 밖 Lazy 로딩 불가
- PostGIS `Point` 타입 → Java 내 거리 계산 금지, DB에 위임
- Redis 8 → `OBJECT ENCODING` 변경 주의, TTL 필수
- Spring Boot 4 / Spring 6 → `HttpMethod.resolve()` deprecated, `jakarta.*` 패키지 사용
- WebSocket + STOMP → Security 설정에서 WebSocket 엔드포인트 별도 허용 필요
