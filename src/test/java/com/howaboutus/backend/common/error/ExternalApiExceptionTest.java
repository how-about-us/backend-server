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
