# Place Photo API 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 클라이언트가 photoName을 서버에 전달하면 Google Photo Media API를 호출해 photoUrl을 반환하는 `GET /places/photos` 엔드포인트를 추가하고, `place:detail` 캐시 TTL을 5분으로 단축한다.

**Architecture:** 기존 검색/상세 조회 API는 `photoName`(Google 리소스 이름)을 그대로 반환한다. 클라이언트는 원하는 시점에 `GET /places/photos?name={photoName}`을 호출해 실제 CDN URL을 받는다. `place:detail` 캐시 TTL을 3h → 5분으로 단축해 캐시에 남은 photoName이 만료되는 문제를 제거한다.

**Tech Stack:** Spring Boot 4.0.5, Java 21, Spring Cache (Redis), Lombok, `MockRestServiceServer` (테스트)

**Worktree:** `.worktrees/feature-place` (브랜치: `feature/place`)
모든 파일 경로는 `.worktrees/feature-place/` 기준이며, 테스트/빌드 명령어는 해당 디렉토리에서 실행한다.

---

## 파일 맵

| 상태 | 경로 |
|------|------|
| 신규 | `src/main/java/com/howaboutus/backend/common/integration/google/dto/GooglePlacePhotoResponse.java` |
| 신규 | `src/main/java/com/howaboutus/backend/common/integration/google/GooglePlacePhotoClient.java` |
| 신규 | `src/main/java/com/howaboutus/backend/places/controller/dto/PlacePhotoResponse.java` |
| 신규 | `src/main/java/com/howaboutus/backend/places/service/PlacePhotoService.java` |
| 수정 | `src/main/java/com/howaboutus/backend/places/controller/PlaceController.java` |
| 수정 | `src/main/java/com/howaboutus/backend/common/config/CachePolicy.java` |
| 수정 | `docs/ai/features.md` |
| 신규 (테스트) | `src/test/java/com/howaboutus/backend/common/integration/google/GooglePlacePhotoClientTest.java` |
| 신규 (테스트) | `src/test/java/com/howaboutus/backend/places/service/PlacePhotoServiceTest.java` |
| 수정 (테스트) | `src/test/java/com/howaboutus/backend/places/controller/PlaceControllerTest.java` |

---

## Task 1: 이전 커밋 되돌리기 커밋

이전 커밋(`bc382da`)에서 서버사이드 photo URL 해결 방식으로 구현한 내용이 있다. 현재 워크트리에는 그 내용을 되돌린 미커밋 변경사항이 있다. 이를 커밋으로 정리한다.

**Files:**
- Stage all: `src/main/java/.../places/` 하위 변경 파일 전체 + `src/test/` 하위 변경 파일 전체
- Remove: `GooglePlacePhotoClient.java`, `GooglePlacePhotoResponse.java` (삭제 상태 stage)

- [ ] **Step 1: 현재 상태 확인**

```bash
git status
```

Expected: 수정/삭제된 파일 목록 표시

- [ ] **Step 2: 모든 변경사항 스테이지**

```bash
git add \
  src/main/java/com/howaboutus/backend/places/controller/dto/PlaceDetailResponse.java \
  src/main/java/com/howaboutus/backend/places/controller/dto/PlaceSearchResponse.java \
  src/main/java/com/howaboutus/backend/places/service/PlaceDetailService.java \
  src/main/java/com/howaboutus/backend/places/service/PlaceSearchService.java \
  src/main/java/com/howaboutus/backend/places/service/dto/PlaceDetailResult.java \
  src/main/java/com/howaboutus/backend/places/service/dto/PlaceSearchResult.java \
  src/test/java/com/howaboutus/backend/places/controller/PlaceControllerTest.java \
  src/test/java/com/howaboutus/backend/places/service/PlaceDetailCachingTest.java \
  src/test/java/com/howaboutus/backend/places/service/PlaceDetailServiceTest.java \
  src/test/java/com/howaboutus/backend/places/service/PlaceSearchServiceTest.java \
  src/test/java/com/howaboutus/backend/places/service/dto/PlaceDetailResultTest.java \
  src/test/java/com/howaboutus/backend/places/service/dto/PlaceSearchResultTest.java
git rm src/main/java/com/howaboutus/backend/common/integration/google/GooglePlacePhotoClient.java \
       src/main/java/com/howaboutus/backend/common/integration/google/dto/GooglePlacePhotoResponse.java
```

- [ ] **Step 3: 커밋**

```bash
git commit -m "revert: 서버사이드 photo URL 해결 방식 제거 — 클라이언트 위임 방식으로 전환"
```

- [ ] **Step 4: 테스트 통과 확인**

```bash
./gradlew test
```

Expected: `BUILD SUCCESSFUL`

---

## Task 2: GooglePlacePhotoClient (TDD)

Google Photo Media API(`/v1/{photoName}/media`)를 호출해 `photoUri`를 반환하는 클라이언트.

**Files:**
- Create: `src/test/java/com/howaboutus/backend/common/integration/google/GooglePlacePhotoClientTest.java`
- Create: `src/main/java/com/howaboutus/backend/common/integration/google/dto/GooglePlacePhotoResponse.java`
- Create: `src/main/java/com/howaboutus/backend/common/integration/google/GooglePlacePhotoClient.java`

- [ ] **Step 1: 실패하는 테스트 작성**

`src/test/java/com/howaboutus/backend/common/integration/google/GooglePlacePhotoClientTest.java`

```java
package com.howaboutus.backend.common.integration.google;

import com.howaboutus.backend.common.config.properties.GooglePlacesProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.RequestMatcher;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GooglePlacePhotoClientTest {

    private MockRestServiceServer server;
    private GooglePlacePhotoClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://places.googleapis.com");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new GooglePlacePhotoClient(
                builder.build(),
                new GooglePlacesProperties(
                        "test-key",
                        "https://places.googleapis.com/",
                        "places.id,places.displayName",
                        "id,displayName"
                )
        );
    }

    @Test
    @DisplayName("Photo Media API로 photoName에 해당하는 photoUri를 반환한다")
    void returnsPhotoUriForGivenPhotoName() {
        String photoName = "places/ChIJ123/photos/abc";
        server.expect(requestTo(
                        "https://places.googleapis.com/v1/" + photoName
                                + "/media?maxWidthPx=400&maxHeightPx=400&skipHttpRedirect=true"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Goog-Api-Key", "test-key"))
                .andExpect(hasEmptyRequestBody())
                .andRespond(withSuccess("""
                        {
                          "name": "places/ChIJ123/photos/abc",
                          "photoUri": "https://lh3.googleusercontent.com/photo.jpg"
                        }
                        """, MediaType.APPLICATION_JSON));

        String result = client.getPhotoUri(photoName);

        assertThat(result).isEqualTo("https://lh3.googleusercontent.com/photo.jpg");
        server.verify();
    }

    private RequestMatcher hasEmptyRequestBody() {
        return request -> assertThat(((MockClientHttpRequest) request).getBodyAsString()).isEmpty();
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

```bash
./gradlew test --tests "*.GooglePlacePhotoClientTest"
```

Expected: FAIL — `GooglePlacePhotoClient` 클래스 없음

- [ ] **Step 3: GooglePlacePhotoResponse DTO 작성**

`src/main/java/com/howaboutus/backend/common/integration/google/dto/GooglePlacePhotoResponse.java`

```java
package com.howaboutus.backend.common.integration.google.dto;

public record GooglePlacePhotoResponse(String name, String photoUri) {
}
```

- [ ] **Step 4: GooglePlacePhotoClient 작성**

`src/main/java/com/howaboutus/backend/common/integration/google/GooglePlacePhotoClient.java`

```java
package com.howaboutus.backend.common.integration.google;

import com.howaboutus.backend.common.config.properties.GooglePlacesProperties;
import com.howaboutus.backend.common.error.ExternalApiException;
import com.howaboutus.backend.common.integration.google.dto.GooglePlacePhotoResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@RequiredArgsConstructor
public class GooglePlacePhotoClient {

    private static final int MAX_WIDTH_PX = 400;
    private static final int MAX_HEIGHT_PX = 400;

    private final RestClient googlePlacesRestClient;
    private final GooglePlacesProperties properties;

    public String getPhotoUri(String photoName) {
        String uri = String.format("/v1/%s/media?maxWidthPx=%d&maxHeightPx=%d&skipHttpRedirect=true",
                photoName, MAX_WIDTH_PX, MAX_HEIGHT_PX);
        try {
            GooglePlacePhotoResponse response = googlePlacesRestClient.get()
                    .uri(uri)
                    .header("X-Goog-Api-Key", properties.apiKey())
                    .retrieve()
                    .body(GooglePlacePhotoResponse.class);
            return response != null ? response.photoUri() : null;
        } catch (RestClientException exception) {
            throw new ExternalApiException(exception);
        }
    }
}
```

- [ ] **Step 5: 테스트 통과 확인**

```bash
./gradlew test --tests "*.GooglePlacePhotoClientTest"
```

Expected: PASS

- [ ] **Step 6: 커밋**

```bash
git add \
  src/main/java/com/howaboutus/backend/common/integration/google/dto/GooglePlacePhotoResponse.java \
  src/main/java/com/howaboutus/backend/common/integration/google/GooglePlacePhotoClient.java \
  src/test/java/com/howaboutus/backend/common/integration/google/GooglePlacePhotoClientTest.java
git commit -m "feat: GooglePlacePhotoClient 추가 — Photo Media API photoUri 조회"
```

---

## Task 3: PlacePhotoService (TDD)

`GooglePlacePhotoClient`를 서비스 레이어로 감싼다.

**Files:**
- Create: `src/test/java/com/howaboutus/backend/places/service/PlacePhotoServiceTest.java`
- Create: `src/main/java/com/howaboutus/backend/places/service/PlacePhotoService.java`
- Create: `src/main/java/com/howaboutus/backend/places/controller/dto/PlacePhotoResponse.java`

- [ ] **Step 1: 실패하는 테스트 작성**

`src/test/java/com/howaboutus/backend/places/service/PlacePhotoServiceTest.java`

```java
package com.howaboutus.backend.places.service;

import com.howaboutus.backend.common.integration.google.GooglePlacePhotoClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

class PlacePhotoServiceTest {

    private final GooglePlacePhotoClient googlePlacePhotoClient = mock(GooglePlacePhotoClient.class);
    private final PlacePhotoService placePhotoService = new PlacePhotoService(googlePlacePhotoClient);

    @Test
    @DisplayName("photoName을 클라이언트에 위임해 photoUrl을 반환한다")
    void delegatesPhotoUriResolutionToClient() {
        given(googlePlacePhotoClient.getPhotoUri("places/ChIJ123/photos/abc"))
                .willReturn("https://lh3.googleusercontent.com/photo.jpg");

        String result = placePhotoService.getPhotoUrl("places/ChIJ123/photos/abc");

        assertThat(result).isEqualTo("https://lh3.googleusercontent.com/photo.jpg");
        then(googlePlacePhotoClient).should().getPhotoUri("places/ChIJ123/photos/abc");
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

```bash
./gradlew test --tests "*.PlacePhotoServiceTest"
```

Expected: FAIL — `PlacePhotoService` 클래스 없음

- [ ] **Step 3: PlacePhotoResponse DTO 작성**

`src/main/java/com/howaboutus/backend/places/controller/dto/PlacePhotoResponse.java`

```java
package com.howaboutus.backend.places.controller.dto;

public record PlacePhotoResponse(String photoUrl) {
}
```

- [ ] **Step 4: PlacePhotoService 작성**

`src/main/java/com/howaboutus/backend/places/service/PlacePhotoService.java`

```java
package com.howaboutus.backend.places.service;

import com.howaboutus.backend.common.integration.google.GooglePlacePhotoClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PlacePhotoService {

    private final GooglePlacePhotoClient googlePlacePhotoClient;

    public String getPhotoUrl(String photoName) {
        return googlePlacePhotoClient.getPhotoUri(photoName);
    }
}
```

- [ ] **Step 5: 테스트 통과 확인**

```bash
./gradlew test --tests "*.PlacePhotoServiceTest"
```

Expected: PASS

- [ ] **Step 6: 커밋**

```bash
git add \
  src/main/java/com/howaboutus/backend/places/controller/dto/PlacePhotoResponse.java \
  src/main/java/com/howaboutus/backend/places/service/PlacePhotoService.java \
  src/test/java/com/howaboutus/backend/places/service/PlacePhotoServiceTest.java
git commit -m "feat: PlacePhotoService 추가 — photoName → photoUrl 변환 서비스"
```

---

## Task 4: GET /places/photos 엔드포인트 (TDD)

`PlaceController`에 사진 URL 조회 엔드포인트를 추가한다.

**Files:**
- Modify: `src/test/java/com/howaboutus/backend/places/controller/PlaceControllerTest.java`
- Modify: `src/main/java/com/howaboutus/backend/places/controller/PlaceController.java`

- [ ] **Step 1: PlaceControllerTest에 실패하는 테스트 추가**

`PlaceControllerTest.java`의 클래스 상단 `@MockitoBean` 필드에 추가:

```java
@MockitoBean
private PlacePhotoService placePhotoService;
```

import 추가:
```java
import com.howaboutus.backend.places.service.PlacePhotoService;
```

클래스 내부에 테스트 메서드 추가:

```java
@Test
@DisplayName("유효한 name으로 요청하면 photoUrl을 반환한다")
void returnsPhotoUrlForValidName() throws Exception {
    given(placePhotoService.getPhotoUrl("places/ChIJ123/photos/abc"))
            .willReturn("https://lh3.googleusercontent.com/photo.jpg");

    mockMvc.perform(get("/places/photos")
                    .param("name", "places/ChIJ123/photos/abc"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.photoUrl").value("https://lh3.googleusercontent.com/photo.jpg"));

    then(placePhotoService).should().getPhotoUrl("places/ChIJ123/photos/abc");
}

@Test
@DisplayName("빈 name으로 요청하면 400을 반환한다")
void returnsBadRequestWhenNameIsBlank() throws Exception {
    mockMvc.perform(get("/places/photos")
                    .param("name", "   "))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
            .andExpect(jsonPath("$.message").value("name은 공백일 수 없습니다"));

    verifyNoInteractions(placePhotoService);
}

@Test
@DisplayName("name 파라미터가 없으면 400을 반환한다")
void returnsBadRequestWhenNameParameterIsMissing() throws Exception {
    mockMvc.perform(get("/places/photos"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
            .andExpect(jsonPath("$.message").value("필수 요청 파라미터가 누락되었습니다: name"));

    verifyNoInteractions(placePhotoService);
}

@Test
@DisplayName("사진 URL 조회 중 외부 API 오류 발생 시 502를 반환한다")
void returnsBadGatewayWhenPhotoUrlLookupFails() throws Exception {
    given(placePhotoService.getPhotoUrl("places/ChIJ123/photos/abc"))
            .willThrow(new ExternalApiException(new RuntimeException("connection timeout")));

    mockMvc.perform(get("/places/photos")
                    .param("name", "places/ChIJ123/photos/abc"))
            .andExpect(status().isBadGateway())
            .andExpect(jsonPath("$.code").value("EXTERNAL_API_ERROR"))
            .andExpect(jsonPath("$.message").value("외부 API 호출 중 오류가 발생했습니다"));
}
```

- [ ] **Step 2: 테스트 실패 확인**

```bash
./gradlew test --tests "*.PlaceControllerTest"
```

Expected: FAIL — `PlacePhotoService` 빈 없음 또는 `/places/photos` 엔드포인트 없음

- [ ] **Step 3: PlaceController에 엔드포인트 추가**

기존 import 목록에 추가:
```java
import com.howaboutus.backend.places.controller.dto.PlacePhotoResponse;
import com.howaboutus.backend.places.service.PlacePhotoService;
```

클래스 필드에 추가:
```java
private final PlacePhotoService placePhotoService;
```

`getDetail()` 메서드 아래에 추가:
```java
@Operation(
        summary = "장소 사진 URL 조회",
        description = "photoName을 기반으로 Google 장소 사진 URL을 조회합니다."
)
@GetMapping("/places/photos")
public PlacePhotoResponse getPhotoUrl(
        @Parameter(description = "Google 장소 사진 리소스 이름", example = "places/ChIJ123/photos/abc")
        @RequestParam
        @NotBlank(message = "name은 공백일 수 없습니다")
        String name) {
    return new PlacePhotoResponse(placePhotoService.getPhotoUrl(name));
}
```

- [ ] **Step 4: 테스트 통과 확인**

```bash
./gradlew test --tests "*.PlaceControllerTest"
```

Expected: PASS

- [ ] **Step 5: 전체 테스트 통과 확인**

```bash
./gradlew test
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: 커밋**

```bash
git add \
  src/main/java/com/howaboutus/backend/places/controller/PlaceController.java \
  src/test/java/com/howaboutus/backend/places/controller/PlaceControllerTest.java
git commit -m "feat: GET /places/photos 엔드포인트 추가 — photoName → photoUrl 변환"
```

---

## Task 5: CachePolicy TTL 변경 + features.md 업데이트

`place:detail` 캐시 TTL을 3h → 5분으로 단축하고 문서를 갱신한다.

**Files:**
- Modify: `src/main/java/com/howaboutus/backend/common/config/CachePolicy.java`
- Modify: `docs/ai/features.md`

- [ ] **Step 1: CachePolicy TTL 변경**

`CachePolicy.java`의 `PLACE_DETAIL` 항목을 수정한다:

```java
PLACE_DETAIL(Keys.PLACE_DETAIL, Duration.ofMinutes(5));
```

- [ ] **Step 2: 전체 테스트 통과 확인**

```bash
./gradlew test
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: features.md 장소 섹션 업데이트**

`docs/ai/features.md`의 섹션 4 상단 노트와 장소 상세 조회 행을 수정한다.

상단 노트 (`> \`places\` 테이블 없이...`로 시작하는 줄):
```markdown
> `places` 테이블 없이 `google_place_id`를 직접 사용한다. 검색은 캐시하지 않고, 장소 상세 조회 payload는 Redis에 5분 TTL로 캐시한다.
```

장소 상세 조회 행:
```markdown
| `[x]` | 장소 상세 조회 | 장소명, 주소, 평점, 전화번호, 웹사이트, 영업시간, 사진 목록(`photoNames`) 등, 상세 조회 결과는 Redis에 5분 TTL 캐시 | Redis |
```

장소 사진 URL 조회 행 추가 (장소 상세 조회 다음 행):
```markdown
| `[x]` | 장소 사진 URL 조회 | `photoName`을 받아 Google Photo Media API를 호출, `photoUrl` 반환. 캐시 없음 | - |
```

- [ ] **Step 4: 커밋**

```bash
git add \
  src/main/java/com/howaboutus/backend/common/config/CachePolicy.java \
  docs/ai/features.md
git commit -m "feat: place:detail 캐시 TTL 5분으로 단축 및 features.md 갱신"
```
