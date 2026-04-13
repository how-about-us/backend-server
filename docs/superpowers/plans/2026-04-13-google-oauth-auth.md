# Google OAuth 인증 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Google OAuth를 통한 소셜 로그인 + JWT 발급 + User 엔티티 구현

**Architecture:** 프론트엔드가 Google 동의화면에서 받은 Authorization Code를 백엔드로 전달하면, 백엔드가 Google Token 엔드포인트에서 id_token을 교환하고, 디코딩하여 사용자 정보를 추출한 뒤 JWT Access Token을 Set-Cookie(HttpOnly)로 발급한다. Spring OAuth2 Client를 사용하지 않고 수동 구현한다.

**Tech Stack:** Spring Boot 4.0.5, Spring Security, JJWT (io.jsonwebtoken), RestClient, JPA Auditing

**범위:** OAuth 로그인 흐름, User 엔티티, JWT 발급. Refresh Token(Redis)과 JWT 검증 필터는 이후 별도 작업으로 진행한다.

---

## 파일 구조

```
src/main/java/com/howaboutus/backend/
├── common/
│   ├── config/
│   │   ├── JpaAuditingConfig.java          ← 신규: JPA Auditing 활성화
│   │   ├── GoogleOAuthProperties.java      ← 신규: Google OAuth 설정
│   │   ├── GoogleOAuthClientConfig.java    ← 신규: Google OAuth용 RestClient Bean
│   │   ├── JwtProperties.java             ← 신규: JWT 설정
│   │   ├── JwtConfig.java                 ← 신규: JWT Bean 등록 + @EnableConfigurationProperties
│   │   ├── SecurityConfig.java            ← 수정: CORS 추가, /auth/** permitAll
│   │   └── CorsProperties.java            ← 신규: CORS 허용 도메인 설정
│   ├── entity/
│   │   └── BaseTimeEntity.java            ← 신규: 공통 생성/수정 시각
│   └── entity/
│       └── Place.java                     ← 수정: BaseTimeEntity 상속으로 전환
│   └── error/
│       └── ErrorCode.java                 ← 수정: 인증 관련 에러 코드 추가
│
├── auth/
│   ├── controller/
│   │   ├── AuthController.java            ← 신규: 로그인 엔드포인트
│   │   └── dto/
│   │       └── GoogleLoginRequest.java    ← 신규: 요청 DTO
│   ├── service/
│   │   ├── AuthService.java               ← 신규: 로그인 오케스트레이션
│   │   ├── GoogleOAuthClient.java         ← 신규: Google 토큰 교환
│   │   ├── JwtProvider.java               ← 신규: JWT 생성
│   │   └── dto/
│   │       ├── GoogleTokenResponse.java   ← 신규: Google 토큰 응답 DTO
│   │       └── GoogleUserInfo.java        ← 신규: id_token 디코딩 결과
│   ├── entity/
│   │   └── User.java                      ← 신규: 사용자 엔티티
│   └── repository/
│       └── UserRepository.java            ← 신규: 사용자 리포지토리

src/main/resources/
├── application.yaml                       ← 수정: google.oauth, jwt, cors 설정 추가

src/test/java/com/howaboutus/backend/
├── auth/
│   ├── controller/
│   │   └── AuthControllerTest.java        ← 신규: @WebMvcTest
│   ├── service/
│   │   ├── AuthServiceTest.java           ← 신규: 단위 테스트
│   │   ├── GoogleOAuthClientTest.java     ← 신규: 단위 테스트
│   │   └── JwtProviderTest.java           ← 신규: 단위 테스트
│   └── repository/
│       └── UserRepositoryTest.java        ← 신규: 통합 테스트
```

---

### Task 1: 의존성 추가

**Files:**
- Modify: `build.gradle`

- [ ] **Step 1: build.gradle에 JJWT 의존성 추가**

`build.gradle` dependencies 블록에 추가:

```groovy
implementation 'io.jsonwebtoken:jjwt-api:0.12.6'
runtimeOnly 'io.jsonwebtoken:jjwt-impl:0.12.6'
runtimeOnly 'io.jsonwebtoken:jjwt-jackson:0.12.6'
```

- [ ] **Step 2: 의존성 다운로드 확인**

Run: `./gradlew dependencies --configuration compileClasspath | grep jjwt`
Expected: `io.jsonwebtoken:jjwt-api:0.12.6` 출력

- [ ] **Step 3: 커밋**

```bash
git add build.gradle
git commit -m "chore: JJWT 의존성 추가"
```

---

### Task 2: JPA Auditing 설정 + BaseTimeEntity + Place 마이그레이션

**Files:**
- Create: `src/main/java/com/howaboutus/backend/common/config/JpaAuditingConfig.java`
- Create: `src/main/java/com/howaboutus/backend/common/entity/BaseTimeEntity.java`
- Modify: `src/main/java/com/howaboutus/backend/places/entity/Place.java`

- [ ] **Step 1: JpaAuditingConfig 생성**

```java
package com.howaboutus.backend.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
```

- [ ] **Step 2: BaseTimeEntity 생성**

```java
package com.howaboutus.backend.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseTimeEntity {

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;
}
```

- [ ] **Step 3: Place 엔티티를 BaseTimeEntity로 마이그레이션**

`Place.java`에서 자체 타임스탬프 필드를 제거하고 `BaseTimeEntity`를 상속:

```java
package com.howaboutus.backend.places.entity;

import com.howaboutus.backend.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "places")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Place extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "google_place_id", nullable = false, unique = true, length = 300)
    private String googlePlaceId;

    public Place(String googlePlaceId) {
        this.googlePlaceId = googlePlaceId;
    }
}
```

변경 사항: `@CreationTimestamp`/`@UpdateTimestamp` + `Instant` 필드 제거, `extends BaseTimeEntity` 추가.
`BaseTimeEntity`가 동일한 `Instant createdAt`/`updatedAt` 필드를 `@CreatedDate`/`@LastModifiedDate`로 제공하므로 DB 스키마 변경 없음.

- [ ] **Step 4: 기존 테스트 실행 — Place 관련 테스트 통과 확인**

Run: `./gradlew test`
Expected: 기존 Place 관련 테스트 전체 PASSED (BaseTimeEntity의 `@CreatedDate`/`@LastModifiedDate`가 동일한 컬럼 매핑을 유지하므로 동작 동일)

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/howaboutus/backend/common/config/JpaAuditingConfig.java \
        src/main/java/com/howaboutus/backend/common/entity/BaseTimeEntity.java \
        src/main/java/com/howaboutus/backend/places/entity/Place.java
git commit -m "feat: JPA Auditing 설정 및 BaseTimeEntity 추가, Place 엔티티 마이그레이션"
```

---

### Task 3: User 엔티티 + UserRepository

**Files:**
- Create: `src/main/java/com/howaboutus/backend/auth/entity/User.java`
- Create: `src/main/java/com/howaboutus/backend/auth/repository/UserRepository.java`
- Create: `src/test/java/com/howaboutus/backend/auth/repository/UserRepositoryTest.java`

- [ ] **Step 1: 실패하는 테스트 작성**

```java
package com.howaboutus.backend.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.howaboutus.backend.auth.entity.User;
import com.howaboutus.backend.support.BaseIntegrationTest;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class UserRepositoryTest extends BaseIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("provider와 providerId로 사용자를 조회한다")
    void findsByProviderAndProviderId() {
        User user = User.ofGoogle("google-123", "test@gmail.com", "테스트", "https://example.com/photo.jpg");
        userRepository.save(user);

        Optional<User> found = userRepository.findByProviderAndProviderId("GOOGLE", "google-123");

        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("test@gmail.com");
    }

    @Test
    @DisplayName("존재하지 않는 provider/providerId 조회 시 빈 Optional을 반환한다")
    void returnsEmptyWhenNotFound() {
        Optional<User> found = userRepository.findByProviderAndProviderId("GOOGLE", "nonexistent");

        assertThat(found).isEmpty();
    }
}
```

- [ ] **Step 2: 테스트 실행 — 컴파일 실패 확인**

Run: `./gradlew test --tests 'com.howaboutus.backend.auth.repository.UserRepositoryTest'`
Expected: 컴파일 실패 (User, UserRepository 클래스 없음)

- [ ] **Step 3: User 엔티티 구현**

```java
package com.howaboutus.backend.auth.entity;

import com.howaboutus.backend.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"provider", "provider_id"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(nullable = false, length = 50)
    private String nickname;

    @Column(length = 500)
    private String profileImageUrl;

    @Column(nullable = false, length = 20)
    private String provider;

    @Column(nullable = false, length = 255)
    private String providerId;

    private User(String providerId, String email, String nickname, String profileImageUrl, String provider) {
        this.providerId = providerId;
        this.email = email;
        this.nickname = nickname;
        this.profileImageUrl = profileImageUrl;
        this.provider = provider;
    }

    public static User ofGoogle(String providerId, String email, String nickname, String profileImageUrl) {
        return new User(providerId, email, nickname, profileImageUrl, "GOOGLE");
    }
}
```

- [ ] **Step 4: UserRepository 구현**

```java
package com.howaboutus.backend.auth.repository;

import com.howaboutus.backend.auth.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByProviderAndProviderId(String provider, String providerId);
}
```

- [ ] **Step 5: 테스트 실행 — 통과 확인**

Run: `./gradlew test --tests 'com.howaboutus.backend.auth.repository.UserRepositoryTest'`
Expected: 2 tests PASSED

- [ ] **Step 6: 커밋**

```bash
git add src/main/java/com/howaboutus/backend/auth/entity/User.java \
        src/main/java/com/howaboutus/backend/auth/repository/UserRepository.java \
        src/test/java/com/howaboutus/backend/auth/repository/UserRepositoryTest.java
git commit -m "feat: User 엔티티 및 UserRepository 추가"
```

---

### Task 4: 인증 관련 ErrorCode 추가

**Files:**
- Modify: `src/main/java/com/howaboutus/backend/common/error/ErrorCode.java`

- [ ] **Step 1: 인증 에러 코드 추가**

`ErrorCode.java`에 다음 항목 추가:

```java
// 401 UNAUTHORIZED
GOOGLE_AUTH_FAILED(HttpStatus.UNAUTHORIZED, "Google 인증에 실패했습니다"),
INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다"),
```

- [ ] **Step 2: 빌드 확인**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add src/main/java/com/howaboutus/backend/common/error/ErrorCode.java
git commit -m "feat: 인증 관련 ErrorCode 추가"
```

---

### Task 5: Google OAuth Properties + Client

**Files:**
- Create: `src/main/java/com/howaboutus/backend/common/config/GoogleOAuthProperties.java`
- Create: `src/main/java/com/howaboutus/backend/common/config/GoogleOAuthClientConfig.java`
- Modify: `src/main/resources/application.yaml`
- Create: `src/main/java/com/howaboutus/backend/auth/service/dto/GoogleTokenResponse.java`
- Create: `src/main/java/com/howaboutus/backend/auth/service/dto/GoogleUserInfo.java`
- Create: `src/main/java/com/howaboutus/backend/auth/service/GoogleOAuthClient.java`
- Create: `src/test/java/com/howaboutus/backend/auth/service/GoogleOAuthClientTest.java`

- [ ] **Step 1: 실패하는 테스트 작성**

```java
package com.howaboutus.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.howaboutus.backend.auth.service.dto.GoogleTokenResponse;
import com.howaboutus.backend.auth.service.dto.GoogleUserInfo;
import com.howaboutus.backend.common.config.GoogleOAuthProperties;
import com.howaboutus.backend.common.error.CustomException;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClient.RequestBodyUriSpec;
import org.springframework.web.client.RestClient.RequestBodySpec;
import org.springframework.web.client.RestClient.ResponseSpec;
import org.springframework.web.client.RestClientException;

class GoogleOAuthClientTest {

    private RestClient restClient;
    private GoogleOAuthProperties properties;
    private GoogleOAuthClient googleOAuthClient;

    @BeforeEach
    void setUp() {
        restClient = mock(RestClient.class);
        properties = new GoogleOAuthProperties(
                "test-client-id",
                "test-client-secret",
                "http://localhost:3000/callback",
                "https://oauth2.googleapis.com/token"
        );
        googleOAuthClient = new GoogleOAuthClient(restClient, properties);
    }

    @Test
    @DisplayName("Google id_token에서 사용자 정보를 추출한다")
    void extractsUserInfoFromIdToken() {
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                """
                {"sub":"google-123","email":"test@gmail.com","name":"테스트","picture":"https://example.com/photo.jpg"}
                """.getBytes()
        );
        String fakeIdToken = "header." + payload + ".signature";

        GoogleTokenResponse tokenResponse = new GoogleTokenResponse("access", fakeIdToken);

        RequestBodyUriSpec requestBodyUriSpec = mock(RequestBodyUriSpec.class);
        RequestBodySpec requestBodySpec = mock(RequestBodySpec.class);
        ResponseSpec responseSpec = mock(ResponseSpec.class);

        given(restClient.post()).willReturn(requestBodyUriSpec);
        given(requestBodyUriSpec.uri(properties.tokenUri())).willReturn(requestBodySpec);
        given(requestBodySpec.contentType(MediaType.APPLICATION_FORM_URLENCODED)).willReturn(requestBodySpec);
        given(requestBodySpec.body(any(String.class))).willReturn(requestBodySpec);
        given(requestBodySpec.retrieve()).willReturn(responseSpec);
        given(responseSpec.body(GoogleTokenResponse.class)).willReturn(tokenResponse);

        GoogleUserInfo userInfo = googleOAuthClient.login("auth-code");

        assertThat(userInfo.providerId()).isEqualTo("google-123");
        assertThat(userInfo.email()).isEqualTo("test@gmail.com");
        assertThat(userInfo.nickname()).isEqualTo("테스트");
        assertThat(userInfo.profileImageUrl()).isEqualTo("https://example.com/photo.jpg");
    }

    @Test
    @DisplayName("Google 토큰 교환 실패 시 CustomException을 던진다")
    void throwsExceptionWhenTokenExchangeFails() {
        RequestBodyUriSpec requestBodyUriSpec = mock(RequestBodyUriSpec.class);
        RequestBodySpec requestBodySpec = mock(RequestBodySpec.class);

        given(restClient.post()).willReturn(requestBodyUriSpec);
        given(requestBodyUriSpec.uri(properties.tokenUri())).willReturn(requestBodySpec);
        given(requestBodySpec.contentType(MediaType.APPLICATION_FORM_URLENCODED)).willReturn(requestBodySpec);
        given(requestBodySpec.body(any(String.class))).willReturn(requestBodySpec);
        given(requestBodySpec.retrieve()).willThrow(new RestClientException("connection refused"));

        assertThatThrownBy(() -> googleOAuthClient.login("bad-code"))
                .isInstanceOf(CustomException.class);
    }
}
```

- [ ] **Step 2: 테스트 실행 — 컴파일 실패 확인**

Run: `./gradlew test --tests 'com.howaboutus.backend.common.integration.google.GoogleOAuthClientTest'`
Expected: 컴파일 실패

- [ ] **Step 3: application.yaml에 Google OAuth, JWT, CORS 설정 추가**

`application.yaml` 기존 `google:` 블록 아래에 추가:

```yaml
  oauth:
    client-id: ${GOOGLE_CLIENT_ID}
    client-secret: ${GOOGLE_CLIENT_SECRET}
    redirect-uri: ${GOOGLE_REDIRECT_URI}
    token-uri: https://oauth2.googleapis.com/token

jwt:
  secret: ${JWT_SECRET}
  access-token-expiration: 3600000

cors:
  allowed-origins:
    - ${CORS_ALLOWED_ORIGIN:http://localhost:3000}
```

전체 `google:` 블록은 다음과 같이 됨:

```yaml
google:
  places:
    api-key: ${GOOGLE_PLACES_API_KEY}
    base-url: https://places.googleapis.com
    field-mask: places.id,places.displayName,...
  oauth:
    client-id: ${GOOGLE_CLIENT_ID}
    client-secret: ${GOOGLE_CLIENT_SECRET}
    redirect-uri: ${GOOGLE_REDIRECT_URI}
    token-uri: https://oauth2.googleapis.com/token
```

- [ ] **Step 4: GoogleOAuthProperties 생성**

```java
package com.howaboutus.backend.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "google.oauth")
public record GoogleOAuthProperties(
        String clientId,
        String clientSecret,
        String redirectUri,
        String tokenUri
) {
}
```

- [ ] **Step 5: GoogleOAuthClientConfig 생성**

```java
package com.howaboutus.backend.common.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(GoogleOAuthProperties.class)
public class GoogleOAuthClientConfig {

    @Bean
    RestClient googleOAuthRestClient() {
        return RestClient.builder().build();
    }
}
```

- [ ] **Step 6: GoogleTokenResponse DTO 생성**

```java
package com.howaboutus.backend.auth.service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GoogleTokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("id_token") String idToken
) {
}
```

- [ ] **Step 7: GoogleUserInfo DTO 생성**

```java
package com.howaboutus.backend.auth.service.dto;

public record GoogleUserInfo(
        String providerId,
        String email,
        String nickname,
        String profileImageUrl
) {
}
```

- [ ] **Step 8: GoogleOAuthClient 구현**

```java
package com.howaboutus.backend.auth.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.howaboutus.backend.auth.service.dto.GoogleTokenResponse;
import com.howaboutus.backend.auth.service.dto.GoogleUserInfo;
import com.howaboutus.backend.common.config.GoogleOAuthProperties;
import com.howaboutus.backend.common.error.CustomException;
import com.howaboutus.backend.common.error.ErrorCode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@RequiredArgsConstructor
public class GoogleOAuthClient {

    private final RestClient googleOAuthRestClient;
    private final GoogleOAuthProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GoogleUserInfo login(String authorizationCode) {
        GoogleTokenResponse tokenResponse = exchangeCode(authorizationCode);
        return extractUserInfo(tokenResponse.idToken());
    }

    private GoogleTokenResponse exchangeCode(String code) {
        String body = "grant_type=authorization_code"
                + "&code=" + URLEncoder.encode(code, StandardCharsets.UTF_8)
                + "&client_id=" + URLEncoder.encode(properties.clientId(), StandardCharsets.UTF_8)
                + "&client_secret=" + URLEncoder.encode(properties.clientSecret(), StandardCharsets.UTF_8)
                + "&redirect_uri=" + URLEncoder.encode(properties.redirectUri(), StandardCharsets.UTF_8);

        try {
            return googleOAuthRestClient.post()
                    .uri(properties.tokenUri())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(body)
                    .retrieve()
                    .body(GoogleTokenResponse.class);
        } catch (RestClientException e) {
            throw new CustomException(ErrorCode.GOOGLE_AUTH_FAILED);
        }
    }

    private GoogleUserInfo extractUserInfo(String idToken) {
        try {
            String payload = idToken.split("\\.")[1];
            byte[] decoded = Base64.getUrlDecoder().decode(payload);
            JsonNode claims = objectMapper.readTree(decoded);
            return new GoogleUserInfo(
                    claims.get("sub").asText(),
                    claims.get("email").asText(),
                    claims.get("name").asText(),
                    claims.has("picture") ? claims.get("picture").asText() : null
            );
        } catch (Exception e) {
            throw new CustomException(ErrorCode.GOOGLE_AUTH_FAILED);
        }
    }
}
```

- [ ] **Step 9: 테스트 실행 — 통과 확인**

Run: `./gradlew test --tests 'com.howaboutus.backend.common.integration.google.GoogleOAuthClientTest'`
Expected: 2 tests PASSED

- [ ] **Step 10: 커밋**

```bash
git add src/main/java/com/howaboutus/backend/common/config/GoogleOAuthProperties.java \
        src/main/java/com/howaboutus/backend/common/config/GoogleOAuthClientConfig.java \
        src/main/java/com/howaboutus/backend/auth/service/GoogleOAuthClient.java \
        src/main/java/com/howaboutus/backend/auth/service/dto/GoogleTokenResponse.java \
        src/main/java/com/howaboutus/backend/auth/service/dto/GoogleUserInfo.java \
        src/test/java/com/howaboutus/backend/auth/service/GoogleOAuthClientTest.java \
        src/main/resources/application.yaml
git commit -m "feat: Google OAuth 토큰 교환 및 사용자 정보 추출 구현"
```

---

### Task 6: JwtProvider

**Files:**
- Create: `src/main/java/com/howaboutus/backend/common/config/JwtProperties.java`
- Create: `src/main/java/com/howaboutus/backend/common/config/JwtConfig.java`
- Create: `src/main/java/com/howaboutus/backend/auth/service/JwtProvider.java`
- Create: `src/test/java/com/howaboutus/backend/auth/service/JwtProviderTest.java`

- [ ] **Step 1: 실패하는 테스트 작성**

```java
package com.howaboutus.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JwtProviderTest {

    private JwtProvider jwtProvider;
    private SecretKey secretKey;
    private static final String SECRET = "test-secret-key-that-is-at-least-256-bits-long-for-hmac-sha";
    private static final long EXPIRATION = 3600000L;

    @BeforeEach
    void setUp() {
        secretKey = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        jwtProvider = new JwtProvider(secretKey, EXPIRATION);
    }

    @Test
    @DisplayName("userId를 subject로 담은 JWT를 생성한다")
    void generatesJwtWithUserIdAsSubject() {
        String token = jwtProvider.generateAccessToken(1L);

        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        assertThat(claims.getSubject()).isEqualTo("1");
    }

    @Test
    @DisplayName("생성된 JWT에 만료 시간이 설정되어 있다")
    void generatesJwtWithExpiration() {
        String token = jwtProvider.generateAccessToken(1L);

        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        assertThat(claims.getExpiration()).isNotNull();
        assertThat(claims.getExpiration().getTime())
                .isGreaterThan(System.currentTimeMillis());
    }
}
```

- [ ] **Step 2: 테스트 실행 — 컴파일 실패 확인**

Run: `./gradlew test --tests 'com.howaboutus.backend.auth.service.JwtProviderTest'`
Expected: 컴파일 실패

- [ ] **Step 3: JwtProperties 생성**

```java
package com.howaboutus.backend.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        String secret,
        long accessTokenExpiration
) {
}
```

- [ ] **Step 4: JwtConfig 생성**

```java
package com.howaboutus.backend.common.config;

import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {

    @Bean
    SecretKey jwtSecretKey(JwtProperties properties) {
        return Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }
}
```

- [ ] **Step 5: JwtProvider 구현**

```java
package com.howaboutus.backend.auth.service;

import io.jsonwebtoken.Jwts;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtProvider {

    private final SecretKey secretKey;
    private final long accessTokenExpiration;

    public JwtProvider(SecretKey secretKey,
                       @Value("${jwt.access-token-expiration}") long accessTokenExpiration) {
        this.secretKey = secretKey;
        this.accessTokenExpiration = accessTokenExpiration;
    }

    public String generateAccessToken(Long userId) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiration(new Date(now.getTime() + accessTokenExpiration))
                .signWith(secretKey)
                .compact();
    }
}
```

- [ ] **Step 6: 테스트 실행 — 통과 확인**

Run: `./gradlew test --tests 'com.howaboutus.backend.auth.service.JwtProviderTest'`
Expected: 2 tests PASSED

- [ ] **Step 7: 커밋**

```bash
git add src/main/java/com/howaboutus/backend/common/config/JwtProperties.java \
        src/main/java/com/howaboutus/backend/common/config/JwtConfig.java \
        src/main/java/com/howaboutus/backend/auth/service/JwtProvider.java \
        src/test/java/com/howaboutus/backend/auth/service/JwtProviderTest.java
git commit -m "feat: JWT Access Token 생성 기능 구현"
```

---

### Task 7: AuthService

**Files:**
- Create: `src/main/java/com/howaboutus/backend/auth/service/AuthService.java`
- Create: `src/test/java/com/howaboutus/backend/auth/service/AuthServiceTest.java`

- [ ] **Step 1: 실패하는 테스트 작성**

```java
package com.howaboutus.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.howaboutus.backend.auth.entity.User;
import com.howaboutus.backend.auth.repository.UserRepository;
import com.howaboutus.backend.auth.service.dto.GoogleUserInfo;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class AuthServiceTest {

    private AuthService authService;
    private GoogleOAuthClient googleOAuthClient;
    private UserRepository userRepository;
    private JwtProvider jwtProvider;

    @BeforeEach
    void setUp() {
        googleOAuthClient = Mockito.mock(GoogleOAuthClient.class);
        userRepository = Mockito.mock(UserRepository.class);
        jwtProvider = Mockito.mock(JwtProvider.class);
        authService = new AuthService(googleOAuthClient, userRepository, jwtProvider);
    }

    @Test
    @DisplayName("신규 사용자 로그인 시 회원가입 후 JWT를 발급한다")
    void registersNewUserAndReturnsJwt() {
        GoogleUserInfo userInfo = new GoogleUserInfo("google-123", "test@gmail.com", "테스트", null);
        given(googleOAuthClient.login("auth-code")).willReturn(userInfo);
        given(userRepository.findByProviderAndProviderId("GOOGLE", "google-123")).willReturn(Optional.empty());
        given(userRepository.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(jwtProvider.generateAccessToken(any())).willReturn("jwt-token");

        String token = authService.googleLogin("auth-code");

        assertThat(token).isEqualTo("jwt-token");
    }

    @Test
    @DisplayName("기존 사용자 로그인 시 조회 후 JWT를 발급한다")
    void returnsJwtForExistingUser() {
        GoogleUserInfo userInfo = new GoogleUserInfo("google-123", "test@gmail.com", "테스트", null);
        User existingUser = User.ofGoogle("google-123", "test@gmail.com", "테스트", null);

        given(googleOAuthClient.login("auth-code")).willReturn(userInfo);
        given(userRepository.findByProviderAndProviderId("GOOGLE", "google-123"))
                .willReturn(Optional.of(existingUser));
        given(jwtProvider.generateAccessToken(any())).willReturn("jwt-token");

        String token = authService.googleLogin("auth-code");

        assertThat(token).isEqualTo("jwt-token");
    }
}
```

- [ ] **Step 2: 테스트 실행 — 컴파일 실패 확인**

Run: `./gradlew test --tests 'com.howaboutus.backend.auth.service.AuthServiceTest'`
Expected: 컴파일 실패

- [ ] **Step 3: AuthService 구현**

```java
package com.howaboutus.backend.auth.service;

import com.howaboutus.backend.auth.entity.User;
import com.howaboutus.backend.auth.repository.UserRepository;
import com.howaboutus.backend.auth.service.dto.GoogleUserInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final GoogleOAuthClient googleOAuthClient;
    private final UserRepository userRepository;
    private final JwtProvider jwtProvider;

    @Transactional
    public String googleLogin(String authorizationCode) {
        GoogleUserInfo userInfo = googleOAuthClient.login(authorizationCode);

        User user = userRepository.findByProviderAndProviderId("GOOGLE", userInfo.providerId())
                .orElseGet(() -> userRepository.save(
                        User.ofGoogle(
                                userInfo.providerId(),
                                userInfo.email(),
                                userInfo.nickname(),
                                userInfo.profileImageUrl()
                        )
                ));

        return jwtProvider.generateAccessToken(user.getId());
    }
}
```

- [ ] **Step 4: 테스트 실행 — 통과 확인**

Run: `./gradlew test --tests 'com.howaboutus.backend.auth.service.AuthServiceTest'`
Expected: 2 tests PASSED

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/howaboutus/backend/auth/service/AuthService.java \
        src/test/java/com/howaboutus/backend/auth/service/AuthServiceTest.java
git commit -m "feat: AuthService 구현 (Google 로그인 → 회원가입/조회 → JWT 발급)"
```

---

### Task 8: AuthController + SecurityConfig 업데이트

**Files:**
- Create: `src/main/java/com/howaboutus/backend/auth/controller/dto/GoogleLoginRequest.java`
- Create: `src/main/java/com/howaboutus/backend/auth/controller/AuthController.java`
- Create: `src/main/java/com/howaboutus/backend/common/config/CorsProperties.java`
- Modify: `src/main/java/com/howaboutus/backend/common/config/SecurityConfig.java`
- Create: `src/test/java/com/howaboutus/backend/auth/controller/AuthControllerTest.java`

- [ ] **Step 1: 실패하는 테스트 작성**

```java
package com.howaboutus.backend.auth.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.howaboutus.backend.auth.service.AuthService;
import com.howaboutus.backend.common.config.SecurityConfig;
import com.howaboutus.backend.common.error.CustomException;
import com.howaboutus.backend.common.error.ErrorCode;
import com.howaboutus.backend.common.error.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @Test
    @DisplayName("Google 로그인 성공 시 200과 access_token 쿠키를 반환한다")
    void returnsAccessTokenCookieOnSuccess() throws Exception {
        given(authService.googleLogin("valid-code")).willReturn("jwt-token");

        mockMvc.perform(post("/auth/google/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code": "valid-code"}
                                """))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("access_token"))
                .andExpect(cookie().httpOnly("access_token", true));
    }

    @Test
    @DisplayName("Google 인증 실패 시 401을 반환한다")
    void returns401WhenGoogleAuthFails() throws Exception {
        given(authService.googleLogin("bad-code"))
                .willThrow(new CustomException(ErrorCode.GOOGLE_AUTH_FAILED));

        mockMvc.perform(post("/auth/google/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code": "bad-code"}
                                """))
                .andExpect(status().isUnauthorized());
    }
}
```

- [ ] **Step 2: 테스트 실행 — 컴파일 실패 확인**

Run: `./gradlew test --tests 'com.howaboutus.backend.auth.controller.AuthControllerTest'`
Expected: 컴파일 실패

- [ ] **Step 3: GoogleLoginRequest DTO 생성**

```java
package com.howaboutus.backend.auth.controller.dto;

public record GoogleLoginRequest(
        String code
) {
}
```

- [ ] **Step 4: CorsProperties 생성**

```java
package com.howaboutus.backend.common.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cors")
public record CorsProperties(
        List<String> allowedOrigins
) {
}
```

- [ ] **Step 5: SecurityConfig 수정 — CORS 추가 + 경로별 접근 제어**

```java
package com.howaboutus.backend.common.config;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(CorsProperties.class)
public class SecurityConfig {

    private final CorsProperties corsProperties;

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/auth/**").permitAll()
                        .requestMatchers("/places/**").permitAll()
                        .requestMatchers("/actuator/**").permitAll()
                        .anyRequest().authenticated())
                .build();
    }

    private CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(corsProperties.allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
```

- [ ] **Step 6: AuthController 구현**

```java
package com.howaboutus.backend.auth.controller;

import com.howaboutus.backend.auth.controller.dto.GoogleLoginRequest;
import com.howaboutus.backend.auth.service.AuthService;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/google/login")
    public ResponseEntity<Void> googleLogin(@RequestBody GoogleLoginRequest request) {
        String accessToken = authService.googleLogin(request.code());

        ResponseCookie cookie = ResponseCookie.from("access_token", accessToken)
                .httpOnly(true)
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ofHours(1))
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .build();
    }
}
```

- [ ] **Step 7: 테스트 실행 — 통과 확인**

Run: `./gradlew test --tests 'com.howaboutus.backend.auth.controller.AuthControllerTest'`
Expected: 2 tests PASSED

- [ ] **Step 8: 전체 테스트 실행**

Run: `./gradlew test`
Expected: 기존 테스트 포함 전체 PASSED

- [ ] **Step 9: 커밋**

```bash
git add src/main/java/com/howaboutus/backend/auth/controller/AuthController.java \
        src/main/java/com/howaboutus/backend/auth/controller/dto/GoogleLoginRequest.java \
        src/main/java/com/howaboutus/backend/common/config/CorsProperties.java \
        src/main/java/com/howaboutus/backend/common/config/SecurityConfig.java \
        src/test/java/com/howaboutus/backend/auth/controller/AuthControllerTest.java
git commit -m "feat: Google OAuth 로그인 엔드포인트 및 CORS/SecurityConfig 구현"
```

---

### Task 9: 문서 업데이트

**Files:**
- Modify: `docs/ai/features.md`
- Modify: `docs/ai/erd.md` (Redis 키 패턴에 refresh token 예정 메모)

- [ ] **Step 1: features.md 구현 상태 갱신**

`features.md` 인증 섹션에서 구글 OAuth 로그인 상태를 `[x]`로 변경:

```markdown
| `[x]` | 구글 OAuth 로그인 | Google 계정으로 소셜 로그인 | users |
```

나머지(토큰 재발급, 로그아웃, 내 정보 조회)는 `[ ]` 유지.

- [ ] **Step 2: erd.md Redis 섹션에 refresh token 키 패턴 추가**

Redis 관리 데이터 테이블에 추가:

```markdown
| `refresh:{userId}` | Refresh Token 저장 (예정) | TTL 7~14일 |
```

- [ ] **Step 3: 커밋**

```bash
git add docs/ai/features.md docs/ai/erd.md
git commit -m "docs: 인증 기능 구현 상태 및 Redis refresh token 키 패턴 문서화"
```
