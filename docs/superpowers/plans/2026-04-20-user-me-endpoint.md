# 내 정보 조회 (GET /users/me) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 로그인된 사용자가 자신의 프로필을 조회할 수 있는 `GET /users/me` 엔드포인트 구현 (JWT 검증, 인증 필터 포함)

**Architecture:** JwtProvider에 토큰 검증 메서드를 추가하고, JwtAuthenticationFilter가 쿠키에서 access_token을 파싱하여 SecurityContext에 userId를 세팅한다. SecurityConfig에서 `/users/me`만 `authenticated()`로 보호하고, 나머지는 현행 `permitAll()` 유지. User 관련 클래스는 `auth/` → `user/` 패키지로 이동.

**Tech Stack:** Spring Boot 4.0.5, Spring Security, JJWT, JUnit 5, Mockito, AssertJ

**Spec:** `docs/superpowers/specs/2026-04-20-user-me-endpoint-design.md`

---

## File Structure

| 파일 | 상태 | 역할 |
|------|------|------|
| `auth/service/JwtProvider.java` | 수정 | `extractUserId()` 검증 메서드 추가 |
| `auth/filter/JwtAuthenticationFilter.java` | 신규 | 쿠키에서 JWT 파싱 → SecurityContext 세팅 |
| `common/config/SecurityConfig.java` | 수정 | 필터 등록, `/users/me` 인증 필수, AuthenticationEntryPoint |
| `common/error/ErrorCode.java` | 수정 | `ACCESS_TOKEN_EXPIRED`, `USER_NOT_FOUND` 추가 |
| `user/entity/User.java` | 이동 | `auth/entity/` → `user/entity/` |
| `user/repository/UserRepository.java` | 이동 | `auth/repository/` → `user/repository/` |
| `user/controller/UserController.java` | 신규 | `GET /users/me` 엔드포인트 |
| `user/service/UserService.java` | 신규 | 프로필 조회 비즈니스 로직 |
| `user/service/dto/UserResponse.java` | 신규 | 응답 DTO |
| `auth/service/AuthService.java` | 수정 | import 경로 변경 |
| `docs/ai/features.md` | 수정 | 상태를 `[x]`로 변경 |

---

### Task 1: ErrorCode 추가

**Files:**
- Modify: `src/main/java/com/howaboutus/backend/common/error/ErrorCode.java`

- [ ] **Step 1: `ACCESS_TOKEN_EXPIRED`와 `USER_NOT_FOUND` 에러 코드 추가**

```java
// 401 UNAUTHORIZED
GOOGLE_AUTH_FAILED(HttpStatus.UNAUTHORIZED, "Google 인증에 실패했습니다"),
INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다"),
ACCESS_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "액세스 토큰이 만료되었습니다"),
REFRESH_TOKEN_NOT_FOUND(HttpStatus.UNAUTHORIZED, "리프레시 토큰이 존재하지 않습니다"),
REFRESH_TOKEN_REUSE_DETECTED(HttpStatus.UNAUTHORIZED, "토큰 재사용이 감지되었습니다"),

// 404 NOT FOUND
USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다"),

// 502 BAD GATEWAY
EXTERNAL_API_ERROR(HttpStatus.BAD_GATEWAY, "외부 API 호출 중 오류가 발생했습니다");
```

- [ ] **Step 2: 빌드 확인**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add src/main/java/com/howaboutus/backend/common/error/ErrorCode.java
git commit -m "feat: ACCESS_TOKEN_EXPIRED, USER_NOT_FOUND 에러 코드 추가"
```

---

### Task 2: JwtProvider 토큰 검증 메서드 추가

**Files:**
- Modify: `src/main/java/com/howaboutus/backend/auth/service/JwtProvider.java`
- Test: `src/test/java/com/howaboutus/backend/auth/service/JwtProviderTest.java`

- [ ] **Step 1: 토큰 검증 테스트 작성**

`JwtProviderTest.java`에 다음 테스트 3개 추가:

```java
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.howaboutus.backend.common.error.CustomException;
import com.howaboutus.backend.common.error.ErrorCode;

@Test
@DisplayName("유효한 토큰에서 userId를 추출한다")
void extractsUserIdFromValidToken() {
    String token = jwtProvider.generateAccessToken(42L);

    Long userId = jwtProvider.extractUserId(token);

    assertThat(userId).isEqualTo(42L);
}

@Test
@DisplayName("만료된 토큰이면 ACCESS_TOKEN_EXPIRED 예외를 던진다")
void throwsAccessTokenExpiredForExpiredToken() {
    JwtProvider shortLivedProvider = new JwtProvider(secretKey, 0L);
    String token = shortLivedProvider.generateAccessToken(1L);

    assertThatThrownBy(() -> jwtProvider.extractUserId(token))
            .isInstanceOf(CustomException.class)
            .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.ACCESS_TOKEN_EXPIRED));
}

@Test
@DisplayName("위변조된 토큰이면 INVALID_TOKEN 예외를 던진다")
void throwsInvalidTokenForTamperedToken() {
    assertThatThrownBy(() -> jwtProvider.extractUserId("invalid.token.value"))
            .isInstanceOf(CustomException.class)
            .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_TOKEN));
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew test --tests "com.howaboutus.backend.auth.service.JwtProviderTest"`
Expected: FAIL — `extractUserId` 메서드가 없음

- [ ] **Step 3: `extractUserId()` 구현**

`JwtProvider.java`에 import 추가 및 메서드 구현:

```java
import com.howaboutus.backend.common.error.CustomException;
import com.howaboutus.backend.common.error.ErrorCode;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;

public Long extractUserId(String token) {
    try {
        String subject = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
        return Long.valueOf(subject);
    } catch (ExpiredJwtException e) {
        throw new CustomException(ErrorCode.ACCESS_TOKEN_EXPIRED, e);
    } catch (JwtException e) {
        throw new CustomException(ErrorCode.INVALID_TOKEN, e);
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew test --tests "com.howaboutus.backend.auth.service.JwtProviderTest"`
Expected: 5 tests PASSED

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/howaboutus/backend/auth/service/JwtProvider.java \
        src/test/java/com/howaboutus/backend/auth/service/JwtProviderTest.java
git commit -m "feat: JwtProvider에 토큰 검증 메서드 extractUserId 추가"
```

---

### Task 3: JwtAuthenticationFilter 구현

**Files:**
- Create: `src/main/java/com/howaboutus/backend/auth/filter/JwtAuthenticationFilter.java`
- Test: `src/test/java/com/howaboutus/backend/auth/filter/JwtAuthenticationFilterTest.java`

- [ ] **Step 1: 필터 테스트 작성**

```java
package com.howaboutus.backend.auth.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.howaboutus.backend.auth.service.JwtProvider;
import com.howaboutus.backend.common.error.CustomException;
import com.howaboutus.backend.common.error.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtProvider jwtProvider;

    @Mock
    private FilterChain filterChain;

    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(jwtProvider);
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("유효한 access_token 쿠키가 있으면 SecurityContext에 userId를 세팅한다")
    void setsSecurityContextWithValidToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("access_token", "valid-jwt"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        given(jwtProvider.extractUserId("valid-jwt")).willReturn(42L);

        filter.doFilterInternal(request, response, filterChain);

        Object principal = SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        assertThat(principal).isEqualTo(42L);
    }

    @Test
    @DisplayName("access_token 쿠키가 없으면 SecurityContext를 비워둔다")
    void doesNotSetSecurityContextWithoutCookie() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("토큰 검증 실패 시 SecurityContext를 비워둔다")
    void doesNotSetSecurityContextOnInvalidToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("access_token", "bad-jwt"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        given(jwtProvider.extractUserId("bad-jwt"))
                .willThrow(new CustomException(ErrorCode.INVALID_TOKEN));

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew test --tests "com.howaboutus.backend.auth.filter.JwtAuthenticationFilterTest"`
Expected: FAIL — 클래스 없음

- [ ] **Step 3: JwtAuthenticationFilter 구현**

```java
package com.howaboutus.backend.auth.filter;

import com.howaboutus.backend.auth.service.JwtProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtProvider jwtProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = extractToken(request);
        if (token != null) {
            try {
                Long userId = jwtProvider.extractUserId(token);
                var authentication = new UsernamePasswordAuthenticationToken(
                        userId, null, Collections.emptyList());
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (Exception e) {
                // 토큰 검증 실패 → 인증 없이 통과
            }
        }

        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        return Arrays.stream(cookies)
                .filter(c -> "access_token".equals(c.getName()))
                .findFirst()
                .map(Cookie::getValue)
                .orElse(null);
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew test --tests "com.howaboutus.backend.auth.filter.JwtAuthenticationFilterTest"`
Expected: 3 tests PASSED

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/howaboutus/backend/auth/filter/JwtAuthenticationFilter.java \
        src/test/java/com/howaboutus/backend/auth/filter/JwtAuthenticationFilterTest.java
git commit -m "feat: JWT 인증 필터 구현"
```

---

### Task 4: SecurityConfig 수정

**Files:**
- Modify: `src/main/java/com/howaboutus/backend/common/config/SecurityConfig.java`
- Test: `src/test/java/com/howaboutus/backend/common/config/SecurityConfigTest.java`

- [ ] **Step 1: SecurityConfig 인증 테스트 작성**

```java
package com.howaboutus.backend.common.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.howaboutus.backend.auth.filter.JwtAuthenticationFilter;
import com.howaboutus.backend.auth.service.JwtProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtProvider jwtProvider;

    @Test
    @DisplayName("인증 없이 /users/me 접근 시 401을 반환한다")
    void returns401ForUnauthenticatedUsersMe() throws Exception {
        mockMvc.perform(get("/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Swagger UI는 인증 없이 접근 가능하다")
    void swaggerIsPermitted() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().isOk());
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew test --tests "com.howaboutus.backend.common.config.SecurityConfigTest"`
Expected: FAIL — `/users/me`가 현재 `permitAll()`이라 401이 아닌 404 반환

- [ ] **Step 3: SecurityConfig 수정**

`SecurityConfig.java` 전체를 다음으로 교체:

```java
package com.howaboutus.backend.common.config;

import java.util.List;

import com.howaboutus.backend.auth.filter.JwtAuthenticationFilter;
import com.howaboutus.backend.common.config.properties.CorsProperties;
import com.howaboutus.backend.common.error.ApiErrorResponse;
import com.howaboutus.backend.common.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.ObjectMapper;

@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(CorsProperties.class)
public class SecurityConfig {

    private final CorsProperties corsProperties;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/v3/api-docs/**",
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/auth/google/login",
                                "/auth/refresh",
                                "/auth/logout")
                        .permitAll()
                        .requestMatchers("/users/me").authenticated()
                        // TODO: API가 갖춰지면 .anyRequest().authenticated() 로 전환
                        .anyRequest().permitAll())
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpStatus.UNAUTHORIZED.value());
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.setCharacterEncoding("UTF-8");
                            ApiErrorResponse body = ApiErrorResponse.of(ErrorCode.INVALID_TOKEN);
                            new ObjectMapper().writeValue(response.getOutputStream(), body);
                        }))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
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

> **Note:** Jackson 3.x 사용 (`tools.jackson.databind.ObjectMapper`). Spring Boot 4.0 기준.

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew test --tests "com.howaboutus.backend.common.config.SecurityConfigTest"`
Expected: 2 tests PASSED

- [ ] **Step 5: 기존 테스트 깨짐 확인 및 수정**

SecurityConfig에 `JwtAuthenticationFilter` 의존성이 추가되었으므로, 기존 `AuthControllerTest`와 `SwaggerEndpointsTest`에서 `JwtAuthenticationFilter`를 mock으로 추가해야 할 수 있다.

Run: `./gradlew test`
Expected: 기존 테스트가 깨지면 해당 테스트 클래스의 `@Import`에 `JwtAuthenticationFilter.class` 추가 또는 `@MockitoBean JwtProvider jwtProvider` 추가.

- [ ] **Step 6: 커밋**

```bash
git add src/main/java/com/howaboutus/backend/common/config/SecurityConfig.java \
        src/test/java/com/howaboutus/backend/common/config/SecurityConfigTest.java
git commit -m "feat: SecurityConfig에 JWT 인증 필터 등록 및 /users/me 인증 필수 설정"
```

---

### Task 5: User 엔티티·레포지토리 패키지 이동

**Files:**
- Move: `auth/entity/User.java` → `user/entity/User.java`
- Move: `auth/repository/UserRepository.java` → `user/repository/UserRepository.java`
- Modify: `auth/service/AuthService.java` (import 변경)
- Modify: 기존 테스트 파일 (import 변경)

- [ ] **Step 1: `user/entity/User.java` 생성 (패키지명만 변경)**

파일을 `src/main/java/com/howaboutus/backend/user/entity/User.java`로 이동하고 패키지 선언을 변경:

```java
package com.howaboutus.backend.user.entity;
```

나머지 코드는 동일.

- [ ] **Step 2: `user/repository/UserRepository.java` 생성 (패키지명, import 변경)**

파일을 `src/main/java/com/howaboutus/backend/user/repository/UserRepository.java`로 이동:

```java
package com.howaboutus.backend.user.repository;

import com.howaboutus.backend.user.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByProviderAndProviderId(String provider, String providerId);
}
```

- [ ] **Step 3: 기존 `auth/entity/User.java`, `auth/repository/UserRepository.java` 삭제**

```bash
rm src/main/java/com/howaboutus/backend/auth/entity/User.java
rm src/main/java/com/howaboutus/backend/auth/repository/UserRepository.java
rmdir src/main/java/com/howaboutus/backend/auth/entity 2>/dev/null || true
rmdir src/main/java/com/howaboutus/backend/auth/repository 2>/dev/null || true
```

- [ ] **Step 4: AuthService.java import 경로 수정**

```java
import com.howaboutus.backend.user.entity.User;
import com.howaboutus.backend.user.repository.UserRepository;
```

- [ ] **Step 5: 테스트 파일 import 경로 수정**

`AuthServiceTest.java`, `UserRepositoryTest.java`, `AuthIntegrationTest.java` 등에서 import 경로를 `com.howaboutus.backend.user.entity.User`, `com.howaboutus.backend.user.repository.UserRepository`로 변경. `UserRepositoryTest.java`는 `auth/repository/` → `user/repository/`로 파일 이동.

- [ ] **Step 6: 빌드 및 전체 테스트 확인**

Run: `./gradlew test`
Expected: ALL PASSED

- [ ] **Step 7: 커밋**

```bash
git add -A
git commit -m "refactor: User 엔티티·레포지토리를 auth에서 user 패키지로 이동"
```

---

### Task 6: UserResponse DTO 생성

**Files:**
- Create: `src/main/java/com/howaboutus/backend/user/service/dto/UserResponse.java`

- [ ] **Step 1: UserResponse record 작성**

```java
package com.howaboutus.backend.user.service.dto;

import com.howaboutus.backend.user.entity.User;

public record UserResponse(
        Long id,
        String email,
        String nickname,
        String profileImageUrl,
        String provider
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.getProfileImageUrl(),
                user.getProvider()
        );
    }
}
```

- [ ] **Step 2: 빌드 확인**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add src/main/java/com/howaboutus/backend/user/service/dto/UserResponse.java
git commit -m "feat: UserResponse DTO 추가"
```

---

### Task 7: UserService 구현

**Files:**
- Create: `src/main/java/com/howaboutus/backend/user/service/UserService.java`
- Test: `src/test/java/com/howaboutus/backend/user/service/UserServiceTest.java`

- [ ] **Step 1: UserService 테스트 작성**

```java
package com.howaboutus.backend.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.howaboutus.backend.common.error.CustomException;
import com.howaboutus.backend.common.error.ErrorCode;
import com.howaboutus.backend.user.entity.User;
import com.howaboutus.backend.user.repository.UserRepository;
import com.howaboutus.backend.user.service.dto.UserResponse;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    @Test
    @DisplayName("존재하는 userId로 프로필을 조회한다")
    void returnsUserProfile() {
        User user = User.ofGoogle("provider-id", "test@gmail.com", "닉네임", "https://img.url/photo.jpg");
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        UserResponse response = userService.getMyProfile(1L);

        assertThat(response.email()).isEqualTo("test@gmail.com");
        assertThat(response.nickname()).isEqualTo("닉네임");
        assertThat(response.profileImageUrl()).isEqualTo("https://img.url/photo.jpg");
        assertThat(response.provider()).isEqualTo("GOOGLE");
    }

    @Test
    @DisplayName("존재하지 않는 userId면 USER_NOT_FOUND 예외를 던진다")
    void throwsUserNotFoundForUnknownId() {
        given(userRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getMyProfile(999L))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.USER_NOT_FOUND));
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew test --tests "com.howaboutus.backend.user.service.UserServiceTest"`
Expected: FAIL — UserService 클래스 없음

- [ ] **Step 3: UserService 구현**

```java
package com.howaboutus.backend.user.service;

import com.howaboutus.backend.common.error.CustomException;
import com.howaboutus.backend.common.error.ErrorCode;
import com.howaboutus.backend.user.repository.UserRepository;
import com.howaboutus.backend.user.service.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;

    public UserResponse getMyProfile(Long userId) {
        return userRepository.findById(userId)
                .map(UserResponse::from)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew test --tests "com.howaboutus.backend.user.service.UserServiceTest"`
Expected: 2 tests PASSED

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/howaboutus/backend/user/service/UserService.java \
        src/test/java/com/howaboutus/backend/user/service/UserServiceTest.java
git commit -m "feat: UserService 프로필 조회 구현"
```

---

### Task 8: UserController 구현

**Files:**
- Create: `src/main/java/com/howaboutus/backend/user/controller/UserController.java`
- Test: `src/test/java/com/howaboutus/backend/user/controller/UserControllerTest.java`

- [ ] **Step 1: UserController 테스트 작성**

```java
package com.howaboutus.backend.user.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.howaboutus.backend.auth.filter.JwtAuthenticationFilter;
import com.howaboutus.backend.auth.service.JwtProvider;
import com.howaboutus.backend.common.config.SecurityConfig;
import com.howaboutus.backend.common.error.CustomException;
import com.howaboutus.backend.common.error.ErrorCode;
import com.howaboutus.backend.common.error.GlobalExceptionHandler;
import com.howaboutus.backend.user.service.UserService;
import com.howaboutus.backend.user.service.dto.UserResponse;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtProvider jwtProvider;

    @Test
    @DisplayName("인증된 사용자가 GET /users/me 요청 시 프로필을 반환한다")
    void returnsProfileForAuthenticatedUser() throws Exception {
        given(jwtProvider.extractUserId("valid-jwt")).willReturn(1L);
        given(userService.getMyProfile(1L))
                .willReturn(new UserResponse(1L, "test@gmail.com", "닉네임", "https://img.url/photo.jpg", "GOOGLE"));

        mockMvc.perform(get("/users/me")
                        .cookie(new Cookie("access_token", "valid-jwt")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.email").value("test@gmail.com"))
                .andExpect(jsonPath("$.nickname").value("닉네임"))
                .andExpect(jsonPath("$.profileImageUrl").value("https://img.url/photo.jpg"))
                .andExpect(jsonPath("$.provider").value("GOOGLE"));
    }

    @Test
    @DisplayName("인증 없이 GET /users/me 요청 시 401을 반환한다")
    void returns401WithoutAuthentication() throws Exception {
        mockMvc.perform(get("/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("존재하지 않는 사용자면 404를 반환한다")
    void returns404ForUnknownUser() throws Exception {
        given(jwtProvider.extractUserId("valid-jwt")).willReturn(999L);
        given(userService.getMyProfile(999L))
                .willThrow(new CustomException(ErrorCode.USER_NOT_FOUND));

        mockMvc.perform(get("/users/me")
                        .cookie(new Cookie("access_token", "valid-jwt")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew test --tests "com.howaboutus.backend.user.controller.UserControllerTest"`
Expected: FAIL — UserController 클래스 없음

- [ ] **Step 3: UserController 구현**

```java
package com.howaboutus.backend.user.controller;

import com.howaboutus.backend.user.service.UserService;
import com.howaboutus.backend.user.service.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getMyProfile(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(userService.getMyProfile(userId));
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew test --tests "com.howaboutus.backend.user.controller.UserControllerTest"`
Expected: 3 tests PASSED

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/howaboutus/backend/user/controller/UserController.java \
        src/test/java/com/howaboutus/backend/user/controller/UserControllerTest.java
git commit -m "feat: GET /users/me 엔드포인트 구현"
```

---

### Task 9: 전체 테스트 및 문서 갱신

**Files:**
- Modify: `docs/ai/features.md`

- [ ] **Step 1: 전체 테스트 실행**

Run: `./gradlew test`
Expected: ALL PASSED

- [ ] **Step 2: features.md 갱신**

`docs/ai/features.md`에서 "내 정보 조회" 행의 상태를 `[x]`로 변경:

```
| `[x]` | 내 정보 조회 | 로그인된 사용자 프로필 조회 | users |
```

- [ ] **Step 3: 커밋**

```bash
git add docs/ai/features.md
git commit -m "docs: 내 정보 조회 기능 구현 완료 표시"
```
