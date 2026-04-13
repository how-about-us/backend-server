# GlobalExceptionHandler 리팩토링 설계

## 개요

`GlobalExceptionHandler`와 관련 예외 클래스(`ExternalApiException`)의 구조적 문제를 해소하고,
미처리 예외에 대한 폴백 핸들러를 추가하는 리팩토링.

## 동기

- `handleExternalApiException()`이 `new CustomException(ErrorCode.EXTERNAL_API_ERROR)`를 내부에서 생성해 위임하는 구조가 어색함
- `ExternalApiException`이 `RuntimeException`을 직접 상속해 에러 코드 체계 외부에 존재함
- 향후 외부 API 오류에 로깅/알림을 붙일 확장 포인트가 없음
- `MissingServletRequestParameterException` 핸들러에 도달 불가능한 `isBlank()` 분기가 존재함
- 처리되지 않은 예외에 대한 폴백이 없어 Spring 기본 응답이 노출됨

## 설계

### 1. ExternalApiException 구조 변경

`RuntimeException` 대신 `CustomException`을 상속한다.
생성자는 `String message` 대신 `Throwable cause`를 받아 원인 예외를 보존한다.

```java
public class ExternalApiException extends CustomException {
    public ExternalApiException(Throwable cause) {
        super(ErrorCode.EXTERNAL_API_ERROR);
        initCause(cause);
    }
}
```

- `initCause()`를 사용하는 이유: `CustomException`이 Lombok `@RequiredArgsConstructor`로
  `CustomException(ErrorCode)` 생성자만 갖고 있어, cause를 부모 생성자에 직접 넘길 수 없음
- 원인 예외는 향후 `e.getCause()`로 로거에서 접근 가능

### 2. GooglePlaceSearchClient 호출부 변경

```java
// Before
throw new ExternalApiException("Google Places API 호출에 실패했습니다");

// After
throw new ExternalApiException(exception);
```

### 3. GlobalExceptionHandler 개선

#### 3-1. handleExternalApiException — 파라미터 복원 + 로깅 훅

```java
@ExceptionHandler(ExternalApiException.class)
public ResponseEntity<ApiErrorResponse> handleExternalApiException(ExternalApiException e) {
    // TODO: log.error("External API error", e.getCause())
    return handleCustomException(e);
}
```

#### 3-2. handleResponseStatusException — if-else 간결화

```java
@ExceptionHandler(ResponseStatusException.class)
public ResponseEntity<ApiErrorResponse> handleResponseStatusException(ResponseStatusException e) {
    HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
    if (status == null) status = HttpStatus.INTERNAL_SERVER_ERROR;
    String message = e.getReason() != null ? e.getReason() : "알 수 없는 오류가 발생했습니다";
    return ResponseEntity.status(status)
            .body(ApiErrorResponse.of(status.name(), message));
}
```

#### 3-3. handleMissingServletRequestParameterException — 불필요한 분기 제거

Spring은 파라미터 이름 없이 이 예외를 throw할 수 없으므로 `isBlank()` 분기는 도달 불가능한 코드다.

```java
@ExceptionHandler(MissingServletRequestParameterException.class)
public ResponseEntity<ApiErrorResponse> handleMissingServletRequestParameterException(
        MissingServletRequestParameterException e) {
    String message = "필수 요청 파라미터가 누락되었습니다: " + e.getParameterName();
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ApiErrorResponse.of(HttpStatus.BAD_REQUEST.name(), message));
}
```

#### 3-4. 폴백 Exception 핸들러 추가

처리되지 않은 모든 예외를 포착해 일관된 JSON 형식으로 응답한다.

```java
@ExceptionHandler(Exception.class)
public ResponseEntity<ApiErrorResponse> handleException(Exception e) {
    // TODO: log.error("Unhandled exception", e)
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR.name(), "서버 내부 오류가 발생했습니다"));
}
```

## 변경 파일 요약

| 파일 | 변경 내용 |
|---|---|
| `common/error/ExternalApiException.java` | `CustomException` 상속, `Throwable` 생성자로 변경 |
| `common/error/GlobalExceptionHandler.java` | 핸들러 3곳 간결화 + 폴백 핸들러 추가 |
| `places/service/GooglePlaceSearchClient.java` | `new ExternalApiException(exception)` |

## 결정 근거

- `ExternalApiException`을 `CustomException`의 서브클래스로 두되 **별도 핸들러를 유지**하는 이유:
  의미론적 구분(외부 API 오류임을 코드로 표현)과 향후 확장 포인트(로깅/알림) 모두를 충족하기 위함
- `String message` → `Throwable cause` 변경: 응답에 내부 메시지를 노출하지 않으면서
  디버깅에 유용한 원인 예외를 보존
