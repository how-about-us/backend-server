# GlobalExceptionHandler 리팩토링 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `ExternalApiException`을 `CustomException` 계층에 통합하고, `GlobalExceptionHandler`에 로깅 훅 포인트와 폴백 핸들러를 추가한다.

**Architecture:** `ExternalApiException`이 `CustomException`을 상속해 에러 코드 체계 안으로 편입된다. `GlobalExceptionHandler`는 별도 핸들러를 유지해 향후 로깅 확장 포인트를 보존하고, 처리되지 않은 예외를 위한 폴백 핸들러를 추가한다. 기존 `PlaceControllerTest`에 핸들러 동작 검증 케이스를 추가한다.

**Tech Stack:** Spring Boot 4.0.5, Java 21, JUnit 5, AssertJ, MockMvc

---

## File Map

| 작업 | 파일 |
|---|---|
| Create | `src/test/java/com/howaboutus/backend/common/error/ExternalApiExceptionTest.java` |
| Modify | `src/main/java/com/howaboutus/backend/common/error/ExternalApiException.java` |
| Modify | `src/main/java/com/howaboutus/backend/places/service/GooglePlaceSearchClient.java` |
| Modify | `src/test/java/com/howaboutus/backend/places/controller/PlaceControllerTest.java` |
| Modify | `src/main/java/com/howaboutus/backend/common/error/GlobalExceptionHandler.java` |

---

### Task 1: ExternalApiException 리팩토링

**Files:**
- Create: `src/test/java/com/howaboutus/backend/common/error/ExternalApiExceptionTest.java`
- Modify: `src/main/java/com/howaboutus/backend/common/error/ExternalApiException.java`
- Modify: `src/main/java/com/howaboutus/backend/places/service/GooglePlaceSearchClient.java`

- [ ] **Step 1: 실패 테스트 작성**

`src/test/java/com/howaboutus/backend/common/error/ExternalApiExceptionTest.java` 파일을 생성한다.

```java
package com.howaboutus.backend.common.error;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExternalApiExceptionTest {

    @Test
    @DisplayName("ExternalApiException은 EXTERNAL_API_ERROR 코드를 가진다")
    void hasExternalApiErrorCode() {
        ExternalApiException exception = new ExternalApiException(new RuntimeException("원인"));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.EXTERNAL_API_ERROR);
    }

    @Test
    @DisplayName("ExternalApiException은 원인 예외를 보존한다")
    void preservesCause() {
        RuntimeException cause = new RuntimeException("원인");
        ExternalApiException exception = new ExternalApiException(cause);

        assertThat(exception.getCause()).isSameAs(cause);
    }
}
```

- [ ] **Step 2: 테스트 실행 → 컴파일 실패 확인**

```bash
./gradlew test --tests "com.howaboutus.backend.common.error.ExternalApiExceptionTest"
```

Expected: 컴파일 오류 — `ExternalApiException`에 `getErrorCode()` 메서드 없음 (현재 `RuntimeException` 상속)

- [ ] **Step 3: ExternalApiException 구현**

`src/main/java/com/howaboutus/backend/common/error/ExternalApiException.java` 전체를 아래로 교체한다.

```java
package com.howaboutus.backend.common.error;

public class ExternalApiException extends CustomException {

    public ExternalApiException(Throwable cause) {
        super(ErrorCode.EXTERNAL_API_ERROR);
        initCause(cause);
    }
}
```

- [ ] **Step 4: GooglePlaceSearchClient 호출부 수정**

`src/main/java/com/howaboutus/backend/places/service/GooglePlaceSearchClient.java`의 catch 블록을 수정한다.

```java
        } catch (RestClientException exception) {
            throw new ExternalApiException(exception);
        }
```

- [ ] **Step 5: 테스트 실행 → PASS 확인**

```bash
./gradlew test --tests "com.howaboutus.backend.common.error.ExternalApiExceptionTest"
```

Expected: `ExternalApiExceptionTest` 2개 PASS

- [ ] **Step 6: 커밋**

```bash
git add src/main/java/com/howaboutus/backend/common/error/ExternalApiException.java \
        src/main/java/com/howaboutus/backend/places/service/GooglePlaceSearchClient.java \
        src/test/java/com/howaboutus/backend/common/error/ExternalApiExceptionTest.java
git commit -m "refactor: ExternalApiException을 CustomException 계층으로 통합"
```

---

### Task 2: GlobalExceptionHandler 개선

**Files:**
- Modify: `src/test/java/com/howaboutus/backend/places/controller/PlaceControllerTest.java`
- Modify: `src/main/java/com/howaboutus/backend/common/error/GlobalExceptionHandler.java`

- [ ] **Step 1: 실패 테스트 2개 추가**

`src/test/java/com/howaboutus/backend/places/controller/PlaceControllerTest.java`에 다음을 추가한다.

import 블록에 추가:
```java
import com.howaboutus.backend.common.error.ExternalApiException;
```

클래스 본문 끝에 테스트 메서드 2개 추가:
```java
    @Test
    @DisplayName("외부 API 오류 발생 시 502를 반환한다")
    void returnsBadGatewayWhenExternalApiErrorOccurs() throws Exception {
        given(placeSearchService.search(VALID_QUERY))
                .willThrow(new ExternalApiException(new RuntimeException("connection timeout")));

        mockMvc.perform(searchRequest(VALID_QUERY))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("EXTERNAL_API_ERROR"))
                .andExpect(jsonPath("$.message").value("외부 API 호출 중 오류가 발생했습니다"));
    }

    @Test
    @DisplayName("처리되지 않은 예외 발생 시 500을 반환한다")
    void returnsInternalServerErrorForUnhandledException() throws Exception {
        given(placeSearchService.search(VALID_QUERY))
                .willThrow(new RuntimeException("예상치 못한 오류"));

        mockMvc.perform(searchRequest(VALID_QUERY))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message").value("서버 내부 오류가 발생했습니다"));
    }
```

- [ ] **Step 2: 테스트 실행 → FAIL 확인**

```bash
./gradlew test --tests "com.howaboutus.backend.places.controller.PlaceControllerTest"
```

Expected:
- `returnsBadGatewayWhenExternalApiErrorOccurs` — FAIL (핸들러 없어서 500 반환)
- `returnsInternalServerErrorForUnhandledException` — FAIL (폴백 핸들러 없음)
- 기존 3개 테스트는 PASS 유지

- [ ] **Step 3: GlobalExceptionHandler 구현**

`src/main/java/com/howaboutus/backend/common/error/GlobalExceptionHandler.java` 전체를 아래로 교체한다.

```java
package com.howaboutus.backend.common.error;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ApiErrorResponse> handleCustomException(CustomException e) {
        ErrorCode errorCode = e.getErrorCode();
        return ResponseEntity.status(errorCode.getStatus())
                .body(ApiErrorResponse.of(errorCode.name(), errorCode.getMessage()));
    }

    @ExceptionHandler(ExternalApiException.class)
    public ResponseEntity<ApiErrorResponse> handleExternalApiException(ExternalApiException e) {
        // TODO: log.error("External API error", e.getCause())
        return handleCustomException(e);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleResponseStatusException(ResponseStatusException e) {
        HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
        if (status == null) status = HttpStatus.INTERNAL_SERVER_ERROR;
        String message = e.getReason() != null ? e.getReason() : "알 수 없는 오류가 발생했습니다";
        return ResponseEntity.status(status)
                .body(ApiErrorResponse.of(status.name(), message));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingServletRequestParameterException(
            MissingServletRequestParameterException e) {
        String message = "필수 요청 파라미터가 누락되었습니다: " + e.getParameterName();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of(HttpStatus.BAD_REQUEST.name(), message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleException(Exception e) {
        // TODO: log.error("Unhandled exception", e)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR.name(), "서버 내부 오류가 발생했습니다"));
    }
}
```

- [ ] **Step 4: 전체 테스트 실행 → PASS 확인**

```bash
./gradlew test --tests "com.howaboutus.backend.places.controller.PlaceControllerTest"
```

Expected: 5개 테스트 모두 PASS

- [ ] **Step 5: 전체 빌드 확인**

```bash
./gradlew build
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 6: 커밋**

```bash
git add src/main/java/com/howaboutus/backend/common/error/GlobalExceptionHandler.java \
        src/test/java/com/howaboutus/backend/places/controller/PlaceControllerTest.java
git commit -m "refactor: GlobalExceptionHandler 개선 및 폴백 핸들러 추가"
```
