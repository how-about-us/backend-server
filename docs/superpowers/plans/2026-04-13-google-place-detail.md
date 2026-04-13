# Google Place Detail Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `GET /places/{googlePlaceId}` 장소 상세 조회 API를 추가하고, Google Places 상세 응답을 카드형 DTO로 정규화한 뒤 Redis에 3시간 TTL로 캐시한다.

**Architecture:** 기존 `PlaceController -> service -> common.integration.google` 구조를 유지하면서 상세 조회 전용 DTO와 클라이언트를 추가한다. 캐시는 `PlaceDetailService` 메서드에 `@Cacheable`을 적용하고, 현재 `CacheConfig`의 fail-open 에러 처리 덕분에 Redis 장애 시에도 Google API 호출로 자연스럽게 폴백한다.

**Tech Stack:** Spring Boot 4, Spring MVC, Spring Cache, Redis, Spring Security, `RestClient`, `MockRestServiceServer`, JUnit 5, Mockito

---

## File Structure

### Create

- `src/main/java/com/howaboutus/backend/common/integration/google/GooglePlaceDetailClient.java`
- `src/main/java/com/howaboutus/backend/common/integration/google/dto/GooglePlaceDetailResponse.java`
- `src/main/java/com/howaboutus/backend/places/controller/dto/PlaceDetailResponse.java`
- `src/main/java/com/howaboutus/backend/places/service/PlaceDetailService.java`
- `src/main/java/com/howaboutus/backend/places/service/dto/PlaceDetailResult.java`
- `src/test/java/com/howaboutus/backend/common/integration/google/GooglePlaceDetailClientTest.java`
- `src/test/java/com/howaboutus/backend/places/service/PlaceDetailServiceTest.java`
- `src/test/java/com/howaboutus/backend/places/service/dto/PlaceDetailResultTest.java`
- `src/test/java/com/howaboutus/backend/places/service/PlaceDetailCachingTest.java`

### Modify

- `src/main/java/com/howaboutus/backend/common/config/CachePolicy.java`
- `src/main/java/com/howaboutus/backend/common/config/GooglePlacesProperties.java`
- `src/main/java/com/howaboutus/backend/places/controller/PlaceController.java`
- `src/test/java/com/howaboutus/backend/places/controller/PlaceControllerTest.java`
- `src/test/resources/application-test.yaml`
- `src/main/resources/application.yaml`
- `docs/ai/features.md`

### Responsibilities

- `GooglePlacesProperties`: 검색용 field mask와 상세 조회용 field mask를 분리한다.
- `GooglePlaceDetailClient` + `GooglePlaceDetailResponse`: Google Places `Place Details (New)` 호출과 응답 역직렬화를 담당한다.
- `PlaceDetailResult`: 외부 응답을 우리 서비스의 안정적인 상세 DTO로 변환한다.
- `PlaceDetailService`: 상세 조회 오케스트레이션과 캐시 적용 지점을 담당한다.
- `PlaceDetailResponse`: 프론트에 반환할 API 응답 스키마다.
- `CachePolicy`: 장소 상세 캐시 이름과 3시간 TTL을 선언한다.
- `PlaceController` + 테스트: `GET /places/{googlePlaceId}` 엔드포인트와 오류 응답 계약을 검증한다.

## Chunk 1: Google Place Detail DTO And Client

### Task 1: Add the Google detail response mapping

**Files:**
- Create: `src/main/java/com/howaboutus/backend/common/integration/google/dto/GooglePlaceDetailResponse.java`
- Create: `src/main/java/com/howaboutus/backend/places/service/dto/PlaceDetailResult.java`
- Test: `src/test/java/com/howaboutus/backend/places/service/dto/PlaceDetailResultTest.java`

- [ ] **Step 1: Write the failing DTO mapping test**

```java
@DisplayName("Google 상세 응답을 장소 상세 결과로 매핑한다")
@Test
void createsDetailResultFromGoogleResponse() {
    GooglePlaceDetailResponse place = new GooglePlaceDetailResponse(
            "places/ChIJ123",
            new GooglePlaceDetailResponse.DisplayName("Cafe Layered", "ko"),
            "서울 종로구 ...",
            new GooglePlaceDetailResponse.Location(37.57, 126.98),
            "cafe",
            4.5,
            "02-123-4567",
            "https://layered.example",
            "https://maps.google.com/?cid=123",
            new GooglePlaceDetailResponse.RegularOpeningHours(List.of("월요일: 09:00~18:00")),
            List.of(
                    new GooglePlaceDetailResponse.Photo("places/ChIJ123/photos/a"),
                    new GooglePlaceDetailResponse.Photo("places/ChIJ123/photos/b")
            )
    );

    PlaceDetailResult result = PlaceDetailResult.from(place);

    assertThat(result.googlePlaceId()).isEqualTo("ChIJ123");
    assertThat(result.name()).isEqualTo("Cafe Layered");
    assertThat(result.location()).isEqualTo(new PlaceDetailResult.Location(37.57, 126.98));
    assertThat(result.weekdayDescriptions()).containsExactly("월요일: 09:00~18:00");
    assertThat(result.photoNames()).containsExactly(
            "places/ChIJ123/photos/a",
            "places/ChIJ123/photos/b"
    );
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run:

```bash
./gradlew test --tests 'com.howaboutus.backend.places.service.dto.PlaceDetailResultTest'
```

Expected:

```text
FAIL with compilation errors because GooglePlaceDetailResponse or PlaceDetailResult does not exist yet
```

- [ ] **Step 3: Add minimal response records and mapping**

```java
public record GooglePlaceDetailResponse(
        String id,
        DisplayName displayName,
        String formattedAddress,
        Location location,
        String primaryType,
        Double rating,
        String nationalPhoneNumber,
        String websiteUri,
        String googleMapsUri,
        RegularOpeningHours regularOpeningHours,
        List<Photo> photos
) {
    public record DisplayName(String text, String languageCode) {}
    public record Location(Double latitude, Double longitude) {}
    public record RegularOpeningHours(List<String> weekdayDescriptions) {}
    public record Photo(String name) {}
}
```

```java
public record PlaceDetailResult(
        String googlePlaceId,
        String name,
        String formattedAddress,
        Location location,
        String primaryType,
        Double rating,
        String phoneNumber,
        String websiteUri,
        String googleMapsUri,
        List<String> weekdayDescriptions,
        List<String> photoNames
) {
    public static PlaceDetailResult from(GooglePlaceDetailResponse place) {
        // id "places/ChIJ123" -> "ChIJ123"
    }

    public record Location(Double lat, Double lng) {}
}
```

- [ ] **Step 4: Run the mapping test to verify it passes**

Run:

```bash
./gradlew test --tests 'com.howaboutus.backend.places.service.dto.PlaceDetailResultTest'
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/howaboutus/backend/common/integration/google/dto/GooglePlaceDetailResponse.java \
        src/main/java/com/howaboutus/backend/places/service/dto/PlaceDetailResult.java \
        src/test/java/com/howaboutus/backend/places/service/dto/PlaceDetailResultTest.java
git commit -m "test: 장소 상세 응답 매핑 추가"
```

### Task 2: Add the Google Place Details client

**Files:**
- Create: `src/main/java/com/howaboutus/backend/common/integration/google/GooglePlaceDetailClient.java`
- Modify: `src/main/java/com/howaboutus/backend/common/config/GooglePlacesProperties.java`
- Modify: `src/main/resources/application.yaml`
- Modify: `src/test/resources/application-test.yaml`
- Test: `src/test/java/com/howaboutus/backend/common/integration/google/GooglePlaceDetailClientTest.java`

- [ ] **Step 1: Write the failing client test**

```java
@DisplayName("Google Places 상세 조회 엔드포인트로 올바른 헤더와 함께 요청한다")
@Test
void getsPlaceDetailUsingPlaceDetailsEndpoint() {
    server.expect(requestTo("https://places.googleapis.com/v1/places/ChIJ123"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("X-Goog-Api-Key", "test-key"))
            .andExpect(header(
                    "X-Goog-FieldMask",
                    "id,displayName,formattedAddress,location,primaryType,rating,nationalPhoneNumber,websiteUri,googleMapsUri,regularOpeningHours.weekdayDescriptions,photos.name"
            ))
            .andRespond(withSuccess("""
                    {
                      "id": "places/ChIJ123",
                      "displayName": {"text": "Cafe Layered", "languageCode": "ko"}
                    }
                    """, MediaType.APPLICATION_JSON));

    GooglePlaceDetailResponse result = client.getDetail("ChIJ123");

    assertThat(result.id()).isEqualTo("places/ChIJ123");
}
```

- [ ] **Step 2: Run the client test to verify it fails**

Run:

```bash
./gradlew test --tests 'com.howaboutus.backend.common.integration.google.GooglePlaceDetailClientTest'
```

Expected:

```text
FAIL with compilation errors because detail field mask or client does not exist yet
```

- [ ] **Step 3: Add minimal property split and RestClient wrapper**

```java
@ConfigurationProperties(prefix = "google.places")
public record GooglePlacesProperties(
        String apiKey,
        String baseUrl,
        String searchFieldMask,
        String detailFieldMask
) {
}
```

```java
@Component
@RequiredArgsConstructor
public class GooglePlaceDetailClient {

    private final RestClient googlePlacesRestClient;
    private final GooglePlacesProperties properties;

    public GooglePlaceDetailResponse getDetail(String googlePlaceId) {
        try {
            return googlePlacesRestClient.get()
                    .uri("/v1/places/{placeId}", googlePlaceId)
                    .header("X-Goog-Api-Key", properties.apiKey())
                    .header("X-Goog-FieldMask", properties.detailFieldMask())
                    .retrieve()
                    .body(GooglePlaceDetailResponse.class);
        } catch (RestClientException exception) {
            throw new ExternalApiException(exception);
        }
    }
}
```

- [ ] **Step 4: Update search client to use `searchFieldMask`**

Replace:

```java
.header("X-Goog-FieldMask", properties.fieldMask())
```

With:

```java
.header("X-Goog-FieldMask", properties.searchFieldMask())
```

- [ ] **Step 5: Run the client-related tests**

Run:

```bash
./gradlew test --tests 'com.howaboutus.backend.common.integration.google.GooglePlaceSearchClientTest' \
               --tests 'com.howaboutus.backend.common.integration.google.GooglePlaceDetailClientTest'
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/howaboutus/backend/common/config/GooglePlacesProperties.java \
        src/main/java/com/howaboutus/backend/common/integration/google/GooglePlaceSearchClient.java \
        src/main/java/com/howaboutus/backend/common/integration/google/GooglePlaceDetailClient.java \
        src/main/java/com/howaboutus/backend/common/integration/google/dto/GooglePlaceDetailResponse.java \
        src/main/resources/application.yaml \
        src/test/resources/application-test.yaml \
        src/test/java/com/howaboutus/backend/common/integration/google/GooglePlaceDetailClientTest.java \
        src/test/java/com/howaboutus/backend/common/integration/google/GooglePlaceSearchClientTest.java
git commit -m "feat: 구글 장소 상세 조회 클라이언트 추가"
```

## Chunk 2: Service, Cache, Controller, And Docs

### Task 3: Add the cached place detail service

**Files:**
- Create: `src/main/java/com/howaboutus/backend/places/service/PlaceDetailService.java`
- Modify: `src/main/java/com/howaboutus/backend/common/config/CachePolicy.java`
- Test: `src/test/java/com/howaboutus/backend/places/service/PlaceDetailServiceTest.java`
- Test: `src/test/java/com/howaboutus/backend/places/service/PlaceDetailCachingTest.java`

- [ ] **Step 1: Write the failing service mapping test**

```java
@DisplayName("상세 조회 클라이언트 응답을 서비스 결과로 변환한다")
@Test
void returnsMappedPlaceDetail() {
    given(googlePlaceDetailClient.getDetail("ChIJ123"))
            .willReturn(detailResponse());

    PlaceDetailResult result = placeDetailService.getDetail("ChIJ123");

    assertThat(result.googlePlaceId()).isEqualTo("ChIJ123");
    assertThat(result.phoneNumber()).isEqualTo("02-123-4567");
    then(googlePlaceDetailClient).should().getDetail("ChIJ123");
}
```

- [ ] **Step 2: Run the service test to verify it fails**

Run:

```bash
./gradlew test --tests 'com.howaboutus.backend.places.service.PlaceDetailServiceTest'
```

Expected:

```text
FAIL with compilation errors because PlaceDetailService does not exist yet
```

- [ ] **Step 3: Add the cache policy and minimal service**

```java
@Getter
@RequiredArgsConstructor
public enum CachePolicy {
    PLACE_DETAIL("place:detail", Duration.ofHours(3));
}
```

```java
@Service
@RequiredArgsConstructor
public class PlaceDetailService {

    private final GooglePlaceDetailClient googlePlaceDetailClient;

    @Cacheable(cacheNames = CachePolicy.Keys.PLACE_DETAIL)
    public PlaceDetailResult getDetail(String googlePlaceId) {
        return PlaceDetailResult.from(googlePlaceDetailClient.getDetail(googlePlaceId));
    }
}
```

```java
public static final class Keys {
    public static final String PLACE_DETAIL = "place:detail";
}
```

- [ ] **Step 4: Add the cache behavior test**

Use a Spring test slice with a simple in-memory cache manager so `@Cacheable` is exercised through the Spring proxy.

```java
@SpringJUnitConfig(classes = PlaceDetailCachingTest.Config.class)
class PlaceDetailCachingTest {

    @Autowired
    private PlaceDetailService placeDetailService;

    @Autowired
    private GooglePlaceDetailClient googlePlaceDetailClient;

    @Test
    void reusesCachedResultForSameGooglePlaceId() {
        placeDetailService.getDetail("ChIJ123");
        placeDetailService.getDetail("ChIJ123");

        then(googlePlaceDetailClient).should(times(1)).getDetail("ChIJ123");
    }
}
```

- [ ] **Step 5: Add a focused cache-fail-open test**

Do not try to integration-test Redis internals. Instead, verify the application keeps working when the cache layer throws by using a test `CacheManager` whose `Cache#get`/`put` throws.

Expected assertion:

```java
assertThat(placeDetailService.getDetail("ChIJ123").googlePlaceId()).isEqualTo("ChIJ123");
then(googlePlaceDetailClient).should().getDetail("ChIJ123");
```

- [ ] **Step 6: Run the service and cache tests**

Run:

```bash
./gradlew test --tests 'com.howaboutus.backend.places.service.PlaceDetailServiceTest' \
               --tests 'com.howaboutus.backend.places.service.PlaceDetailCachingTest'
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/howaboutus/backend/common/config/CachePolicy.java \
        src/main/java/com/howaboutus/backend/places/service/PlaceDetailService.java \
        src/test/java/com/howaboutus/backend/places/service/PlaceDetailServiceTest.java \
        src/test/java/com/howaboutus/backend/places/service/PlaceDetailCachingTest.java
git commit -m "feat: 장소 상세 조회 서비스와 캐시 추가"
```

### Task 4: Expose the endpoint and finish documentation

**Files:**
- Modify: `src/main/java/com/howaboutus/backend/places/controller/PlaceController.java`
- Create: `src/main/java/com/howaboutus/backend/places/controller/dto/PlaceDetailResponse.java`
- Modify: `src/test/java/com/howaboutus/backend/places/controller/PlaceControllerTest.java`
- Modify: `docs/ai/features.md`

- [ ] **Step 1: Write the failing controller test**

```java
@DisplayName("장소 상세 조회 결과를 반환한다")
@Test
void returnsPlaceDetailWhenGooglePlaceIdIsValid() throws Exception {
    given(placeDetailService.getDetail("ChIJ123"))
            .willReturn(placeDetailResult());

    mockMvc.perform(get("/places/{googlePlaceId}", "ChIJ123"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.googlePlaceId").value("ChIJ123"))
            .andExpect(jsonPath("$.phoneNumber").value("02-123-4567"))
            .andExpect(jsonPath("$.photoNames[0]").value("places/ChIJ123/photos/a"));
}
```

- [ ] **Step 2: Add error-path tests before implementation**

```java
@DisplayName("외부 API 오류 발생 시 502를 반환한다")
@Test
void returnsBadGatewayWhenDetailLookupFails() throws Exception {
    given(placeDetailService.getDetail("ChIJ123"))
            .willThrow(new ExternalApiException(new RuntimeException("timeout")));

    mockMvc.perform(get("/places/{googlePlaceId}", "ChIJ123"))
            .andExpect(status().isBadGateway())
            .andExpect(jsonPath("$.code").value("EXTERNAL_API_ERROR"));
}
```

- [ ] **Step 3: Run the controller test to verify it fails**

Run:

```bash
./gradlew test --tests 'com.howaboutus.backend.places.controller.PlaceControllerTest'
```

Expected:

```text
FAIL because the endpoint and DTO do not exist yet
```

- [ ] **Step 4: Add the endpoint and response DTO**

```java
@Operation(summary = "장소 상세 조회", description = "googlePlaceId를 기반으로 장소 상세 정보를 조회합니다.")
@GetMapping("/places/{googlePlaceId}")
public PlaceDetailResponse getDetail(@PathVariable @NotBlank String googlePlaceId) {
    return PlaceDetailResponse.from(placeDetailService.getDetail(googlePlaceId));
}
```

```java
public record PlaceDetailResponse(
        String googlePlaceId,
        String name,
        String formattedAddress,
        Location location,
        String primaryType,
        Double rating,
        String phoneNumber,
        String websiteUri,
        String googleMapsUri,
        List<String> weekdayDescriptions,
        List<String> photoNames
) {
    public static PlaceDetailResponse from(PlaceDetailResult result) {
        // map nested location and lists
    }
}
```

- [ ] **Step 5: Mark the feature doc as implemented**

Change:

```markdown
| `[ ]` | 장소 상세 조회 | ... | Redis |
```

To:

```markdown
| `[x]` | 장소 상세 조회 | ... | Redis |
```

- [ ] **Step 6: Run the endpoint tests and the focused places test suite**

Run:

```bash
./gradlew test --tests 'com.howaboutus.backend.places.controller.PlaceControllerTest' \
               --tests 'com.howaboutus.backend.places.service.PlaceDetailServiceTest' \
               --tests 'com.howaboutus.backend.places.service.PlaceDetailCachingTest' \
               --tests 'com.howaboutus.backend.common.integration.google.GooglePlaceDetailClientTest' \
               --tests 'com.howaboutus.backend.places.service.dto.PlaceDetailResultTest'
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 7: Run the broader regression check**

Run:

```bash
./gradlew test
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/howaboutus/backend/places/controller/PlaceController.java \
        src/main/java/com/howaboutus/backend/places/controller/dto/PlaceDetailResponse.java \
        src/test/java/com/howaboutus/backend/places/controller/PlaceControllerTest.java \
        docs/ai/features.md
git commit -m "feat: 장소 상세 조회 API 추가"
```

## Notes For Execution

- 기존 `GlobalExceptionHandler`는 `ConstraintViolationException`을 모두 `INVALID_PLACE_QUERY`로 매핑하고 있으므로, 상세 조회 path validation을 추가할 때는 기존 검색 오류 코드와 충돌하지 않게 분기 방식을 먼저 점검한다.
- `@Cacheable`은 Spring proxy를 통해서만 동작하므로, 단순 new 생성한 서비스 단위 테스트와 캐시 검증 테스트를 분리한다.
- Google Places 상세 응답의 `id`는 `"places/<placeId>"` 형태일 수 있으니, 서비스 외부로는 순수 `googlePlaceId`만 노출되게 정규화한다.
- `photoNames`, `weekdayDescriptions`는 `null`보다 빈 리스트가 프론트에서 다루기 쉬운지 구현 전에 한 번 결정하라. 스펙상 명시가 없다면 record 생성 시 빈 리스트로 정규화하는 쪽이 안전하다.
- 구현 완료 후 `docs/ai/features.md`의 상태 표시는 최신 상태와 맞춰 반드시 `[x]`로 갱신한다.
