# Google Place Search Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Google Places API (New) 기반 `/places/search` 엔드포인트를 추가하고, `places`는 `google_place_id`만 영속 저장하는 참조 테이블로 유지하면서 검색 결과는 Redis에 10분 TTL로 캐시한다.

**Architecture:** Spring MVC + `RestClient` 기반 동기 외부 호출을 사용한다. Google Places Text Search 응답은 서비스 레이어에서 정규화하고, `PlaceReferenceService`가 내부 `places.id`를 보장하며, Redis는 검색 결과 DTO 캐시만 담당한다. Java 21 가상 스레드를 활성화해 동기 HTTP 호출의 스레드 비용을 낮춘다.

**Tech Stack:** Spring Boot 4, Spring MVC, Spring Security, Spring Data JPA, Redis, PostgreSQL/PostGIS, Testcontainers, `RestClient`, `MockRestServiceServer`

---

## File Structure

### Create

- `src/main/java/com/howaboutus/backend/config/GooglePlacesProperties.java`
- `src/main/java/com/howaboutus/backend/config/GooglePlacesClientConfig.java`
- `src/main/java/com/howaboutus/backend/config/SecurityConfig.java`
- `src/main/java/com/howaboutus/backend/common/error/ApiErrorResponse.java`
- `src/main/java/com/howaboutus/backend/common/error/GlobalExceptionHandler.java`
- `src/main/java/com/howaboutus/backend/common/error/ExternalApiException.java`
- `src/main/java/com/howaboutus/backend/places/entity/Place.java`
- `src/main/java/com/howaboutus/backend/places/repository/PlaceRepository.java`
- `src/main/java/com/howaboutus/backend/places/service/PlaceReferenceService.java`
- `src/main/java/com/howaboutus/backend/places/service/PlaceSearchCacheService.java`
- `src/main/java/com/howaboutus/backend/places/service/GooglePlaceSearchClient.java`
- `src/main/java/com/howaboutus/backend/places/service/PlaceSearchService.java`
- `src/main/java/com/howaboutus/backend/places/service/dto/GoogleTextSearchRequest.java`
- `src/main/java/com/howaboutus/backend/places/service/dto/GoogleTextSearchResponse.java`
- `src/main/java/com/howaboutus/backend/places/controller/PlaceController.java`
- `src/main/java/com/howaboutus/backend/places/controller/PlaceSearchResponse.java`
- `src/main/resources/application-test.yaml`
- `src/test/java/com/howaboutus/backend/places/controller/PlaceControllerTest.java`
- `src/test/java/com/howaboutus/backend/places/repository/PlaceReferenceServiceTest.java`
- `src/test/java/com/howaboutus/backend/places/service/GooglePlaceSearchClientTest.java`
- `src/test/java/com/howaboutus/backend/places/service/PlaceSearchServiceTest.java`

### Modify

- `src/main/resources/application.yaml`
- `src/main/resources/application-dev.yaml`
- `src/main/resources/application-prod.yaml`
- `src/test/java/com/howaboutus/backend/support/AbstractPostgresContainerTest.java`
- `docs/ai/features.md`
- `docs/ai/erd.md`

### Responsibilities

- `config/*`: Google API 설정, `RestClient` bean, security, virtual thread 관련 설정값
- `common/error/*`: 입력 오류와 외부 API 오류를 공통 응답으로 변환
- `places/entity|repository`: `google_place_id` 참조 테이블과 조회
- `places/service/*`: Google 호출, Redis 캐시, `places.id` 보장, 검색 orchestration
- `places/controller/*`: `/places/search` 엔드포인트와 응답 DTO
- `application-test.yaml` + `AbstractPostgresContainerTest`: Postgres/Redis 통합 테스트 환경
- `docs/ai/*`: 실제 정책과 구현 구조를 문서에 반영

## Task 1: Wire `/places/search` Into MVC And Security

**Files:**
- Create: `src/main/java/com/howaboutus/backend/config/SecurityConfig.java`
- Create: `src/main/java/com/howaboutus/backend/common/error/ApiErrorResponse.java`
- Create: `src/main/java/com/howaboutus/backend/common/error/GlobalExceptionHandler.java`
- Create: `src/main/java/com/howaboutus/backend/places/controller/PlaceController.java`
- Create: `src/main/java/com/howaboutus/backend/places/controller/PlaceSearchResponse.java`
- Test: `src/test/java/com/howaboutus/backend/places/controller/PlaceControllerTest.java`

- [ ] **Step 1: Write the failing MVC test**

```java
package com.howaboutus.backend.places.controller;

import com.howaboutus.backend.common.error.GlobalExceptionHandler;
import com.howaboutus.backend.config.SecurityConfig;
import com.howaboutus.backend.places.service.PlaceSearchService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PlaceController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class PlaceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PlaceSearchService placeSearchService;

    @Test
    void returnsBadRequestWhenQueryIsBlank() throws Exception {
        mockMvc.perform(get("/places/search").param("query", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("query must not be blank"));
    }

    @Test
    void returnsSearchResultsWhenQueryIsValid() throws Exception {
        given(placeSearchService.search("seoul cafe"))
                .willReturn(List.of(new PlaceSearchResponse(
                        1L,
                        "ChIJ123",
                        "Cafe Layered",
                        "서울 종로구 ...",
                        new PlaceSearchResponse.Location(37.57, 126.98),
                        "cafe",
                        4.5,
                        "places/ChIJ123/photos/abc"
                )));

        mockMvc.perform(get("/places/search").param("query", "seoul cafe"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].placeId").value(1L))
                .andExpect(jsonPath("$[0].googlePlaceId").value("ChIJ123"));
    }
}
```

- [ ] **Step 2: Run the MVC test to verify it fails**

Run:

```bash
./gradlew test --tests 'com.howaboutus.backend.places.controller.PlaceControllerTest'
```

Expected:

```text
FAILURE: Build failed with an exception.
* What went wrong:
Execution failed for task ':test'.
> No matching tests / or compilation errors because PlaceController and related types do not exist yet
```

- [ ] **Step 3: Add minimal controller, response DTO, security, and error handling**

```java
// src/main/java/com/howaboutus/backend/config/SecurityConfig.java
package com.howaboutus.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .httpBasic(Customizer.withDefaults())
                .formLogin(form -> form.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/places/**").permitAll()
                        .anyRequest().authenticated())
                .build();
    }
}
```

```java
// src/main/java/com/howaboutus/backend/common/error/ApiErrorResponse.java
package com.howaboutus.backend.common.error;

public record ApiErrorResponse(String code, String message) {
}
```

```java
// src/main/java/com/howaboutus/backend/common/error/GlobalExceptionHandler.java
package com.howaboutus.backend.common.error;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ApiErrorResponse handleIllegalArgument(IllegalArgumentException exception) {
        return new ApiErrorResponse("BAD_REQUEST", exception.getMessage());
    }

    @ExceptionHandler(ExternalApiException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    ApiErrorResponse handleExternalApiException(ExternalApiException exception) {
        return new ApiErrorResponse("EXTERNAL_API_ERROR", exception.getMessage());
    }
}
```

```java
// src/main/java/com/howaboutus/backend/places/controller/PlaceSearchResponse.java
package com.howaboutus.backend.places.controller;

public record PlaceSearchResponse(
        Long placeId,
        String googlePlaceId,
        String name,
        String formattedAddress,
        Location location,
        String primaryType,
        Double rating,
        String photoName
) {
    public record Location(Double lat, Double lng) {
    }
}
```

```java
// src/main/java/com/howaboutus/backend/places/controller/PlaceController.java
package com.howaboutus.backend.places.controller;

import com.howaboutus.backend.places.service.PlaceSearchService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PlaceController {

    private final PlaceSearchService placeSearchService;

    @GetMapping("/places/search")
    public List<PlaceSearchResponse> search(@RequestParam String query) {
        if (!StringUtils.hasText(query)) {
            throw new IllegalArgumentException("query must not be blank");
        }
        return placeSearchService.search(query);
    }
}
```

- [ ] **Step 4: Run the MVC test to verify it passes**

Run:

```bash
./gradlew test --tests 'com.howaboutus.backend.places.controller.PlaceControllerTest'
```

Expected:

```text
BUILD SUCCESSFUL
2 tests completed, 0 failed
```

- [ ] **Step 5: Commit the controller skeleton**

```bash
git add src/main/java/com/howaboutus/backend/config/SecurityConfig.java \
  src/main/java/com/howaboutus/backend/common/error/ApiErrorResponse.java \
  src/main/java/com/howaboutus/backend/common/error/GlobalExceptionHandler.java \
  src/main/java/com/howaboutus/backend/places/controller/PlaceController.java \
  src/main/java/com/howaboutus/backend/places/controller/PlaceSearchResponse.java \
  src/test/java/com/howaboutus/backend/places/controller/PlaceControllerTest.java
git commit -m "feat: 장소 검색 엔드포인트 골격 추가"
```

## Task 2: Add The Minimal Persistent Place Reference

**Files:**
- Create: `src/main/java/com/howaboutus/backend/places/entity/Place.java`
- Create: `src/main/java/com/howaboutus/backend/places/repository/PlaceRepository.java`
- Create: `src/main/java/com/howaboutus/backend/places/service/PlaceReferenceService.java`
- Create: `src/main/resources/application-test.yaml`
- Modify: `src/test/java/com/howaboutus/backend/support/AbstractPostgresContainerTest.java`
- Test: `src/test/java/com/howaboutus/backend/places/repository/PlaceReferenceServiceTest.java`

- [ ] **Step 1: Write the failing persistence test**

```java
package com.howaboutus.backend.places.repository;

import com.howaboutus.backend.places.service.PlaceReferenceService;
import com.howaboutus.backend.support.BaseIntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class PlaceReferenceServiceTest extends AbstractPostgresContainerTest {

    @Autowired
    private PlaceReferenceService placeReferenceService;

    @Autowired
    private PlaceRepository placeRepository;

    @Test
    @Transactional
    void createsMissingPlaceReferencesAndReusesExistingOnes() {
        Map<String, Long> first = placeReferenceService.ensurePlaceIds(List.of("ChIJ1", "ChIJ2"));
        Map<String, Long> second = placeReferenceService.ensurePlaceIds(List.of("ChIJ2", "ChIJ3"));

        assertThat(first).containsKeys("ChIJ1", "ChIJ2");
        assertThat(second.get("ChIJ2")).isEqualTo(first.get("ChIJ2"));
        assertThat(placeRepository.count()).isEqualTo(3);
    }
}
```

- [ ] **Step 2: Run the persistence test to verify it fails**

Run:

```bash
./gradlew test --tests 'com.howaboutus.backend.places.repository.PlaceReferenceServiceTest'
```

Expected:

```text
FAILURE: Build failed with an exception.
* What went wrong:
Compilation failure because Place, PlaceRepository, PlaceReferenceService, or test datasource settings do not exist yet
```

- [ ] **Step 3: Implement the `places` entity, repository, reference service, and test profile**

```java
// src/main/java/com/howaboutus/backend/places/entity/Place.java
package com.howaboutus.backend.places.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "places")
public class Place {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "google_place_id", nullable = false, unique = true, length = 300)
    private String googlePlaceId;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    protected Place() {
    }

    public Place(String googlePlaceId) {
        this.googlePlaceId = googlePlaceId;
    }

    public Long getId() {
        return id;
    }

    public String getGooglePlaceId() {
        return googlePlaceId;
    }
}
```

```java
// src/main/java/com/howaboutus/backend/places/repository/PlaceRepository.java
package com.howaboutus.backend.places.repository;

import com.howaboutus.backend.places.entity.Place;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlaceRepository extends JpaRepository<Place, Long> {

    Optional<Place> findByGooglePlaceId(String googlePlaceId);

    List<Place> findAllByGooglePlaceIdIn(Collection<String> googlePlaceIds);
}
```

```java
// src/main/java/com/howaboutus/backend/places/service/PlaceReferenceService.java
package com.howaboutus.backend.places.service;

import com.howaboutus.backend.places.entity.Place;
import com.howaboutus.backend.places.repository.PlaceRepository;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PlaceReferenceService {

    private final PlaceRepository placeRepository;

    @Transactional
    public Map<String, Long> ensurePlaceIds(Collection<String> googlePlaceIds) {
        Map<String, Long> existing = new LinkedHashMap<>();
        placeRepository.findAllByGooglePlaceIdIn(googlePlaceIds)
                .forEach(place -> existing.put(place.getGooglePlaceId(), place.getId()));

        Set<String> missing = googlePlaceIds.stream()
                .filter(id -> !existing.containsKey(id))
                .collect(java.util.stream.Collectors.toSet());

        for (String googlePlaceId : missing) {
            try {
                Place saved = placeRepository.saveAndFlush(new Place(googlePlaceId));
                existing.put(saved.getGooglePlaceId(), saved.getId());
            } catch (DataIntegrityViolationException ignored) {
                Place place = placeRepository.findByGooglePlaceId(googlePlaceId).orElseThrow();
                existing.put(place.getGooglePlaceId(), place.getId());
            }
        }

        return existing;
    }
}
```

```yaml
# src/main/resources/application-test.yaml
spring:
  docker:
    compose:
      enabled: false
  jpa:
    hibernate:
      ddl-auto: create-drop
```

```java
// src/test/java/com/howaboutus/backend/support/AbstractPostgresContainerTest.java
package com.howaboutus.backend.support;

import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@ActiveProfiles("test")
public abstract class AbstractPostgresContainerTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(
                    DockerImageName.parse("postgis/postgis:17-3.5")
                            .asCompatibleSubstituteFor("postgres"))
                    .withDatabaseName("howaboutus_test")
                    .withUsername("test")
                    .withPassword("test");

    @Container
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:8-alpine"))
                    .withExposedPorts(6379);

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }
}
```

- [ ] **Step 4: Run the persistence test to verify it passes**

Run:

```bash
./gradlew test --tests 'com.howaboutus.backend.places.repository.PlaceReferenceServiceTest'
```

Expected:

```text
BUILD SUCCESSFUL
1 test completed, 0 failed
```

- [ ] **Step 5: Commit the place reference layer**

```bash
git add src/main/java/com/howaboutus/backend/places/entity/Place.java \
  src/main/java/com/howaboutus/backend/places/repository/PlaceRepository.java \
  src/main/java/com/howaboutus/backend/places/service/PlaceReferenceService.java \
  src/main/resources/application-test.yaml \
  src/test/java/com/howaboutus/backend/support/AbstractPostgresContainerTest.java \
  src/test/java/com/howaboutus/backend/places/repository/PlaceReferenceServiceTest.java
git commit -m "feat: 장소 참조 테이블 구현"
```

## Task 3: Implement The Google Places Text Search Client

**Files:**
- Create: `src/main/java/com/howaboutus/backend/config/GooglePlacesProperties.java`
- Create: `src/main/java/com/howaboutus/backend/config/GooglePlacesClientConfig.java`
- Create: `src/main/java/com/howaboutus/backend/common/error/ExternalApiException.java`
- Create: `src/main/java/com/howaboutus/backend/places/service/GooglePlaceSearchClient.java`
- Create: `src/main/java/com/howaboutus/backend/places/service/dto/GoogleTextSearchRequest.java`
- Create: `src/main/java/com/howaboutus/backend/places/service/dto/GoogleTextSearchResponse.java`
- Modify: `src/main/resources/application.yaml`
- Modify: `src/main/resources/application-dev.yaml`
- Modify: `src/main/resources/application-prod.yaml`
- Test: `src/test/java/com/howaboutus/backend/places/service/GooglePlaceSearchClientTest.java`

- [ ] **Step 1: Write the failing client test**

```java
package com.howaboutus.backend.places.service;

import com.howaboutus.backend.config.GooglePlacesProperties;
import com.howaboutus.backend.places.service.dto.GoogleTextSearchResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GooglePlaceSearchClientTest {

    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private GooglePlaceSearchClient client;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new GooglePlaceSearchClient(
                builder.build(),
                new GooglePlacesProperties(
                        "test-key",
                        "https://places.googleapis.com",
                        "places.id,places.displayName,places.formattedAddress,places.location,places.primaryType,places.rating,places.photos"
                )
        );
    }

    @Test
    void searchesPlacesUsingTextSearchEndpoint() {
        server.expect(requestTo("https://places.googleapis.com/v1/places:searchText"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Goog-Api-Key", "test-key"))
                .andExpect(header("X-Goog-FieldMask", "places.id,places.displayName,places.formattedAddress,places.location,places.primaryType,places.rating,places.photos"))
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

        List<GoogleTextSearchResponse.PlaceItem> result = client.search("seoul cafe");

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().id()).isEqualTo("ChIJ123");
        server.verify();
    }
}
```

- [ ] **Step 2: Run the client test to verify it fails**

Run:

```bash
./gradlew test --tests 'com.howaboutus.backend.places.service.GooglePlaceSearchClientTest'
```

Expected:

```text
FAILURE: Build failed with an exception.
* What went wrong:
Compilation failure because GooglePlacesProperties, GooglePlaceSearchClient, or DTO classes do not exist yet
```

- [ ] **Step 3: Implement Google properties, `RestClient`, DTOs, and client**

```java
// src/main/java/com/howaboutus/backend/config/GooglePlacesProperties.java
package com.howaboutus.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "google.places")
public record GooglePlacesProperties(
        String apiKey,
        String baseUrl,
        String fieldMask
) {
}
```

```java
// src/main/java/com/howaboutus/backend/config/GooglePlacesClientConfig.java
package com.howaboutus.backend.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(GooglePlacesProperties.class)
public class GooglePlacesClientConfig {

    @Bean
    RestClient googlePlacesRestClient(GooglePlacesProperties properties) {
        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .build();
    }
}
```

```java
// src/main/java/com/howaboutus/backend/common/error/ExternalApiException.java
package com.howaboutus.backend.common.error;

public class ExternalApiException extends RuntimeException {

    public ExternalApiException(String message) {
        super(message);
    }
}
```

```java
// src/main/java/com/howaboutus/backend/places/service/dto/GoogleTextSearchRequest.java
package com.howaboutus.backend.places.service.dto;

public record GoogleTextSearchRequest(String textQuery) {
}
```

```java
// src/main/java/com/howaboutus/backend/places/service/dto/GoogleTextSearchResponse.java
package com.howaboutus.backend.places.service.dto;

import java.util.List;

public record GoogleTextSearchResponse(List<PlaceItem> places) {

    public record PlaceItem(
            String id,
            DisplayName displayName,
            String formattedAddress,
            Location location,
            String primaryType,
            Double rating,
            List<Photo> photos
    ) {
    }

    public record DisplayName(String text, String languageCode) {
    }

    public record Location(Double latitude, Double longitude) {
    }

    public record Photo(String name) {
    }
}
```

```java
// src/main/java/com/howaboutus/backend/places/service/GooglePlaceSearchClient.java
package com.howaboutus.backend.places.service;

import com.howaboutus.backend.common.error.ExternalApiException;
import com.howaboutus.backend.config.GooglePlacesProperties;
import com.howaboutus.backend.places.service.dto.GoogleTextSearchRequest;
import com.howaboutus.backend.places.service.dto.GoogleTextSearchResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@RequiredArgsConstructor
public class GooglePlaceSearchClient {

    private final RestClient googlePlacesRestClient;
    private final GooglePlacesProperties properties;

    public List<GoogleTextSearchResponse.PlaceItem> search(String query) {
        try {
            GoogleTextSearchResponse response = googlePlacesRestClient.post()
                    .uri("/v1/places:searchText")
                    .header("X-Goog-Api-Key", properties.apiKey())
                    .header("X-Goog-FieldMask", properties.fieldMask())
                    .body(new GoogleTextSearchRequest(query))
                    .retrieve()
                    .body(GoogleTextSearchResponse.class);

            return response == null || response.places() == null ? List.of() : response.places();
        } catch (RestClientException exception) {
            throw new ExternalApiException("Google Places API call failed");
        }
    }
}
```

```yaml
# src/main/resources/application.yaml
spring:
  application:
    name: how-about-us-backend
  jpa:
    open-in-view: false
  threads:
    virtual:
      enabled: true

google:
  places:
    api-key: ${GOOGLE_PLACES_API_KEY}
    base-url: https://places.googleapis.com
    field-mask: places.id,places.displayName,places.formattedAddress,places.location,places.primaryType,places.rating,places.photos
```

```yaml
# src/main/resources/application-dev.yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5433/${DB_NAME}
    username: ${DB_USER}
    password: ${DB_PASSWORD}
  docker:
    compose:
      enabled: true
      lifecycle-management: start-and-stop
      file:
        - compose.yaml
        - compose.dev.yaml
  mongodb:
    uri: mongodb://${MONGO_USER}:${MONGO_PASSWORD}@localhost:27017/${MONGO_DB}?authSource=admin
```

```yaml
# src/main/resources/application-prod.yaml
spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST}:5432/${DB_NAME}
    username: ${DB_USER}
    password: ${DB_PASSWORD}
  data:
    redis:
      host: ${REDIS_HOST:${DB_HOST}}
      port: 6379
  mongodb:
    uri: mongodb://${MONGO_USER}:${MONGO_PASSWORD}@${MONGO_HOST:${DB_HOST}}:27017/${MONGO_DB}?authSource=admin
  docker:
    compose:
      enabled: false
```

- [ ] **Step 4: Run the client test to verify it passes**

Run:

```bash
./gradlew test --tests 'com.howaboutus.backend.places.service.GooglePlaceSearchClientTest'
```

Expected:

```text
BUILD SUCCESSFUL
1 test completed, 0 failed
```

- [ ] **Step 5: Commit the Google client layer**

```bash
git add src/main/java/com/howaboutus/backend/config/GooglePlacesProperties.java \
  src/main/java/com/howaboutus/backend/config/GooglePlacesClientConfig.java \
  src/main/java/com/howaboutus/backend/common/error/ExternalApiException.java \
  src/main/java/com/howaboutus/backend/places/service/GooglePlaceSearchClient.java \
  src/main/java/com/howaboutus/backend/places/service/dto/GoogleTextSearchRequest.java \
  src/main/java/com/howaboutus/backend/places/service/dto/GoogleTextSearchResponse.java \
  src/main/resources/application.yaml \
  src/main/resources/application-dev.yaml \
  src/main/resources/application-prod.yaml \
  src/test/java/com/howaboutus/backend/places/service/GooglePlaceSearchClientTest.java
git commit -m "feat: 구글 장소 검색 클라이언트 추가"
```

## Task 4: Add Redis Cache And Orchestration Service

**Files:**
- Create: `src/main/java/com/howaboutus/backend/places/service/PlaceSearchCacheService.java`
- Create: `src/main/java/com/howaboutus/backend/places/service/PlaceSearchService.java`
- Test: `src/test/java/com/howaboutus/backend/places/service/PlaceSearchServiceTest.java`

- [ ] **Step 1: Write the failing service test**

```java
package com.howaboutus.backend.places.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.howaboutus.backend.places.controller.PlaceSearchResponse;
import com.howaboutus.backend.places.service.dto.GoogleTextSearchResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.then;

class PlaceSearchServiceTest {

    private PlaceSearchCacheService cacheService;
    private GooglePlaceSearchClient googleClient;
    private PlaceReferenceService placeReferenceService;
    private PlaceSearchService placeSearchService;

    @BeforeEach
    void setUp() {
        cacheService = Mockito.mock(PlaceSearchCacheService.class);
        googleClient = Mockito.mock(GooglePlaceSearchClient.class);
        placeReferenceService = Mockito.mock(PlaceReferenceService.class);
        placeSearchService = new PlaceSearchService(cacheService, googleClient, placeReferenceService);
    }

    @Test
    void returnsCachedResultsBeforeCallingGoogle() {
        List<PlaceSearchResponse> cached = List.of(new PlaceSearchResponse(
                1L, "ChIJ1", "Cafe Layered", "서울 종로구 ...",
                new PlaceSearchResponse.Location(37.57, 126.98), "cafe", 4.5, "places/ChIJ1/photos/1"));
        given(cacheService.get("seoul cafe")).willReturn(cached);

        List<PlaceSearchResponse> result = placeSearchService.search("seoul cafe");

        assertThat(result).isEqualTo(cached);
        then(googleClient).shouldHaveNoInteractions();
    }

    @Test
    void fetchesFromGooglePersistsInternalIdsAndCachesMisses() {
        given(cacheService.get("seoul cafe")).willReturn(null);
        given(googleClient.search("seoul cafe")).willReturn(List.of(
                new GoogleTextSearchResponse.PlaceItem(
                        "ChIJ1",
                        new GoogleTextSearchResponse.DisplayName("Cafe Layered", "ko"),
                        "서울 종로구 ...",
                        new GoogleTextSearchResponse.Location(37.57, 126.98),
                        "cafe",
                        4.5,
                        List.of(new GoogleTextSearchResponse.Photo("places/ChIJ1/photos/1"))
                )
        ));
        given(placeReferenceService.ensurePlaceIds(List.of("ChIJ1")))
                .willReturn(Map.of("ChIJ1", 11L));

        List<PlaceSearchResponse> result = placeSearchService.search("seoul cafe");

        assertThat(result.getFirst().placeId()).isEqualTo(11L);
        then(cacheService).should().put("seoul cafe", result);
    }
}
```

- [ ] **Step 2: Run the service test to verify it fails**

Run:

```bash
./gradlew test --tests 'com.howaboutus.backend.places.service.PlaceSearchServiceTest'
```

Expected:

```text
FAILURE: Build failed with an exception.
* What went wrong:
Compilation failure because PlaceSearchService and PlaceSearchCacheService do not exist yet
```

- [ ] **Step 3: Implement the cache service and orchestration service**

```java
// src/main/java/com/howaboutus/backend/places/service/PlaceSearchCacheService.java
package com.howaboutus.backend.places.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.howaboutus.backend.places.controller.PlaceSearchResponse;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PlaceSearchCacheService {

    private static final Duration TTL = Duration.ofMinutes(10);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public List<PlaceSearchResponse> get(String query) {
        try {
            String payload = redisTemplate.opsForValue().get(key(query));
            if (payload == null) {
                return null;
            }
            return objectMapper.readValue(payload, new TypeReference<>() {});
        } catch (Exception exception) {
            return null;
        }
    }

    public void put(String query, List<PlaceSearchResponse> responses) {
        try {
            redisTemplate.opsForValue().set(key(query), objectMapper.writeValueAsString(responses), TTL);
        } catch (Exception ignored) {
        }
    }

    private String key(String query) {
        return "places:search:" + normalize(query);
    }

    private String normalize(String query) {
        return query.trim().replaceAll("\\s+", " ").toLowerCase();
    }
}
```

```java
// src/main/java/com/howaboutus/backend/places/service/PlaceSearchService.java
package com.howaboutus.backend.places.service;

import com.howaboutus.backend.places.controller.PlaceSearchResponse;
import com.howaboutus.backend.places.service.dto.GoogleTextSearchResponse;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PlaceSearchService {

    private final PlaceSearchCacheService cacheService;
    private final GooglePlaceSearchClient googlePlaceSearchClient;
    private final PlaceReferenceService placeReferenceService;

    public List<PlaceSearchResponse> search(String query) {
        List<PlaceSearchResponse> cached = cacheService.get(query);
        if (cached != null) {
            return cached;
        }

        List<GoogleTextSearchResponse.PlaceItem> places = googlePlaceSearchClient.search(query);
        List<String> googlePlaceIds = places.stream()
                .map(GoogleTextSearchResponse.PlaceItem::id)
                .toList();
        Map<String, Long> internalIds = placeReferenceService.ensurePlaceIds(googlePlaceIds);

        List<PlaceSearchResponse> responses = places.stream()
                .map(place -> new PlaceSearchResponse(
                        internalIds.get(place.id()),
                        place.id(),
                        place.displayName() == null ? null : place.displayName().text(),
                        place.formattedAddress(),
                        place.location() == null ? null
                                : new PlaceSearchResponse.Location(place.location().latitude(), place.location().longitude()),
                        place.primaryType(),
                        place.rating(),
                        place.photos() == null || place.photos().isEmpty() ? null : place.photos().getFirst().name()
                ))
                .toList();

        cacheService.put(query, responses);
        return responses;
    }
}
```

- [ ] **Step 4: Run the service test to verify it passes**

Run:

```bash
./gradlew test --tests 'com.howaboutus.backend.places.service.PlaceSearchServiceTest'
```

Expected:

```text
BUILD SUCCESSFUL
2 tests completed, 0 failed
```

- [ ] **Step 5: Commit cache and orchestration**

```bash
git add src/main/java/com/howaboutus/backend/places/service/PlaceSearchCacheService.java \
  src/main/java/com/howaboutus/backend/places/service/PlaceSearchService.java \
  src/test/java/com/howaboutus/backend/places/service/PlaceSearchServiceTest.java
git commit -m "feat: 장소 검색 캐시와 서비스 구현"
```

## Task 5: Close The Loop With End-To-End Tests And Docs

**Files:**
- Modify: `src/test/java/com/howaboutus/backend/HowAboutUsBackendApplicationTests.java`
- Modify: `src/test/java/com/howaboutus/backend/places/controller/PlaceControllerTest.java`
- Modify: `docs/ai/features.md`
- Modify: `docs/ai/erd.md`

- [ ] **Step 1: Write the failing full integration test**

```java
package com.howaboutus.backend;

import com.howaboutus.backend.support.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class HowAboutUsBackendApplicationTests extends AbstractPostgresContainerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PlaceSearchService placeSearchService;

    @Test
    void placeSearchEndpointIsExposed() throws Exception {
        given(placeSearchService.search("seoul cafe"))
                .willReturn(java.util.List.of(new PlaceSearchResponse(
                        1L, "ChIJ1", "Cafe Layered", "서울 종로구 ...",
                        new PlaceSearchResponse.Location(37.57, 126.98), "cafe", 4.5, null
                )));

        mockMvc.perform(get("/places/search").param("query", "seoul cafe"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].placeId").value(1L));
    }
}
```

- [ ] **Step 2: Run the focused integration suite to verify it fails**

Run:

```bash
./gradlew test --tests 'com.howaboutus.backend.HowAboutUsBackendApplicationTests'
```

Expected:

```text
FAILURE: Build failed with an exception.
* What went wrong:
Compilation failure or context wiring failure until controller, security, and service beans are all connected correctly
```

- [ ] **Step 3: Make the integration suite pass and update docs**

```md
<!-- docs/ai/features.md -->
| `[ ]` | 장소 검색 | Google Places API (New)로 장소 검색, `google_place_id`만 DB에 영속 저장하고 검색 결과는 Redis에 10분 TTL 캐시 | places, Redis |
```

```md
<!-- docs/ai/erd.md -->
## 5. places (장소)

Google Place ID를 내부 FK와 연결하기 위한 영속 참조 테이블. Google 장소 상세 데이터 자체는 영속 저장하지 않는다.

| 컬럼 | 타입 | 제약조건 | 설명 |
|------|------|----------|------|
| id | BIGINT | PK, AUTO_INCREMENT | |
| google_place_id | VARCHAR(300) | UNIQUE, NOT NULL | Google Place ID |
| created_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | 생성일시 |
| updated_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | 수정일시 |
```

Also update related explanation blocks in `docs/ai/erd.md` so they no longer mention `places` as a Google payload cache, and note that search result caching lives in Redis for 10 minutes.

- [ ] **Step 4: Run the full verification set**

Run:

```bash
./gradlew test
```

Expected:

```text
BUILD SUCCESSFUL
All tests passed
```

Then run the markdown conflict check:

```bash
rg -n '`docs/ai/[^`]*\.md`|`[A-Z][A-Z_]*\.md`' -g '*.md' AGENTS.md CONTRIBUTING.md docs/ai docs/superpowers
```

Expected:

```text
Referenced markdown paths resolve correctly and there are no dead references introduced by the docs updates
```

- [ ] **Step 5: Commit the end-to-end wiring and docs**

```bash
git add src/test/java/com/howaboutus/backend/HowAboutUsBackendApplicationTests.java \
  docs/ai/features.md \
  docs/ai/erd.md
git commit -m "feat: 구글 장소 검색 기능 마무리"
```

## Self-Review

### Spec Coverage

- `/places/search` 엔드포인트: Task 1, Task 4, Task 5
- `places` 최소 참조 테이블: Task 2
- Google Places Text Search (New): Task 3
- Redis 10분 TTL 캐시: Task 4
- `RestClient + virtual threads`: Task 3
- 문서 갱신: Task 5

### Placeholder Scan

- `TBD`, `TODO`, `implement later`, `fill in details` 없음
- 각 task는 파일 경로, 테스트, 실행 명령, 커밋 메시지를 포함함

### Type Consistency

- 응답 타입은 모든 task에서 `PlaceSearchResponse`
- 영속 참조 서비스는 모든 task에서 `PlaceReferenceService.ensurePlaceIds`
- 외부 호출 DTO는 `GoogleTextSearchResponse.PlaceItem`
