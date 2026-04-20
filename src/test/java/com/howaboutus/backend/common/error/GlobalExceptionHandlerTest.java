package com.howaboutus.backend.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

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
}
