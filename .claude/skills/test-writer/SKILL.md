---
name: test-writer
description: >
  Use when writing unit tests, integration tests, or repository tests for this Spring Boot project.
  Covers test layer separation, @SpringBootTest overuse prevention, Testcontainers usage decisions,
  and test naming conventions.
  Make sure to use this skill whenever the user asks to write tests, add test cases, set up test
  infrastructure, or decide between unit and integration testing strategies.
---

# Test Writer

## 역할 (Role)

how-about-us-backend에서 **테스트가 실제 버그를 잡고, 빠르게 실행되며, 유지보수 부담이 낮은** 테스트 스위트를 작성한다. 테스트 커버리지 숫자보다 **신뢰할 수 있는 테스트**가 더 가치 있다.

---

## 원칙 (Principles)

1. **테스트 범위에 맞는 도구를 선택한다** — `@SpringBootTest`는 전체 애플리케이션 컨텍스트를 로드해 느리다. 비즈니스 로직 검증에는 단위 테스트, DB 쿼리 검증에는 슬라이스 테스트(`@DataJpaTest`), E2E 흐름에는 통합 테스트를 선택한다.

2. **테스트는 실제 동작을 검증한다** — 모킹을 남용하면 테스트가 통과해도 실제 환경에서 실패할 수 있다. 특히 DB 쿼리는 실제 DB 대상으로 검증해야 JPQL 오류, N+1 문제, 인덱스 미사용을 잡을 수 있다.

3. **Testcontainers는 DB/외부 의존성에만 사용한다** — 모든 테스트에 Testcontainers를 붙이면 기동 비용이 높아진다. Repository 슬라이스 테스트, PostGIS 쿼리 검증처럼 실제 DB가 반드시 필요한 테스트에만 사용한다.

4. **테스트 이름은 의도를 드러낸다** — `test1()`, `shouldWork()` 같은 이름은 실패했을 때 원인을 파악하기 어렵다. `{상황}_when_{조건}_then_{기대결과}` 패턴으로 이름을 짓는다.

5. **테스트 간 상태를 격리한다** — 테스트가 DB 상태에 의존하거나 다른 테스트가 만든 데이터를 참조하면 실행 순서에 따라 테스트가 깨진다. `@Transactional` 롤백 또는 `@BeforeEach`에서 명시적 정리를 사용한다.

---

## 테스트 레이어 구분 기준

| 테스트 유형 | 어노테이션 | 사용 시점 | 속도 |
|---|---|---|---|
| **단위 테스트** | 없음 (순수 Java) | 비즈니스 로직, 도메인 메서드 검증 | 빠름 |
| **슬라이스 - JPA** | `@DataJpaTest` | Repository 쿼리, JPQL, N+1 검증 | 중간 |
| **슬라이스 - Web** | `@WebMvcTest` | 컨트롤러 요청/응답 포맷, 입력 검증 | 중간 |
| **통합 테스트** | `@SpringBootTest` | 전체 플로우, 외부 연동 검증 | 느림 |

```
@SpringBootTest 사용 기준:
- 여러 레이어를 가로지르는 시나리오 (예: 인증 → 서비스 → DB → 응답)
- 특정 슬라이스 어노테이션으로는 재현할 수 없는 경우
그 외에는 슬라이스 테스트 또는 단위 테스트로 충분하다.
```

---

## 작업 전 체크리스트

- [ ] 어떤 레이어의 어떤 동작을 검증하려는지 명확히 한다.
- [ ] DB 연동이 필요한지 결정한다. (필요하면 Testcontainers 사용)
- [ ] 이미 유사한 테스트가 있는지 확인한다.

## 작업 후 체크리스트

- [ ] 테스트 이름이 의도를 드러내는지 확인한다.
- [ ] `@SpringBootTest`가 정말 필요한지 재검토한다.
- [ ] DB 상태 의존성이 없는지 확인한다 (테스트 간 격리).
- [ ] `./gradlew test`로 새 테스트가 통과하는지 확인한다.

---

## 코드 패턴

### 단위 테스트 — 도메인 로직

```java
class MeetingTest {

    @Test
    void join_whenParticipantAlreadyJoined_throwsException() {
        // given
        Meeting meeting = Meeting.create(...);
        meeting.join(userId);

        // when & then
        assertThatThrownBy(() -> meeting.join(userId))
            .isInstanceOf(AlreadyJoinedException.class);
    }
}
```

### JPA 슬라이스 테스트 — Repository

```java
@DataJpaTest
@Import(JpaConfig.class)  // 커스텀 JPA 설정이 있는 경우
class MeetingRepositoryTest {

    @Autowired
    MeetingRepository meetingRepository;

    @Test
    void findNearby_returnsOnlyMeetingsWithinRadius() {
        // given: 테스트 데이터 저장
        // when: 공간 쿼리 실행
        // then: 반환된 미팅이 반경 내에 있는지 검증
    }
}
```

### PostGIS 테스트 — Testcontainers 사용

```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class SpatialQueryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgis/postgis:17-3.5");

    // PostGIS 쿼리, ST_DWithin 등 실제 DB 기능 검증
}
```

### 컨트롤러 슬라이스 테스트

```java
@WebMvcTest(MeetingController.class)
class MeetingControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean MeetingService meetingService;

    @Test
    void createMeeting_withInvalidInput_returns400() throws Exception {
        mockMvc.perform(post("/api/meetings")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))  // 필수 필드 누락
            .andExpect(status().isBadRequest());
    }
}
```

---

## 금지 사항 (Never Do)

| 금지 행동 | 이유 |
|---|---|
| 모든 테스트에 `@SpringBootTest` 사용 | 전체 컨텍스트 로딩으로 테스트 속도 심각하게 저하 |
| Repository 테스트에 H2 인메모리 DB 사용 | PostGIS 쿼리(`ST_DWithin` 등)는 H2에서 실행 불가 |
| 테스트에서 다른 테스트의 DB 데이터 참조 | 실행 순서 의존성 → 간헐적 실패 |
| 서비스 레이어 테스트에서 모든 의존성 모킹 | 실제 통합 오류를 잡지 못함 |
| `test1()`, `method_test()` 같은 이름 | 실패 시 원인 파악 어려움 |

---

## 참고 문서

| 문서 | 경로 | 읽는 시점 |
|------|------|-----------|
| 기능 명세서 | `docs/ai/features.md` | 어떤 비즈니스 규칙을 테스트해야 하는지 확인 시 |
| ERD 명세서 | `docs/ai/erd.md` | Repository 테스트 데이터 구성 시 |

---

## 이 프로젝트 특이사항

- **PostGIS 테스트**: `postgis/postgis:17-3.5` 이미지로 Testcontainers 사용 (H2 사용 불가)
- **`open-in-view=false`**: 서비스 트랜잭션 경계 밖에서 Lazy 로딩을 테스트하면 `LazyInitializationException` 발생 → 테스트에서도 서비스를 통해 접근하거나 `@Transactional`을 붙인다
- **dev 프로파일 PostgreSQL 포트**: `5433` (기본값 `5432` 아님)
