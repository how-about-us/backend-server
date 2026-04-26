# Schedule Management Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 여행 방에서 일정 일자 CRUD와 일정 항목 기본 CRUD 및 시간 수정을 제공한다.

**Architecture:** `schedules`와 `schedule_items`를 별도 도메인 패키지로 추가하고, `Room` 최소 엔티티를 재사용해 방 소속 검증을 수행한다. 일정 일자는 `ScheduleService`, 일정 항목은 `ScheduleItemService`가 담당하며, 컨트롤러는 기존 북마크 패턴처럼 요청 DTO 검증과 응답 매핑만 맡는다.

**Tech Stack:** Spring Boot 4, Java 21, Spring Data JPA, Bean Validation, MockMvc, JUnit 5, Mockito, Testcontainers PostgreSQL/PostGIS

---

## File Structure

### New files

- `src/main/java/com/howaboutus/backend/schedules/entity/Schedule.java`
- `src/main/java/com/howaboutus/backend/schedules/entity/ScheduleItem.java`
- `src/main/java/com/howaboutus/backend/schedules/repository/ScheduleRepository.java`
- `src/main/java/com/howaboutus/backend/schedules/repository/ScheduleItemRepository.java`
- `src/main/java/com/howaboutus/backend/schedules/service/ScheduleService.java`
- `src/main/java/com/howaboutus/backend/schedules/service/ScheduleItemService.java`
- `src/main/java/com/howaboutus/backend/schedules/service/dto/ScheduleCreateCommand.java`
- `src/main/java/com/howaboutus/backend/schedules/service/dto/ScheduleResult.java`
- `src/main/java/com/howaboutus/backend/schedules/service/dto/ScheduleItemCreateCommand.java`
- `src/main/java/com/howaboutus/backend/schedules/service/dto/ScheduleItemUpdateCommand.java`
- `src/main/java/com/howaboutus/backend/schedules/service/dto/ScheduleItemResult.java`
- `src/main/java/com/howaboutus/backend/schedules/controller/ScheduleController.java`
- `src/main/java/com/howaboutus/backend/schedules/controller/ScheduleItemController.java`
- `src/main/java/com/howaboutus/backend/schedules/controller/dto/CreateScheduleRequest.java`
- `src/main/java/com/howaboutus/backend/schedules/controller/dto/ScheduleResponse.java`
- `src/main/java/com/howaboutus/backend/schedules/controller/dto/CreateScheduleItemRequest.java`
- `src/main/java/com/howaboutus/backend/schedules/controller/dto/UpdateScheduleItemRequest.java`
- `src/main/java/com/howaboutus/backend/schedules/controller/dto/ScheduleItemResponse.java`
- `src/test/java/com/howaboutus/backend/schedules/service/ScheduleServiceTest.java`
- `src/test/java/com/howaboutus/backend/schedules/service/ScheduleItemServiceTest.java`
- `src/test/java/com/howaboutus/backend/schedules/controller/ScheduleControllerTest.java`
- `src/test/java/com/howaboutus/backend/schedules/controller/ScheduleItemControllerTest.java`
- `src/test/java/com/howaboutus/backend/schedules/ScheduleIntegrationTest.java`

### Modified files

- `src/main/java/com/howaboutus/backend/common/error/ErrorCode.java`
- `docs/ai/features.md`
- `docs/ai/erd.md`

## Task 1: Schedule 도메인과 일정 일자 서비스 추가

**Files:**
- Create: `src/main/java/com/howaboutus/backend/schedules/entity/Schedule.java`
- Create: `src/main/java/com/howaboutus/backend/schedules/repository/ScheduleRepository.java`
- Create: `src/main/java/com/howaboutus/backend/schedules/service/ScheduleService.java`
- Create: `src/main/java/com/howaboutus/backend/schedules/service/dto/ScheduleCreateCommand.java`
- Create: `src/main/java/com/howaboutus/backend/schedules/service/dto/ScheduleResult.java`
- Create: `src/test/java/com/howaboutus/backend/schedules/service/ScheduleServiceTest.java`
- Modify: `src/main/java/com/howaboutus/backend/common/error/ErrorCode.java`

- [ ] **Step 1: 일정 일자 서비스 실패 테스트 작성**

```java
@ExtendWith(MockitoExtension.class)
class ScheduleServiceTest {

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private ScheduleRepository scheduleRepository;

    private ScheduleService scheduleService;

    @BeforeEach
    void setUp() {
        scheduleService = new ScheduleService(roomRepository, scheduleRepository);
    }

    @Test
    @DisplayName("여행 시작일 기준 dayNumber와 date가 일치하면 일정 생성에 성공한다")
    void createReturnsSavedSchedule() {
        UUID roomId = UUID.randomUUID();
        Room room = Room.create(
                "도쿄 여행",
                "도쿄",
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 3),
                "TOKYO-SCHEDULE",
                1L
        );
        ReflectionTestUtils.setField(room, "id", roomId);

        Schedule savedSchedule = Schedule.create(room, 2, LocalDate.of(2026, 5, 2));
        ReflectionTestUtils.setField(savedSchedule, "id", 10L);
        ReflectionTestUtils.setField(savedSchedule, "createdAt", Instant.parse("2026-05-01T00:00:00Z"));

        given(roomRepository.findById(roomId)).willReturn(Optional.of(room));
        given(scheduleRepository.existsByRoom_IdAndDayNumber(roomId, 2)).willReturn(false);
        given(scheduleRepository.existsByRoom_IdAndDate(roomId, LocalDate.of(2026, 5, 2))).willReturn(false);
        given(scheduleRepository.saveAndFlush(any(Schedule.class))).willReturn(savedSchedule);

        ScheduleResult result = scheduleService.create(roomId, new ScheduleCreateCommand(2, LocalDate.of(2026, 5, 2)));

        assertThat(result.scheduleId()).isEqualTo(10L);
        assertThat(result.dayNumber()).isEqualTo(2);
        assertThat(result.date()).isEqualTo(LocalDate.of(2026, 5, 2));
    }

    @Test
    @DisplayName("dayNumber와 date가 여행 시작일 기준으로 맞지 않으면 SCHEDULE_DATE_MISMATCH 예외를 던진다")
    void createThrowsWhenDateDoesNotMatchDayNumber() {
        UUID roomId = UUID.randomUUID();
        Room room = Room.create(
                "도쿄 여행",
                "도쿄",
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 3),
                "TOKYO-SCHEDULE",
                1L
        );

        given(roomRepository.findById(roomId)).willReturn(Optional.of(room));

        assertThatThrownBy(() -> scheduleService.create(roomId, new ScheduleCreateCommand(2, LocalDate.of(2026, 5, 3))))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SCHEDULE_DATE_MISMATCH);
    }
}
```

- [ ] **Step 2: 테스트가 올바르게 실패하는지 확인**

Run: `./gradlew test --tests com.howaboutus.backend.schedules.service.ScheduleServiceTest`

Expected: `ScheduleServiceTest` 컴파일 실패 또는 `ScheduleService`/`Schedule`/`ScheduleRepository`/에러 코드 누락으로 실패

- [ ] **Step 3: 일정 일자 엔티티, 저장소, 서비스 최소 구현**

```java
@Entity
@Table(
        name = "schedules",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"room_id", "day_number"}),
                @UniqueConstraint(columnNames = {"room_id", "date"})
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Schedule extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Column(name = "day_number", nullable = false)
    private int dayNumber;

    @Column(nullable = false)
    private LocalDate date;

    private Schedule(Room room, int dayNumber, LocalDate date) {
        this.room = room;
        this.dayNumber = dayNumber;
        this.date = date;
    }

    public static Schedule create(Room room, int dayNumber, LocalDate date) {
        return new Schedule(room, dayNumber, date);
    }
}

public interface ScheduleRepository extends JpaRepository<Schedule, Long> {

    boolean existsByRoom_IdAndDayNumber(UUID roomId, int dayNumber);

    boolean existsByRoom_IdAndDate(UUID roomId, LocalDate date);

    List<Schedule> findAllByRoom_IdOrderByDayNumberAsc(UUID roomId);

    Optional<Schedule> findByIdAndRoom_Id(Long scheduleId, UUID roomId);
}

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScheduleService {

    private final RoomRepository roomRepository;
    private final ScheduleRepository scheduleRepository;

    @Transactional
    public ScheduleResult create(UUID roomId, ScheduleCreateCommand command) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.ROOM_NOT_FOUND));
        validateTravelDate(room, command.dayNumber(), command.date());
        validateUniqueness(roomId, command.dayNumber(), command.date());

        Schedule schedule = Schedule.create(room, command.dayNumber(), command.date());
        return ScheduleResult.from(scheduleRepository.saveAndFlush(schedule));
    }

    public List<ScheduleResult> getSchedules(UUID roomId) {
        ensureRoomExists(roomId);
        return scheduleRepository.findAllByRoom_IdOrderByDayNumberAsc(roomId).stream()
                .map(ScheduleResult::from)
                .toList();
    }

    @Transactional
    public void delete(UUID roomId, Long scheduleId) {
        Schedule schedule = scheduleRepository.findByIdAndRoom_Id(scheduleId, roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.SCHEDULE_NOT_FOUND));
        scheduleRepository.delete(schedule);
    }

    private void validateTravelDate(Room room, int dayNumber, LocalDate date) {
        LocalDate expectedDate = room.getStartDate().plusDays(dayNumber - 1L);
        if (dayNumber < 1 || date.isBefore(room.getStartDate()) || date.isAfter(room.getEndDate()) || !expectedDate.equals(date)) {
            throw new CustomException(ErrorCode.SCHEDULE_DATE_MISMATCH);
        }
    }

    private void validateUniqueness(UUID roomId, int dayNumber, LocalDate date) {
        if (scheduleRepository.existsByRoom_IdAndDayNumber(roomId, dayNumber)
                || scheduleRepository.existsByRoom_IdAndDate(roomId, date)) {
            throw new CustomException(ErrorCode.SCHEDULE_ALREADY_EXISTS);
        }
    }

    private void ensureRoomExists(UUID roomId) {
        if (!roomRepository.existsById(roomId)) {
            throw new CustomException(ErrorCode.ROOM_NOT_FOUND);
        }
    }
}

public enum ErrorCode {
    SCHEDULE_NOT_FOUND(HttpStatus.NOT_FOUND, "일정을 찾을 수 없습니다"),
    SCHEDULE_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 존재하는 일정입니다"),
    SCHEDULE_DATE_MISMATCH(HttpStatus.BAD_REQUEST, "여행 날짜와 일차 정보가 일치하지 않습니다")
}
```

- [ ] **Step 4: 일정 일자 서비스 테스트 통과 확인**

Run: `./gradlew test --tests com.howaboutus.backend.schedules.service.ScheduleServiceTest`

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/howaboutus/backend/common/error/ErrorCode.java \
  src/main/java/com/howaboutus/backend/schedules/entity/Schedule.java \
  src/main/java/com/howaboutus/backend/schedules/repository/ScheduleRepository.java \
  src/main/java/com/howaboutus/backend/schedules/service/ScheduleService.java \
  src/main/java/com/howaboutus/backend/schedules/service/dto/ScheduleCreateCommand.java \
  src/main/java/com/howaboutus/backend/schedules/service/dto/ScheduleResult.java \
  src/test/java/com/howaboutus/backend/schedules/service/ScheduleServiceTest.java
git commit -m "feat: 일정 일자 도메인 추가"
```

## Task 2: 일정 일자 컨트롤러와 요청 검증 추가

**Files:**
- Create: `src/main/java/com/howaboutus/backend/schedules/controller/ScheduleController.java`
- Create: `src/main/java/com/howaboutus/backend/schedules/controller/dto/CreateScheduleRequest.java`
- Create: `src/main/java/com/howaboutus/backend/schedules/controller/dto/ScheduleResponse.java`
- Create: `src/test/java/com/howaboutus/backend/schedules/controller/ScheduleControllerTest.java`

- [ ] **Step 1: 일정 일자 컨트롤러 실패 테스트 작성**

```java
@WebMvcTest(ScheduleController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class ScheduleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ScheduleService scheduleService;

    @Test
    @DisplayName("dayNumber가 없으면 400을 반환한다")
    void returnsBadRequestWhenDayNumberMissing() throws Exception {
        mockMvc.perform(post("/rooms/{roomId}/schedules", ROOM_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"date": "2026-05-02"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));

        verifyNoInteractions(scheduleService);
    }

    @Test
    @DisplayName("일정 생성 성공 시 201을 반환한다")
    void createsScheduleSuccessfully() throws Exception {
        given(scheduleService.create(eq(ROOM_ID), any(ScheduleCreateCommand.class))).willReturn(SCHEDULE_RESULT);

        mockMvc.perform(post("/rooms/{roomId}/schedules", ROOM_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"dayNumber": 2, "date": "2026-05-02"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.scheduleId").value(10L))
                .andExpect(jsonPath("$.dayNumber").value(2))
                .andExpect(jsonPath("$.date").value("2026-05-02"));
    }
}
```

- [ ] **Step 2: 테스트가 올바르게 실패하는지 확인**

Run: `./gradlew test --tests com.howaboutus.backend.schedules.controller.ScheduleControllerTest`

Expected: `ScheduleController`/DTO/응답 타입 누락으로 실패

- [ ] **Step 3: 일정 일자 컨트롤러 최소 구현**

```java
public record CreateScheduleRequest(
        @NotNull(message = "dayNumber는 필수입니다")
        @Min(value = 1, message = "dayNumber는 1 이상이어야 합니다")
        Integer dayNumber,
        @NotNull(message = "date는 필수입니다")
        LocalDate date
) {
    public ScheduleCreateCommand toCommand() {
        return new ScheduleCreateCommand(dayNumber, date);
    }
}

public record ScheduleResponse(
        Long scheduleId,
        UUID roomId,
        int dayNumber,
        LocalDate date,
        Instant createdAt
) {
    public static ScheduleResponse from(ScheduleResult result) {
        return new ScheduleResponse(
                result.scheduleId(),
                result.roomId(),
                result.dayNumber(),
                result.date(),
                result.createdAt()
        );
    }
}

@RestController
@RequiredArgsConstructor
@RequestMapping("/rooms/{roomId}/schedules")
public class ScheduleController {

    private final ScheduleService scheduleService;

    @PostMapping
    public ResponseEntity<ScheduleResponse> create(
            @PathVariable UUID roomId,
            @RequestBody @Valid CreateScheduleRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ScheduleResponse.from(scheduleService.create(roomId, request.toCommand())));
    }

    @GetMapping
    public List<ScheduleResponse> getSchedules(@PathVariable UUID roomId) {
        return scheduleService.getSchedules(roomId).stream()
                .map(ScheduleResponse::from)
                .toList();
    }

    @DeleteMapping("/{scheduleId}")
    public ResponseEntity<Void> delete(@PathVariable UUID roomId, @PathVariable Long scheduleId) {
        scheduleService.delete(roomId, scheduleId);
        return ResponseEntity.noContent().build();
    }
}
```

- [ ] **Step 4: 일정 일자 컨트롤러 테스트 통과 확인**

Run: `./gradlew test --tests com.howaboutus.backend.schedules.controller.ScheduleControllerTest`

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/howaboutus/backend/schedules/controller/ScheduleController.java \
  src/main/java/com/howaboutus/backend/schedules/controller/dto/CreateScheduleRequest.java \
  src/main/java/com/howaboutus/backend/schedules/controller/dto/ScheduleResponse.java \
  src/test/java/com/howaboutus/backend/schedules/controller/ScheduleControllerTest.java
git commit -m "feat: 일정 일자 API 추가"
```

## Task 3: 일정 일자 통합 테스트로 HTTP 흐름 고정

**Files:**
- Create: `src/test/java/com/howaboutus/backend/schedules/ScheduleIntegrationTest.java`

- [ ] **Step 1: 일정 일자 통합 실패 테스트 작성**

```java
@AutoConfigureMockMvc
@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
class ScheduleIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private ScheduleRepository scheduleRepository;

    @AfterEach
    void tearDown() {
        scheduleRepository.deleteAll();
        roomRepository.deleteAll();
    }

    @Test
    @DisplayName("일정 일자 생성 후 목록 조회와 삭제가 동작한다")
    void scheduleCrudFlowWorksEndToEnd() throws Exception {
        Room room = roomRepository.save(Room.create(
                "도쿄 여행",
                "도쿄",
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 3),
                "TOKYO-INTEGRATION",
                1L
        ));

        mockMvc.perform(post("/rooms/{roomId}/schedules", room.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"dayNumber": 1, "date": "2026-05-01"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.dayNumber").value(1));

        Schedule schedule = scheduleRepository.findAll().getFirst();

        mockMvc.perform(get("/rooms/{roomId}/schedules", room.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].scheduleId").value(schedule.getId()))
                .andExpect(jsonPath("$[0].date").value("2026-05-01"));

        mockMvc.perform(delete("/rooms/{roomId}/schedules/{scheduleId}", room.getId(), schedule.getId()))
                .andExpect(status().isNoContent());

        assertThat(scheduleRepository.findAll()).isEmpty();
    }
}
```

- [ ] **Step 2: 테스트가 올바르게 실패하는지 확인**

Run: `./gradlew test --tests com.howaboutus.backend.schedules.ScheduleIntegrationTest`

Expected: 엔드포인트 미노출 또는 JPA 매핑 미완성으로 실패

- [ ] **Step 3: 서비스 삭제 로직과 조회 경로 보강**

```java
public void delete(UUID roomId, Long scheduleId) {
    ensureRoomExists(roomId);
    Schedule schedule = scheduleRepository.findByIdAndRoom_Id(scheduleId, roomId)
            .orElseThrow(() -> new CustomException(ErrorCode.SCHEDULE_NOT_FOUND));
    scheduleRepository.delete(schedule);
}

public List<ScheduleResult> getSchedules(UUID roomId) {
    ensureRoomExists(roomId);
    return scheduleRepository.findAllByRoom_IdOrderByDayNumberAsc(roomId).stream()
            .map(ScheduleResult::from)
            .toList();
}
```

- [ ] **Step 4: 일정 일자 통합 테스트 통과 확인**

Run: `./gradlew test --tests com.howaboutus.backend.schedules.ScheduleIntegrationTest`

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: 커밋**

```bash
git add src/test/java/com/howaboutus/backend/schedules/ScheduleIntegrationTest.java \
  src/main/java/com/howaboutus/backend/schedules/service/ScheduleService.java
git commit -m "test: 일정 일자 통합 흐름 검증 추가"
```

## Task 4: ScheduleItem 도메인과 일정 항목 서비스 추가

**Files:**
- Create: `src/main/java/com/howaboutus/backend/schedules/entity/ScheduleItem.java`
- Create: `src/main/java/com/howaboutus/backend/schedules/repository/ScheduleItemRepository.java`
- Create: `src/main/java/com/howaboutus/backend/schedules/service/ScheduleItemService.java`
- Create: `src/main/java/com/howaboutus/backend/schedules/service/dto/ScheduleItemCreateCommand.java`
- Create: `src/main/java/com/howaboutus/backend/schedules/service/dto/ScheduleItemUpdateCommand.java`
- Create: `src/main/java/com/howaboutus/backend/schedules/service/dto/ScheduleItemResult.java`
- Create: `src/test/java/com/howaboutus/backend/schedules/service/ScheduleItemServiceTest.java`
- Modify: `src/main/java/com/howaboutus/backend/common/error/ErrorCode.java`
- Modify: `src/main/java/com/howaboutus/backend/schedules/service/ScheduleService.java`

- [ ] **Step 1: 일정 항목 서비스 실패 테스트 작성**

```java
@ExtendWith(MockitoExtension.class)
class ScheduleItemServiceTest {

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private ScheduleRepository scheduleRepository;

    @Mock
    private ScheduleItemRepository scheduleItemRepository;

    private ScheduleItemService scheduleItemService;

    @BeforeEach
    void setUp() {
        scheduleItemService = new ScheduleItemService(roomRepository, scheduleRepository, scheduleItemRepository);
    }

    @Test
    @DisplayName("일정 항목 생성 시 마지막 orderIndex 다음 값이 부여된다")
    void createAssignsNextOrderIndex() {
        UUID roomId = UUID.randomUUID();
        Room room = Room.create(
                "도쿄 여행",
                "도쿄",
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 3),
                "TOKYO-ITEM",
                1L
        );
        ReflectionTestUtils.setField(room, "id", roomId);

        Schedule schedule = Schedule.create(room, 1, LocalDate.of(2026, 5, 1));
        ReflectionTestUtils.setField(schedule, "id", 10L);

        ScheduleItem savedItem = ScheduleItem.create(schedule, "place-1", LocalTime.of(9, 0), 90, 2);
        ReflectionTestUtils.setField(savedItem, "id", 20L);
        ReflectionTestUtils.setField(savedItem, "createdAt", Instant.parse("2026-05-01T00:00:00Z"));

        given(roomRepository.existsById(roomId)).willReturn(true);
        given(scheduleRepository.findByIdAndRoom_Id(10L, roomId)).willReturn(Optional.of(schedule));
        given(scheduleItemRepository.findMaxOrderIndexBySchedule_Id(10L)).willReturn(Optional.of(1));
        given(scheduleItemRepository.saveAndFlush(any(ScheduleItem.class))).willReturn(savedItem);

        ScheduleItemResult result = scheduleItemService.create(
                roomId,
                10L,
                new ScheduleItemCreateCommand("place-1", LocalTime.of(9, 0), 90)
        );

        assertThat(result.orderIndex()).isEqualTo(2);
    }

    @Test
    @DisplayName("항목 삭제 후 남은 항목 순서를 연속 값으로 재정렬한다")
    void deleteReordersRemainingItems() {
        UUID roomId = UUID.randomUUID();
        Room room = Room.create(
                "도쿄 여행",
                "도쿄",
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 3),
                "TOKYO-ITEM",
                1L
        );
        ReflectionTestUtils.setField(room, "id", roomId);

        Schedule schedule = Schedule.create(room, 1, LocalDate.of(2026, 5, 1));
        ReflectionTestUtils.setField(schedule, "id", 10L);

        ScheduleItem first = ScheduleItem.create(schedule, "place-1", null, null, 0);
        ScheduleItem second = ScheduleItem.create(schedule, "place-2", null, null, 1);
        ReflectionTestUtils.setField(first, "id", 20L);
        ReflectionTestUtils.setField(second, "id", 21L);

        given(roomRepository.existsById(roomId)).willReturn(true);
        given(scheduleRepository.findByIdAndRoom_Id(10L, roomId)).willReturn(Optional.of(schedule));
        given(scheduleItemRepository.findByIdAndSchedule_Id(20L, 10L)).willReturn(Optional.of(first));
        given(scheduleItemRepository.findAllBySchedule_IdOrderByOrderIndexAsc(10L)).willReturn(List.of(second));

        scheduleItemService.delete(roomId, 10L, 20L);

        assertThat(second.getOrderIndex()).isEqualTo(0);
        verify(scheduleItemRepository).delete(first);
    }
}
```

- [ ] **Step 2: 테스트가 올바르게 실패하는지 확인**

Run: `./gradlew test --tests com.howaboutus.backend.schedules.service.ScheduleItemServiceTest`

Expected: `ScheduleItem`/`ScheduleItemService`/`ScheduleItemRepository` 누락으로 실패

- [ ] **Step 3: 일정 항목 엔티티, 저장소, 서비스 최소 구현**

```java
@Entity
@Table(
        name = "schedule_items",
        indexes = {
                @Index(name = "idx_schedule_items_schedule_id_order_index", columnList = "schedule_id, order_index"),
                @Index(name = "idx_schedule_items_google_place_id", columnList = "google_place_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScheduleItem extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "schedule_id", nullable = false)
    private Schedule schedule;

    @Column(name = "google_place_id", nullable = false, length = 300)
    private String googlePlaceId;

    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Column(name = "order_index", nullable = false)
    private int orderIndex;

    @Column(name = "travel_mode", length = 20)
    private String travelMode;

    @Column(name = "distance_meters")
    private Integer distanceMeters;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    private ScheduleItem(Schedule schedule, String googlePlaceId, LocalTime startTime, Integer durationMinutes, int orderIndex) {
        this.schedule = schedule;
        this.googlePlaceId = googlePlaceId;
        this.startTime = startTime;
        this.durationMinutes = durationMinutes;
        this.orderIndex = orderIndex;
    }

    public static ScheduleItem create(Schedule schedule, String googlePlaceId, LocalTime startTime, Integer durationMinutes, int orderIndex) {
        return new ScheduleItem(schedule, googlePlaceId, startTime, durationMinutes, orderIndex);
    }

    public void updateTime(LocalTime startTime, Integer durationMinutes) {
        this.startTime = startTime;
        this.durationMinutes = durationMinutes;
    }

    public void changeOrderIndex(int orderIndex) {
        this.orderIndex = orderIndex;
    }
}

public interface ScheduleItemRepository extends JpaRepository<ScheduleItem, Long> {

    @Query("select coalesce(max(si.orderIndex), -1) from ScheduleItem si where si.schedule.id = :scheduleId")
    Optional<Integer> findMaxOrderIndexBySchedule_Id(@Param("scheduleId") Long scheduleId);

    List<ScheduleItem> findAllBySchedule_IdOrderByOrderIndexAsc(Long scheduleId);

    Optional<ScheduleItem> findByIdAndSchedule_Id(Long itemId, Long scheduleId);

    void deleteAllBySchedule_Id(Long scheduleId);
}

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScheduleItemService {

    private final RoomRepository roomRepository;
    private final ScheduleRepository scheduleRepository;
    private final ScheduleItemRepository scheduleItemRepository;

    @Transactional
    public ScheduleItemResult create(UUID roomId, Long scheduleId, ScheduleItemCreateCommand command) {
        ensureRoomExists(roomId);
        Schedule schedule = getSchedule(roomId, scheduleId);
        int nextOrderIndex = scheduleItemRepository.findMaxOrderIndexBySchedule_Id(scheduleId).orElse(-1) + 1;
        ScheduleItem item = ScheduleItem.create(schedule, command.googlePlaceId(), command.startTime(), command.durationMinutes(), nextOrderIndex);
        return ScheduleItemResult.from(scheduleItemRepository.saveAndFlush(item));
    }

    public List<ScheduleItemResult> getItems(UUID roomId, Long scheduleId) {
        ensureRoomExists(roomId);
        getSchedule(roomId, scheduleId);
        return scheduleItemRepository.findAllBySchedule_IdOrderByOrderIndexAsc(scheduleId).stream()
                .map(ScheduleItemResult::from)
                .toList();
    }

    @Transactional
    public ScheduleItemResult update(UUID roomId, Long scheduleId, Long itemId, ScheduleItemUpdateCommand command) {
        ensureRoomExists(roomId);
        getSchedule(roomId, scheduleId);
        ScheduleItem item = scheduleItemRepository.findByIdAndSchedule_Id(itemId, scheduleId)
                .orElseThrow(() -> new CustomException(ErrorCode.SCHEDULE_ITEM_NOT_FOUND));
        item.updateTime(command.startTime(), command.durationMinutes());
        return ScheduleItemResult.from(scheduleItemRepository.saveAndFlush(item));
    }

    @Transactional
    public void delete(UUID roomId, Long scheduleId, Long itemId) {
        ensureRoomExists(roomId);
        getSchedule(roomId, scheduleId);
        ScheduleItem item = scheduleItemRepository.findByIdAndSchedule_Id(itemId, scheduleId)
                .orElseThrow(() -> new CustomException(ErrorCode.SCHEDULE_ITEM_NOT_FOUND));
        scheduleItemRepository.delete(item);
        reorder(scheduleId);
    }

    @Transactional
    public void deleteAllByScheduleId(Long scheduleId) {
        scheduleItemRepository.deleteAllBySchedule_Id(scheduleId);
    }

    private Schedule getSchedule(UUID roomId, Long scheduleId) {
        return scheduleRepository.findByIdAndRoom_Id(scheduleId, roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.SCHEDULE_NOT_FOUND));
    }

    private void ensureRoomExists(UUID roomId) {
        if (!roomRepository.existsById(roomId)) {
            throw new CustomException(ErrorCode.ROOM_NOT_FOUND);
        }
    }

    private void reorder(Long scheduleId) {
        List<ScheduleItem> items = scheduleItemRepository.findAllBySchedule_IdOrderByOrderIndexAsc(scheduleId);
        for (int i = 0; i < items.size(); i++) {
            items.get(i).changeOrderIndex(i);
        }
    }
}

public enum ErrorCode {
    SCHEDULE_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "일정 항목을 찾을 수 없습니다")
}
```

- [ ] **Step 4: 일정 항목 서비스 테스트 통과 확인**

Run: `./gradlew test --tests com.howaboutus.backend.schedules.service.ScheduleItemServiceTest`

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/howaboutus/backend/common/error/ErrorCode.java \
  src/main/java/com/howaboutus/backend/schedules/entity/ScheduleItem.java \
  src/main/java/com/howaboutus/backend/schedules/repository/ScheduleItemRepository.java \
  src/main/java/com/howaboutus/backend/schedules/service/ScheduleItemService.java \
  src/main/java/com/howaboutus/backend/schedules/service/dto/ScheduleItemCreateCommand.java \
  src/main/java/com/howaboutus/backend/schedules/service/dto/ScheduleItemUpdateCommand.java \
  src/main/java/com/howaboutus/backend/schedules/service/dto/ScheduleItemResult.java \
  src/test/java/com/howaboutus/backend/schedules/service/ScheduleItemServiceTest.java
git commit -m "feat: 일정 항목 도메인 추가"
```

## Task 5: 일정 일자 삭제와 연동되는 일정 항목 삭제 정책 연결

**Files:**
- Modify: `src/main/java/com/howaboutus/backend/schedules/service/ScheduleService.java`

- [ ] **Step 1: 일정 삭제 시 항목 삭제 실패 테스트 작성**

```java
@Test
@DisplayName("일정 삭제 시 하위 일정 항목도 함께 삭제한다")
void deleteAlsoRemovesScheduleItems() {
    UUID roomId = UUID.randomUUID();
    Room room = Room.create(
            "도쿄 여행",
            "도쿄",
            LocalDate.of(2026, 5, 1),
            LocalDate.of(2026, 5, 3),
            "TOKYO-SCHEDULE",
            1L
    );
    ReflectionTestUtils.setField(room, "id", roomId);

    Schedule schedule = Schedule.create(room, 1, LocalDate.of(2026, 5, 1));
    ReflectionTestUtils.setField(schedule, "id", 10L);

    given(roomRepository.existsById(roomId)).willReturn(true);
    given(scheduleRepository.findByIdAndRoom_Id(10L, roomId)).willReturn(Optional.of(schedule));

    scheduleService.delete(roomId, 10L);

    verify(scheduleItemService).deleteAllByScheduleId(10L);
    verify(scheduleRepository).delete(schedule);
}
```

- [ ] **Step 2: 테스트가 올바르게 실패하는지 확인**

Run: `./gradlew test --tests com.howaboutus.backend.schedules.service.ScheduleServiceTest`

Expected: `ScheduleService` 생성자 시그니처 또는 `scheduleItemService` 호출 누락으로 실패

- [ ] **Step 3: 일정 삭제 시 하위 항목 삭제 연결**

```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScheduleService {

    private final RoomRepository roomRepository;
    private final ScheduleRepository scheduleRepository;
    private final ScheduleItemService scheduleItemService;

    @Transactional
    public void delete(UUID roomId, Long scheduleId) {
        ensureRoomExists(roomId);
        Schedule schedule = scheduleRepository.findByIdAndRoom_Id(scheduleId, roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.SCHEDULE_NOT_FOUND));
        scheduleItemService.deleteAllByScheduleId(scheduleId);
        scheduleRepository.delete(schedule);
    }
}
```

- [ ] **Step 4: 일정 서비스 테스트 재실행**

Run: `./gradlew test --tests com.howaboutus.backend.schedules.service.ScheduleServiceTest`

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/howaboutus/backend/schedules/service/ScheduleService.java \
  src/test/java/com/howaboutus/backend/schedules/service/ScheduleServiceTest.java
git commit -m "feat: 일정 삭제 시 항목 정리 추가"
```

## Task 6: 일정 항목 컨트롤러와 요청 검증 추가

**Files:**
- Create: `src/main/java/com/howaboutus/backend/schedules/controller/ScheduleItemController.java`
- Create: `src/main/java/com/howaboutus/backend/schedules/controller/dto/CreateScheduleItemRequest.java`
- Create: `src/main/java/com/howaboutus/backend/schedules/controller/dto/UpdateScheduleItemRequest.java`
- Create: `src/main/java/com/howaboutus/backend/schedules/controller/dto/ScheduleItemResponse.java`
- Create: `src/test/java/com/howaboutus/backend/schedules/controller/ScheduleItemControllerTest.java`

- [ ] **Step 1: 일정 항목 컨트롤러 실패 테스트 작성**

```java
@WebMvcTest(ScheduleItemController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class ScheduleItemControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ScheduleItemService scheduleItemService;

    @Test
    @DisplayName("googlePlaceId가 공백이면 400을 반환한다")
    void returnsBadRequestWhenGooglePlaceIdIsBlank() throws Exception {
        mockMvc.perform(post("/rooms/{roomId}/schedules/{scheduleId}/items", ROOM_ID, 10L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"googlePlaceId": "   "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));

        verifyNoInteractions(scheduleItemService);
    }

    @Test
    @DisplayName("일정 항목 시간 수정 성공 시 200을 반환한다")
    void updatesScheduleItemSuccessfully() throws Exception {
        given(scheduleItemService.update(eq(ROOM_ID), eq(10L), eq(20L), any(ScheduleItemUpdateCommand.class)))
                .willReturn(SCHEDULE_ITEM_RESULT);

        mockMvc.perform(patch("/rooms/{roomId}/schedules/{scheduleId}/items/{itemId}", ROOM_ID, 10L, 20L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"startTime": "09:00:00", "durationMinutes": 90}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemId").value(20L))
                .andExpect(jsonPath("$.orderIndex").value(0));
    }
}
```

- [ ] **Step 2: 테스트가 올바르게 실패하는지 확인**

Run: `./gradlew test --tests com.howaboutus.backend.schedules.controller.ScheduleItemControllerTest`

Expected: 컨트롤러/DTO/응답 타입 누락으로 실패

- [ ] **Step 3: 일정 항목 컨트롤러 최소 구현**

```java
public record CreateScheduleItemRequest(
        @NotBlank(message = "googlePlaceId는 공백일 수 없습니다")
        @Size(max = 300, message = "googlePlaceId는 300자 이하여야 합니다")
        @Pattern(regexp = "[A-Za-z0-9_\\-:]+", message = "googlePlaceId 형식이 올바르지 않습니다")
        String googlePlaceId,
        LocalTime startTime,
        @Positive(message = "durationMinutes는 1 이상이어야 합니다")
        Integer durationMinutes
) {
    public ScheduleItemCreateCommand toCommand() {
        return new ScheduleItemCreateCommand(googlePlaceId, startTime, durationMinutes);
    }
}

public record UpdateScheduleItemRequest(
        LocalTime startTime,
        @Positive(message = "durationMinutes는 1 이상이어야 합니다")
        Integer durationMinutes
) {
    public ScheduleItemUpdateCommand toCommand() {
        return new ScheduleItemUpdateCommand(startTime, durationMinutes);
    }
}

@RestController
@RequiredArgsConstructor
@RequestMapping("/rooms/{roomId}/schedules/{scheduleId}/items")
public class ScheduleItemController {

    private final ScheduleItemService scheduleItemService;

    @PostMapping
    public ResponseEntity<ScheduleItemResponse> create(
            @PathVariable UUID roomId,
            @PathVariable Long scheduleId,
            @RequestBody @Valid CreateScheduleItemRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ScheduleItemResponse.from(scheduleItemService.create(roomId, scheduleId, request.toCommand())));
    }

    @GetMapping
    public List<ScheduleItemResponse> getItems(@PathVariable UUID roomId, @PathVariable Long scheduleId) {
        return scheduleItemService.getItems(roomId, scheduleId).stream()
                .map(ScheduleItemResponse::from)
                .toList();
    }

    @PatchMapping("/{itemId}")
    public ScheduleItemResponse update(
            @PathVariable UUID roomId,
            @PathVariable Long scheduleId,
            @PathVariable Long itemId,
            @RequestBody @Valid UpdateScheduleItemRequest request
    ) {
        return ScheduleItemResponse.from(scheduleItemService.update(roomId, scheduleId, itemId, request.toCommand()));
    }

    @DeleteMapping("/{itemId}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID roomId,
            @PathVariable Long scheduleId,
            @PathVariable Long itemId
    ) {
        scheduleItemService.delete(roomId, scheduleId, itemId);
        return ResponseEntity.noContent().build();
    }
}
```

- [ ] **Step 4: 일정 항목 컨트롤러 테스트 통과 확인**

Run: `./gradlew test --tests com.howaboutus.backend.schedules.controller.ScheduleItemControllerTest`

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/howaboutus/backend/schedules/controller/ScheduleItemController.java \
  src/main/java/com/howaboutus/backend/schedules/controller/dto/CreateScheduleItemRequest.java \
  src/main/java/com/howaboutus/backend/schedules/controller/dto/UpdateScheduleItemRequest.java \
  src/main/java/com/howaboutus/backend/schedules/controller/dto/ScheduleItemResponse.java \
  src/test/java/com/howaboutus/backend/schedules/controller/ScheduleItemControllerTest.java
git commit -m "feat: 일정 항목 API 추가"
```

## Task 7: 일정 항목 통합 테스트로 순서와 수정 흐름 고정

**Files:**
- Modify: `src/test/java/com/howaboutus/backend/schedules/ScheduleIntegrationTest.java`

- [ ] **Step 1: 일정 항목 통합 실패 테스트 작성**

```java
@Test
@DisplayName("일정 항목 생성 후 목록 조회, 시간 수정, 삭제 시 순서 재정렬이 동작한다")
void scheduleItemFlowWorksEndToEnd() throws Exception {
    Room room = roomRepository.save(Room.create(
            "도쿄 여행",
            "도쿄",
            LocalDate.of(2026, 5, 1),
            LocalDate.of(2026, 5, 3),
            "TOKYO-ITEM-INTEGRATION",
            1L
    ));

    Schedule schedule = scheduleRepository.saveAndFlush(Schedule.create(room, 1, LocalDate.of(2026, 5, 1)));

    mockMvc.perform(post("/rooms/{roomId}/schedules/{scheduleId}/items", room.getId(), schedule.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"googlePlaceId": "place-1", "startTime": "09:00:00", "durationMinutes": 90}
                            """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.orderIndex").value(0));

    mockMvc.perform(post("/rooms/{roomId}/schedules/{scheduleId}/items", room.getId(), schedule.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"googlePlaceId": "place-2"}
                            """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.orderIndex").value(1));

    ScheduleItem firstItem = scheduleItemRepository.findAllBySchedule_IdOrderByOrderIndexAsc(schedule.getId()).getFirst();

    mockMvc.perform(patch("/rooms/{roomId}/schedules/{scheduleId}/items/{itemId}", room.getId(), schedule.getId(), firstItem.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"startTime": "10:00:00", "durationMinutes": 120}
                            """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.startTime").value("10:00:00"))
            .andExpect(jsonPath("$.durationMinutes").value(120));

    mockMvc.perform(delete("/rooms/{roomId}/schedules/{scheduleId}/items/{itemId}", room.getId(), schedule.getId(), firstItem.getId()))
            .andExpect(status().isNoContent());

    mockMvc.perform(get("/rooms/{roomId}/schedules/{scheduleId}/items", room.getId(), schedule.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].orderIndex").value(0));
}
```

- [ ] **Step 2: 테스트가 올바르게 실패하는지 확인**

Run: `./gradlew test --tests com.howaboutus.backend.schedules.ScheduleIntegrationTest`

Expected: 일정 항목 엔드포인트 또는 저장소 연결 미완성으로 실패

- [ ] **Step 3: 통합 테스트에 필요한 저장소 주입과 조회 경로 보강**

```java
@Autowired
private ScheduleItemRepository scheduleItemRepository;

@AfterEach
void tearDown() {
    scheduleItemRepository.deleteAll();
    scheduleRepository.deleteAll();
    roomRepository.deleteAll();
}
```

- [ ] **Step 4: 일정 항목 통합 테스트 통과 확인**

Run: `./gradlew test --tests com.howaboutus.backend.schedules.ScheduleIntegrationTest`

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: 커밋**

```bash
git add src/test/java/com/howaboutus/backend/schedules/ScheduleIntegrationTest.java
git commit -m "test: 일정 항목 통합 흐름 검증 추가"
```

## Task 8: 문서 반영과 최종 검증

**Files:**
- Modify: `docs/ai/features.md`
- Modify: `docs/ai/erd.md`

- [ ] **Step 1: 문서 변경 실패 확인 대신 기준 문구 찾기**

```md
## docs/ai/features.md 변경 포인트
- `[ ] 일정 항목 메모 수정` → 1차 범위 제외 또는 후속 단계로 상태 조정
- 일정 1차 범위를 `일정 생성/목록/삭제`, `일정에 장소 추가/목록/삭제`, `시간 설정` 중심으로 명시

## docs/ai/erd.md 변경 포인트
- `schedule_items.memo`는 1차 구현 제외 상태를 설명으로 보정
- `order_index` 삭제 후 연속 재배치 정책 추가
```

- [ ] **Step 2: 기능/ERD 문서 갱신**

```md
| `[x]` | 일정 생성 | N일차 + 날짜 등록 | schedules |
| `[x]` | 일정 목록 조회 | 방의 전체 일자별 일정 조회 | schedules |
| `[x]` | 일정 삭제 | 특정 일자 삭제 (하위 items 포함) | schedules |
| `[x]` | 일정에 장소 추가 | 특정 일자에 장소 추가 (보관함 또는 검색에서 바로) | schedule_items |
| `[x]` | 일정 항목 목록 조회 | 특정 일자의 장소 목록 (order_index 순) | schedule_items |
| `[x]` | 일정 항목 삭제 | 일자에서 장소 제거 | schedule_items |
| `[x]` | 시간 설정 | start_time, duration_minutes 설정 | schedule_items |
| `[-]` | 일정 항목 메모 수정 | 1차 구현 범위 제외 | schedule_items |
```

- [ ] **Step 3: 일정 관련 테스트와 전체 빌드 검증**

Run:

```bash
./gradlew test --tests com.howaboutus.backend.schedules.service.ScheduleServiceTest
./gradlew test --tests com.howaboutus.backend.schedules.service.ScheduleItemServiceTest
./gradlew test --tests com.howaboutus.backend.schedules.controller.ScheduleControllerTest
./gradlew test --tests com.howaboutus.backend.schedules.controller.ScheduleItemControllerTest
./gradlew test --tests com.howaboutus.backend.schedules.ScheduleIntegrationTest
./gradlew build
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 4: Markdown 충돌 점검**

Run:

```bash
rg -n '`docs/ai/[^`]*\.md`|`[A-Z][A-Z_]*\.md`' -g '*.md' AGENTS.md CONTRIBUTING.md docs/ai docs/superpowers/specs docs/superpowers/plans
```

Expected: 새로 추가/수정한 문서 경로가 모두 존재하고, 잘못된 참조가 없음

- [ ] **Step 5: 최종 커밋**

```bash
git add docs/ai/features.md docs/ai/erd.md \
  src/main/java/com/howaboutus/backend/schedules \
  src/test/java/com/howaboutus/backend/schedules
git commit -m "feat: 일정 관리 1차 기능 구현"
```

## Self-Review

- Spec coverage:
  - 일정 일자 CRUD: Task 1, Task 2, Task 3
  - 일정 항목 생성/목록/삭제/시간 수정: Task 4, Task 6, Task 7
  - 일정 삭제 시 하위 항목 삭제: Task 5
  - 문서 갱신과 최종 검증: Task 8
- Placeholder scan: 금지 패턴 없이 구체 파일/명령/코드 포함
- Type consistency:
  - 서비스 DTO 명: `ScheduleCreateCommand`, `ScheduleItemCreateCommand`, `ScheduleItemUpdateCommand`
  - 응답 DTO 명: `ScheduleResponse`, `ScheduleItemResponse`
  - 에러 코드 명: `SCHEDULE_NOT_FOUND`, `SCHEDULE_ALREADY_EXISTS`, `SCHEDULE_DATE_MISMATCH`, `SCHEDULE_ITEM_NOT_FOUND`
