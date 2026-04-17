# Place Search Location Bias Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `GET /places/search`에 선택적 위치 파라미터(`latitude`, `longitude`, `radius`)를 추가해 Google Places API `locationBias.circle`을 통한 위치 편향 검색을 지원한다.

**Architecture:** Controller → Service → GooglePlaceSearchClient 3개 레이어 시그니처를 하위부터 순서대로 변경한다. `GoogleTextSearchRequest` DTO에 `LocationBias` 중첩 레코드를 추가하고, `latitude`가 `null`이면 기존처럼 `locationBias` 없이 Google API를 호출한다. `latitude`와 `longitude`는 항상 쌍으로 제공해야 하며, 하나만 있으면 `400 Bad Request`를 반환한다. `radius`는 선택이며 기본값은 `5000.0`m이다.

**Tech Stack:** Java 21, Spring Boot 4.x, Spring Web (RestClient), Jackson (`com.fasterxml.jackson.annotation`), JUnit 5, MockRestServiceServer, MockMvc

---

### Task 1: GoogleTextSearchRequest DTO 확장

**Files:**
- Modify: `src/main/java/com/howaboutus/backend/common/integration/google/dto/GoogleTextSearchRequest.java`
- Create: `src/test/java/com/howaboutus/backend/common/integration/google/dto/GoogleTextSearchRequestTest.java`

- [ ] **Step 1: 실패하는 테스트 작성**

아래 내용으로 `GoogleTextSearchRequestTest.java`를 생성한다.

```java
package com.howaboutus.backend.common.integration.google.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class GoogleTextSearchRequestTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("withKorean으로 생성하면 locationBias가 JSON에서 제외된다")
    void withKorean_excludesLocationBiasFromJson() throws Exception {
        GoogleTextSearchRequest request = GoogleTextSearchRequest.withKorean("일본 맛집");

        String json = objectMapper.writeValueAsString(request);

        assertThat(json).doesNotContain("locationBias");
    }

    @Test
    @DisplayName("withKoreanAndLocation으로 생성하면 locationBias.circle이 JSON에 포함된다")
    void withKoreanAndLocation_includesLocationBiasInJson() throws Exception {
        GoogleTextSearchRequest request = GoogleTextSearchRequest.withKoreanAndLocation("일본 맛집", 37.5, 127.0, 3000.0);

        String json = objectMapper.writeValueAsString(request);

        assertThat(json).contains("\"locationBias\"");
        assertThat(json).contains("\"circle\"");
        assertThat(json).contains("37.5");
        assertThat(json).contains("127.0");
        assertThat(json).contains("3000.0");
    }
}
```

- [ ] **Step 2: 테스트 실행 → 실패 확인**

```bash
./gradlew test --tests "com.howaboutus.backend.common.integration.google.dto.GoogleTextSearchRequestTest"
```

Expected: `withKoreanAndLocation` 메서드가 없으므로 컴파일 오류.

- [ ] **Step 3: 구현**

`GoogleTextSearchRequest.java` 전체를 아래로 교체한다.

```java
package com.howaboutus.backend.common.integration.google.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record GoogleTextSearchRequest(String textQuery, String languageCode, LocationBias locationBias) {

    public static GoogleTextSearchRequest withKorean(String textQuery) {
        return new GoogleTextSearchRequest(textQuery, "ko", null);
    }

    public static GoogleTextSearchRequest withKoreanAndLocation(
            String textQuery, double latitude, double longitude, double radius) {
        return new GoogleTextSearchRequest(textQuery, "ko",
                new LocationBias(new Circle(new LatLng(latitude, longitude), radius)));
    }

    public record LocationBias(Circle circle) {}

    public record Circle(LatLng center, Double radius) {}

    public record LatLng(Double latitude, Double longitude) {}
}
```

- [ ] **Step 4: 테스트 실행 → 통과 확인**

```bash
./gradlew test --tests "com.howaboutus.backend.common.integration.google.dto.GoogleTextSearchRequestTest"
```

Expected: 2개 테스트 PASS.

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/howaboutus/backend/common/integration/google/dto/GoogleTextSearchRequest.java \
        src/test/java/com/howaboutus/backend/common/integration/google/dto/GoogleTextSearchRequestTest.java
git commit -m "feat: GoogleTextSearchRequest에 locationBias.circle 지원 추가"
```

---

### Task 2: GooglePlaceSearchClient 시그니처 변경

**Files:**
- Modify: `src/main/java/com/howaboutus/backend/common/integration/google/GooglePlaceSearchClient.java`
- Modify: `src/test/java/com/howaboutus/backend/common/integration/google/GooglePlaceSearchClientTest.java`

- [ ] **Step 1: 테스트 수정 및 신규 테스트 작성**

`GooglePlaceSearchClientTest.java` 전체를 아래로 교체한다.

```java
package com.howaboutus.backend.common.integration.google;

import com.howaboutus.backend.common.config.properties.GooglePlacesProperties;
import com.howaboutus.backend.common.integration.google.dto.GoogleTextSearchResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GooglePlaceSearchClientTest {

    private MockRestServiceServer server;
    private GooglePlaceSearchClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://places.googleapis.com");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new GooglePlaceSearchClient(
                builder.build(),
                new GooglePlacesProperties(
                        "test-key",
                        "https://places.googleapis.com/",
                        "places.id,places.displayName,places.formattedAddress,places.location,places.primaryType,places.rating,places.photos",
                        "id,displayName,formattedAddress,location,primaryType,rating,nationalPhoneNumber,websiteUri,googleMapsUri,regularOpeningHours.weekdayDescriptions,photos.name"
                )
        );
    }

    @Test
    @DisplayName("Google Places 텍스트 검색 엔드포인트로 올바른 헤더와 함께 요청을 전송한다")
    void searchesPlacesUsingTextSearchEndpoint() {
        server.expect(requestTo("https://places.googleapis.com/v1/places:searchText"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Goog-Api-Key", "test-key"))
                .andExpect(header(
                        "X-Goog-FieldMask",
                        "places.id,places.displayName,places.formattedAddress,places.location,places.primaryType,places.rating,places.photos"
                ))
                .andRespond(withSuccess("""
                        {
                          "places": [
                            {
                              "id": "ChIJ123",
                              "displayName": {"text": "Cafe Layered", "languageCode": "ko"},
                              "formattedAddress": "서울 종로구 ...",
                              "location": {"latitude": 37.57, "longitude": 126.98},
                              "primaryType": "cafe",
                              "rating": 4.5,
                              "photos": [{"name": "places/ChIJ123/photos/abc"}]
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        List<GoogleTextSearchResponse.PlaceItem> result = client.search("seoul cafe", null, null, null);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().id()).isEqualTo("ChIJ123");
        server.verify();
    }

    @Test
    @DisplayName("위치 정보를 넘기면 locationBias.circle이 포함된 요청 본문을 전송한다")
    void searchesWithLocationBiasWhenCoordinatesProvided() {
        server.expect(requestTo("https://places.googleapis.com/v1/places:searchText"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"locationBias\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("37.5")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("127.0")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("3000.0")))
                .andRespond(withSuccess("{\"places\": []}", MediaType.APPLICATION_JSON));

        List<GoogleTextSearchResponse.PlaceItem> result = client.search("seoul cafe", 37.5, 127.0, 3000.0);

        assertThat(result).isEmpty();
        server.verify();
    }

    @Test
    @DisplayName("위치 정보가 없으면 locationBias가 포함되지 않은 요청 본문을 전송한다")
    void searchesWithoutLocationBiasWhenCoordinatesAreNull() {
        server.expect(requestTo("https://places.googleapis.com/v1/places:searchText"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("locationBias"))))
                .andRespond(withSuccess("{\"places\": []}", MediaType.APPLICATION_JSON));

        List<GoogleTextSearchResponse.PlaceItem> result = client.search("seoul cafe", null, null, null);

        assertThat(result).isEmpty();
        server.verify();
    }
}
```

- [ ] **Step 2: 테스트 실행 → 실패 확인**

```bash
./gradlew test --tests "com.howaboutus.backend.common.integration.google.GooglePlaceSearchClientTest"
```

Expected: `search(String, Double, Double, Double)` 메서드가 없으므로 컴파일 오류.

- [ ] **Step 3: 구현**

`GooglePlaceSearchClient.java` 전체를 아래로 교체한다.

```java
package com.howaboutus.backend.common.integration.google;

import com.howaboutus.backend.common.config.properties.GooglePlacesProperties;
import com.howaboutus.backend.common.error.ExternalApiException;
import com.howaboutus.backend.common.integration.google.dto.GoogleTextSearchRequest;
import com.howaboutus.backend.common.integration.google.dto.GoogleTextSearchResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

@Component
@RequiredArgsConstructor
public class GooglePlaceSearchClient {

    private final RestClient googlePlacesRestClient;
    private final GooglePlacesProperties properties;

    public List<GoogleTextSearchResponse.PlaceItem> search(
            String query, Double latitude, Double longitude, Double radius) {
        GoogleTextSearchRequest request = (latitude != null)
                ? GoogleTextSearchRequest.withKoreanAndLocation(query, latitude, longitude, radius)
                : GoogleTextSearchRequest.withKorean(query);
        try {
            GoogleTextSearchResponse response = googlePlacesRestClient.post()
                    .uri("/v1/places:searchText")
                    .header("X-Goog-Api-Key", properties.apiKey())
                    .header("X-Goog-FieldMask", properties.searchFieldMask())
                    .body(request)
                    .retrieve()
                    .body(GoogleTextSearchResponse.class);

            if (response == null || response.places() == null) {
                return List.of();
            }
            return response.places();
        } catch (RestClientException exception) {
            throw new ExternalApiException(exception);
        }
    }
}
```

- [ ] **Step 4: 테스트 실행 → 통과 확인**

```bash
./gradlew test --tests "com.howaboutus.backend.common.integration.google.GooglePlaceSearchClientTest"
```

Expected: 3개 테스트 PASS.

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/howaboutus/backend/common/integration/google/GooglePlaceSearchClient.java \
        src/test/java/com/howaboutus/backend/common/integration/google/GooglePlaceSearchClientTest.java
git commit -m "feat: GooglePlaceSearchClient에 위치 파라미터 추가"
```

---

### Task 3: PlaceSearchService 시그니처 변경

**Files:**
- Modify: `src/main/java/com/howaboutus/backend/places/service/PlaceSearchService.java`
- Modify: `src/test/java/com/howaboutus/backend/places/service/PlaceSearchServiceTest.java`

- [ ] **Step 1: 테스트 수정 및 신규 테스트 작성**

`PlaceSearchServiceTest.java` 전체를 아래로 교체한다.

```java
package com.howaboutus.backend.places.service;

import com.howaboutus.backend.common.integration.google.GooglePlaceSearchClient;
import com.howaboutus.backend.common.integration.google.dto.GoogleTextSearchResponse;
import com.howaboutus.backend.places.service.dto.PlaceSearchResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

class PlaceSearchServiceTest {

    private final GooglePlaceSearchClient googlePlaceSearchClient = mock(GooglePlaceSearchClient.class);
    private final PlaceSearchService placeSearchService = new PlaceSearchService(googlePlaceSearchClient);

    @Test
    @DisplayName("Google 장소 응답을 검색 결과 목록으로 변환한다")
    void returnsMappedSearchResults() {
        GoogleTextSearchResponse.PlaceItem placeItem = new GoogleTextSearchResponse.PlaceItem(
                "ChIJ123",
                new GoogleTextSearchResponse.DisplayName("Cafe Layered", "ko"),
                "서울 종로구 ...",
                new GoogleTextSearchResponse.Location(37.57, 126.98),
                "cafe",
                4.5,
                List.of(new GoogleTextSearchResponse.Photo("places/ChIJ123/photos/abc"))
        );
        given(googlePlaceSearchClient.search("seoul cafe", null, null, null))
                .willReturn(List.of(placeItem));

        List<PlaceSearchResult> results = placeSearchService.search("seoul cafe", null, null, null);

        assertThat(results).containsExactly(PlaceSearchResult.from(placeItem));
        then(googlePlaceSearchClient).should().search("seoul cafe", null, null, null);
    }

    @Test
    @DisplayName("Google 장소 응답이 비어 있으면 빈 목록을 반환한다")
    void returnsEmptyResultsWhenClientReturnsEmptyList() {
        given(googlePlaceSearchClient.search("seoul cafe", null, null, null))
                .willReturn(List.of());

        List<PlaceSearchResult> results = placeSearchService.search("seoul cafe", null, null, null);

        assertThat(results).isEmpty();
        then(googlePlaceSearchClient).should().search("seoul cafe", null, null, null);
    }

    @Test
    @DisplayName("좌표가 있으면 client에 좌표와 반경을 그대로 전달한다")
    void forwardsLocationToClient() {
        given(googlePlaceSearchClient.search("seoul cafe", 37.5, 127.0, 3000.0))
                .willReturn(List.of());

        placeSearchService.search("seoul cafe", 37.5, 127.0, 3000.0);

        then(googlePlaceSearchClient).should().search("seoul cafe", 37.5, 127.0, 3000.0);
    }
}
```

- [ ] **Step 2: 테스트 실행 → 실패 확인**

```bash
./gradlew test --tests "com.howaboutus.backend.places.service.PlaceSearchServiceTest"
```

Expected: `search(String, Double, Double, Double)` 메서드가 없으므로 컴파일 오류.

- [ ] **Step 3: 구현**

`PlaceSearchService.java` 전체를 아래로 교체한다.

```java
package com.howaboutus.backend.places.service;

import com.howaboutus.backend.common.integration.google.GooglePlaceSearchClient;
import com.howaboutus.backend.places.service.dto.PlaceSearchResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PlaceSearchService {

    private final GooglePlaceSearchClient googlePlaceSearchClient;

    public List<PlaceSearchResult> search(String query, Double latitude, Double longitude, Double radius) {
        return googlePlaceSearchClient.search(query, latitude, longitude, radius)
                .stream()
                .map(PlaceSearchResult::from)
                .toList();
    }
}
```

- [ ] **Step 4: 테스트 실행 → 통과 확인**

```bash
./gradlew test --tests "com.howaboutus.backend.places.service.PlaceSearchServiceTest"
```

Expected: 3개 테스트 PASS.

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/howaboutus/backend/places/service/PlaceSearchService.java \
        src/test/java/com/howaboutus/backend/places/service/PlaceSearchServiceTest.java
git commit -m "feat: PlaceSearchService에 위치 파라미터 추가"
```

---

### Task 4: PlaceController 파라미터 확장 및 검증

**Files:**
- Modify: `src/main/java/com/howaboutus/backend/places/controller/PlaceController.java`
- Modify: `src/test/java/com/howaboutus/backend/places/controller/PlaceControllerTest.java`

- [ ] **Step 1: 테스트 수정 및 신규 테스트 작성**

`PlaceControllerTest.java` 전체를 아래로 교체한다.

```java
package com.howaboutus.backend.places.controller;

import com.howaboutus.backend.common.config.SecurityConfig;
import com.howaboutus.backend.common.error.ExternalApiException;
import com.howaboutus.backend.common.error.GlobalExceptionHandler;
import com.howaboutus.backend.places.service.PlaceDetailService;
import com.howaboutus.backend.places.service.PlaceSearchService;
import com.howaboutus.backend.places.service.dto.PlaceDetailResult;
import com.howaboutus.backend.places.service.dto.PlaceSearchResult;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PlaceController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class PlaceControllerTest {

    private static final String SEARCH_PATH = "/places/search";
    private static final String VALID_QUERY = "seoul cafe";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PlaceSearchService placeSearchService;

    @MockitoBean
    private PlaceDetailService placeDetailService;

    private PlaceSearchResult placeSearchResult;
    private PlaceDetailResult placeDetailResult;

    @BeforeEach
    void setUp() {
        placeSearchResult = new PlaceSearchResult(
                "ChIJ123",
                "Cafe Layered",
                "서울 종로구 ...",
                new PlaceSearchResult.Location(37.57, 126.98),
                "cafe",
                4.5,
                "places/ChIJ123/photos/abc"
        );
        placeDetailResult = new PlaceDetailResult(
                "ChIJ123",
                "Cafe Layered",
                "서울 종로구 ...",
                new PlaceDetailResult.Location(37.57, 126.98),
                "cafe",
                4.5,
                "02-123-4567",
                "https://layered.example",
                "https://maps.google.com/?cid=123",
                List.of("월요일: 09:00~18:00"),
                List.of("places/ChIJ123/photos/a", "places/ChIJ123/photos/b")
        );
    }

    @Test
    @DisplayName("빈 query로 검색하면 400을 반환한다")
    void returnsBadRequestWhenQueryIsBlank() throws Exception {
        mockMvc.perform(searchRequest("   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PLACE_QUERY"))
                .andExpect(jsonPath("$.message").value("검색어는 공백일 수 없습니다"));

        verifyNoInteractions(placeSearchService);
    }

    @Test
    @DisplayName("query 파라미터가 없으면 400을 반환한다")
    void returnsBadRequestWhenQueryParameterIsMissing() throws Exception {
        mockMvc.perform(get(SEARCH_PATH))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("필수 요청 파라미터가 누락되었습니다: query"));
    }

    @Test
    @DisplayName("유효한 query만으로 검색하면 위치 없이 서비스를 호출한다")
    void returnsSearchResultsWhenQueryIsValid() throws Exception {
        given(placeSearchService.search(VALID_QUERY, null, null, 5000.0))
                .willReturn(List.of(placeSearchResult));

        mockMvc.perform(searchRequest(VALID_QUERY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].googlePlaceId").value("ChIJ123"))
                .andExpect(jsonPath("$[0].name").value("Cafe Layered"));

        then(placeSearchService).should().search(VALID_QUERY, null, null, 5000.0);
    }

    @Test
    @DisplayName("latitude와 longitude, radius를 모두 제공하면 해당 위치와 반경으로 서비스를 호출한다")
    void callsServiceWithLocationWhenAllLocationParamsProvided() throws Exception {
        given(placeSearchService.search(VALID_QUERY, 37.5, 127.0, 3000.0))
                .willReturn(List.of(placeSearchResult));

        mockMvc.perform(get(SEARCH_PATH)
                        .param("query", VALID_QUERY)
                        .param("latitude", "37.5")
                        .param("longitude", "127.0")
                        .param("radius", "3000.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].googlePlaceId").value("ChIJ123"));

        then(placeSearchService).should().search(VALID_QUERY, 37.5, 127.0, 3000.0);
    }

    @Test
    @DisplayName("latitude와 longitude만 제공하면 기본 반경 5000m로 서비스를 호출한다")
    void callsServiceWithDefaultRadiusWhenRadiusNotProvided() throws Exception {
        given(placeSearchService.search(VALID_QUERY, 37.5, 127.0, 5000.0))
                .willReturn(List.of(placeSearchResult));

        mockMvc.perform(get(SEARCH_PATH)
                        .param("query", VALID_QUERY)
                        .param("latitude", "37.5")
                        .param("longitude", "127.0"))
                .andExpect(status().isOk());

        then(placeSearchService).should().search(VALID_QUERY, 37.5, 127.0, 5000.0);
    }

    @Test
    @DisplayName("latitude만 있고 longitude가 없으면 400을 반환한다")
    void returnsBadRequestWhenOnlyLatitudeProvided() throws Exception {
        mockMvc.perform(get(SEARCH_PATH)
                        .param("query", VALID_QUERY)
                        .param("latitude", "37.5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("latitude와 longitude는 함께 제공해야 합니다"));

        verifyNoInteractions(placeSearchService);
    }

    @Test
    @DisplayName("longitude만 있고 latitude가 없으면 400을 반환한다")
    void returnsBadRequestWhenOnlyLongitudeProvided() throws Exception {
        mockMvc.perform(get(SEARCH_PATH)
                        .param("query", VALID_QUERY)
                        .param("longitude", "127.0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("latitude와 longitude는 함께 제공해야 합니다"));

        verifyNoInteractions(placeSearchService);
    }

    @Test
    @DisplayName("검색 결과에 좌표가 없으면 location을 null로 반환한다")
    void returnsNullLocationWhenSearchResultDoesNotContainCoordinates() throws Exception {
        PlaceSearchResult resultWithoutLocation = new PlaceSearchResult(
                "ChIJ123",
                "Cafe Layered",
                "서울 종로구 ...",
                null,
                "cafe",
                4.5,
                "places/ChIJ123/photos/abc"
        );
        given(placeSearchService.search(VALID_QUERY, null, null, 5000.0))
                .willReturn(List.of(resultWithoutLocation));

        mockMvc.perform(searchRequest(VALID_QUERY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].location").value(Matchers.nullValue()));

        then(placeSearchService).should().search(VALID_QUERY, null, null, 5000.0);
    }

    @Test
    @DisplayName("외부 API 오류 발생 시 502를 반환한다")
    void returnsBadGatewayWhenExternalApiErrorOccurs() throws Exception {
        given(placeSearchService.search(VALID_QUERY, null, null, 5000.0))
                .willThrow(new ExternalApiException(new RuntimeException("connection timeout")));

        mockMvc.perform(searchRequest(VALID_QUERY))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("EXTERNAL_API_ERROR"))
                .andExpect(jsonPath("$.message").value("외부 API 호출 중 오류가 발생했습니다"));
    }

    @Test
    @DisplayName("처리되지 않은 예외 발생 시 500을 반환한다")
    void returnsInternalServerErrorForUnhandledException() throws Exception {
        given(placeSearchService.search(VALID_QUERY, null, null, 5000.0))
                .willThrow(new RuntimeException("예상치 못한 오류"));

        mockMvc.perform(searchRequest(VALID_QUERY))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message").value("서버 내부 오류가 발생했습니다"));
    }

    @Test
    @DisplayName("유효한 googlePlaceId로 장소 상세 조회 시 결과를 반환한다")
    void returnsPlaceDetailWhenGooglePlaceIdIsValid() throws Exception {
        given(placeDetailService.getDetail("ChIJ123"))
                .willReturn(placeDetailResult);

        mockMvc.perform(get("/places/{googlePlaceId}", "ChIJ123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.googlePlaceId").value("ChIJ123"))
                .andExpect(jsonPath("$.phoneNumber").value("02-123-4567"))
                .andExpect(jsonPath("$.photoNames[0]").value("places/ChIJ123/photos/a"));

        then(placeDetailService).should().getDetail("ChIJ123");
    }

    @Test
    @DisplayName("장소 상세 조회 중 외부 API 오류 발생 시 502를 반환한다")
    void returnsBadGatewayWhenPlaceDetailLookupFails() throws Exception {
        given(placeDetailService.getDetail("ChIJ123"))
                .willThrow(new ExternalApiException(new RuntimeException("connection timeout")));

        mockMvc.perform(get("/places/{googlePlaceId}", "ChIJ123"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("EXTERNAL_API_ERROR"))
                .andExpect(jsonPath("$.message").value("외부 API 호출 중 오류가 발생했습니다"));
    }

    private static MockHttpServletRequestBuilder searchRequest(String query) {
        return get(SEARCH_PATH).param("query", query);
    }
}
```

- [ ] **Step 2: 테스트 실행 → 실패 확인**

```bash
./gradlew test --tests "com.howaboutus.backend.places.controller.PlaceControllerTest"
```

Expected: `search(String, Double, Double, Double)` 시그니처 불일치로 컴파일 오류.

- [ ] **Step 3: 구현**

`PlaceController.java` 전체를 아래로 교체한다.

```java
package com.howaboutus.backend.places.controller;

import com.howaboutus.backend.places.controller.dto.PlaceDetailResponse;
import com.howaboutus.backend.places.controller.dto.PlaceSearchResponse;
import com.howaboutus.backend.places.service.PlaceDetailService;
import com.howaboutus.backend.places.service.PlaceSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Validated
@Tag(name = "Places", description = "장소 검색 API")
@RestController
@RequiredArgsConstructor
public class PlaceController {

    private final PlaceDetailService placeDetailService;
    private final PlaceSearchService placeSearchService;

    @Operation(
            summary = "장소 검색",
            description = "사용자 검색어를 기반으로 장소 후보 목록을 조회합니다. latitude/longitude를 제공하면 해당 좌표 근처 결과를 우선합니다."
    )
    @GetMapping("/places/search")
    public List<PlaceSearchResponse> search(
            @Parameter(description = "장소 검색어", example = "일본 맛집")
            @RequestParam
            @NotBlank(message = "검색어는 공백일 수 없습니다")
            String query,
            @Parameter(description = "현재 위치 위도 (longitude와 함께 제공)", example = "37.5")
            @RequestParam(required = false)
            Double latitude,
            @Parameter(description = "현재 위치 경도 (latitude와 함께 제공)", example = "127.0")
            @RequestParam(required = false)
            Double longitude,
            @Parameter(description = "검색 반경(m), 기본값 5000", example = "3000")
            @RequestParam(required = false, defaultValue = "5000.0")
            Double radius) {
        if ((latitude == null) != (longitude == null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "latitude와 longitude는 함께 제공해야 합니다");
        }
        return placeSearchService.search(query, latitude, longitude, radius)
                .stream()
                .map(PlaceSearchResponse::from)
                .toList();
    }

    @Operation(
            summary = "장소 상세 조회",
            description = "googlePlaceId를 기반으로 장소 상세 정보를 조회합니다."
    )
    @GetMapping("/places/{googlePlaceId}")
    public PlaceDetailResponse getDetail(
            @Parameter(description = "Google Place ID", example = "ChIJ123")
            @PathVariable
            @NotBlank(message = "googlePlaceId는 공백일 수 없습니다")
            String googlePlaceId) {
        return PlaceDetailResponse.from(placeDetailService.getDetail(googlePlaceId));
    }
}
```

- [ ] **Step 4: 전체 테스트 실행 → 통과 확인**

```bash
./gradlew test
```

Expected: 모든 테스트 PASS.

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/howaboutus/backend/places/controller/PlaceController.java \
        src/test/java/com/howaboutus/backend/places/controller/PlaceControllerTest.java
git commit -m "feat: 장소 텍스트 검색에 위치 편향(locationBias) 파라미터 추가"
```
