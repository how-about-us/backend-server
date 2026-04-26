package com.howaboutus.backend.common.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler globalExceptionHandler = new GlobalExceptionHandler();

    @Test
    @DisplayName("낙관적 락 예외는 SCHEDULE_CONFLICT 409 응답으로 변환한다")
    void handleOptimisticLockingFailureReturnsConflictResponse() {
        ObjectOptimisticLockingFailureException exception =
                new ObjectOptimisticLockingFailureException("Schedule", 100L);

        var response = globalExceptionHandler.handleOptimisticLockingFailure(exception);

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody()).isEqualTo(ApiErrorResponse.of(ErrorCode.SCHEDULE_CONFLICT));
    }

    @Test
    @DisplayName("MethodArgumentNotValidException 처리 시 같은 필드의 NotBlank 메시지를 우선한다")
    void handleMethodArgumentNotValidPrefersNotBlankMessage() {
        FieldError patternError = Mockito.mock(FieldError.class);
        given(patternError.getCode()).willReturn("Pattern");
        given(patternError.getDefaultMessage()).willReturn("googlePlaceId 형식이 올바르지 않습니다");
        FieldError notBlankError = Mockito.mock(FieldError.class);
        given(notBlankError.getCode()).willReturn("NotBlank");
        given(notBlankError.getDefaultMessage()).willReturn("googlePlaceId는 공백일 수 없습니다");

        BindingResult bindingResult = Mockito.mock(BindingResult.class);
        given(bindingResult.getFieldErrors()).willReturn(List.of(patternError, notBlankError));
        MethodArgumentNotValidException exception = Mockito.mock(MethodArgumentNotValidException.class);
        given(exception.getBindingResult()).willReturn(bindingResult);

        var response = globalExceptionHandler.handleMethodArgumentNotValidException(exception);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isEqualTo(ApiErrorResponse.of(HttpStatus.BAD_REQUEST, "googlePlaceId는 공백일 수 없습니다"));
    }

    @Test
    @DisplayName("HttpMessageNotReadableException 처리 시 400 응답을 반환한다")
    void handleHttpMessageNotReadableReturnsBadRequestResponse() {
        HttpInputMessage httpInputMessage = Mockito.mock(HttpInputMessage.class);
        HttpMessageNotReadableException exception = new HttpMessageNotReadableException("invalid body", httpInputMessage);

        var response = globalExceptionHandler.handleHttpMessageNotReadableException(exception);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isEqualTo(ApiErrorResponse.of(HttpStatus.BAD_REQUEST, "요청 본문 형식이 올바르지 않습니다"));
    }
}
