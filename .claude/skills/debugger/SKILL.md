---
name: debugger
description: >
  Use when investigating bugs, diagnosing errors, analyzing stack traces, or troubleshooting unexpected
  behavior in this Spring Boot project. Enforces systematic log-first → hypothesis → minimal reproduction
  approach and prohibits guess-based fixes.
  Make sure to use this skill whenever the user reports a bug, shows an error or exception, asks why
  something isn't working, or needs to diagnose unexpected behavior.
---

# Debugger

## 역할 (Role)

how-about-us-backend에서 발생한 버그와 장애를 **체계적으로** 분석한다. 추측으로 코드를 수정하는 대신, 증거를 수집하고 가설을 세우고 최소 재현 경로를 찾아 원인을 확정한 뒤 수정한다.

---

## 원칙 (Principles)

1. **추측 기반 수정 금지** — "아마 이것 때문일 것"이라는 추측으로 코드를 바꾸면 실제 원인이 남아 재발한다. 원인이 확정되지 않은 상태에서의 수정은 새로운 버그를 만들 수 있다.

2. **로그가 먼저다** — 코드를 보기 전에 로그를 본다. 스택 트레이스, 에러 메시지, 최근 로그에는 원인의 90% 이상이 담겨 있다. 로그 없이 코드만 보는 것은 지도 없이 길을 찾는 것이다.

3. **최소 재현을 만든다** — 버그를 재현할 수 있으면 원인을 찾은 것과 다름없다. 재현하지 못한 채로 코드를 수정하면 "고쳤다"는 확신을 가질 수 없다.

4. **한 번에 하나씩 변경한다** — 동시에 여러 곳을 바꾸면 어떤 변경이 문제를 해결했는지 알 수 없다. 가설을 하나씩 검증한다.

---

## 디버깅 순서 (반드시 이 순서를 따른다)

```
1. 로그 확인
   ↓
2. 문제 범위 특정
   ↓
3. 가설 수립 (2~3개)
   ↓
4. 최소 재현 경로 확인
   ↓
5. 원인 확정
   ↓
6. 수정 및 검증
```

---

## 단계별 가이드

### 1단계: 로그 확인

- 스택 트레이스 전체를 읽는다. 첫 번째 `Caused by:` 줄이 실제 원인인 경우가 많다.
- 에러 발생 직전 로그 라인을 확인한다.
- 이 프로젝트의 주요 로그 위치:
  ```bash
  # 로컬 실행 로그 (Spring Boot 콘솔)
  ./gradlew bootRun --args='--spring.profiles.active=dev'

  # 특정 패키지 로그 레벨 높이기 (application-dev.yaml)
  logging.level.com.howaboutus: DEBUG
  logging.level.org.hibernate.SQL: DEBUG  # JPA 쿼리 확인
  logging.level.org.hibernate.orm.jdbc.bind: TRACE  # 바인딩 파라미터
  ```

### 2단계: 문제 범위 특정

아래 질문으로 범위를 좁힌다:

- 항상 발생하는가, 간헐적으로 발생하는가?
- 특정 데이터 / 사용자 / 환경에서만 발생하는가?
- 언제부터 발생했는가? (최근 배포와 연관이 있는가?)
- 어느 레이어에서 발생하는가? (Controller / Service / Repository / DB)

### 3단계: 가설 수립

증거(로그, 스택 트레이스)를 바탕으로 2~3개의 가설을 세운다.

```
예시:
1. N+1 쿼리로 인한 타임아웃 — 로그에 반복 쿼리 패턴이 보임
2. 트랜잭션 경계 밖 Lazy 로딩 — LazyInitializationException 스택 트레이스
3. Redis TTL 만료 후 캐시 미스 처리 누락 — NPE 발생 시점이 캐시 만료 패턴과 일치
```

### 4단계: 최소 재현

가장 가능성 높은 가설부터 재현을 시도한다.

- 테스트 코드로 재현 가능하면 테스트를 먼저 작성한다.
- 재현이 되면 원인이 확정된 것이다.
- 재현이 안 되면 다음 가설로 넘어간다.

### 5단계: 원인 확정 + 수정

- 원인이 명확히 확정된 경우에만 코드를 수정한다.
- 수정 후 재현 테스트가 통과하는지 확인한다.
- `./gradlew test`로 기존 테스트가 깨지지 않는지 확인한다.

---

## 이 프로젝트 주요 버그 패턴

### LazyInitializationException

```
원인: open-in-view=false 환경에서 트랜잭션 밖에서 Lazy 컬렉션 접근
확인: 스택 트레이스에 LazyInitializationException 포함 여부
수정: @Transactional 범위 내로 접근 이동 or JOIN FETCH 사용
```

### N+1 쿼리

```
원인: 루프 안에서 연관 엔티티 접근
확인: logging.level.org.hibernate.SQL=DEBUG 후 반복 SELECT 패턴 확인
수정: JOIN FETCH, @EntityGraph, 또는 배치 조회
```

### Redis NPE / 캐시 불일치

```
원인: TTL 만료 후 null 반환 처리 누락, 또는 캐시-DB 동기화 누락
확인: 캐시 히트/미스 시 null 체크 여부, @CacheEvict 적용 여부
수정: null 방어 처리 추가, 쓰기 작업에 캐시 무효화 추가
```

### WebSocket 403

```
원인: Spring Security에서 WebSocket 핸드셰이크 경로 미허용
확인: SecurityFilterChain에서 /ws/** 경로 허용 여부
수정: SecurityFilterChain에 .requestMatchers("/ws/**").permitAll() 추가
```

### PostGIS 쿼리 오류

```
원인: hibernate-spatial 타입 불일치, SRID 설정 누락
확인: 컬럼 정의(geometry(Point, 4326))와 Java 타입(org.locationtech.jts.geom.Point) 일치 여부
수정: 타입/SRID 통일, ST_SetSRID 명시
```

---

## 금지 사항 (Never Do)

| 금지 행동 | 이유 |
|---|---|
| 원인 불확정 상태에서 "이것 같은데" 수정 | 실제 원인이 남아 재발, 새 버그 유입 가능성 |
| 스택 트레이스 없이 코드만 보고 수정 | 증거 없는 수정은 운에 의존 |
| 동시에 여러 곳 수정 | 무엇이 해결했는지 알 수 없어 재발 시 대응 불가 |
| 재현 없이 "아마 고쳤을 것" 처리 | 문제가 남아있는지 알 수 없음 |
| 로그 추가 없이 간헐적 버그 해결 시도 | 재발 시 같은 사이클 반복 |

---

## 참고 문서

| 문서 | 경로 | 읽는 시점 |
|------|------|-----------|
| ERD 명세서 | `docs/ai/erd.md` | DB 관련 버그 분석 시 스키마 확인 |
| API 명세서 | `docs/ai/api-spec.md` | API 동작 이상 시 예상 스펙 확인 |
| 기능 명세서 | `docs/ai/features.md` | 비즈니스 로직 버그 시 정상 동작 확인 |

---

## 이 프로젝트 특이사항

- **`open-in-view=false`** — Lazy 로딩 관련 버그의 가장 흔한 원인
- **PostGIS** — 공간 쿼리 오류는 SRID 불일치 또는 타입 불일치가 대부분
- **dev 환경 PostgreSQL 포트**: `5433` (연결 오류 시 포트 먼저 확인)
- **Spring Boot 4** — `jakarta.*` 패키지 (구 `javax.*` 사용 시 NoClassDefFoundError)
