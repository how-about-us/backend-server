package com.howaboutus.backend.common.integration.google.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GoogleTextSearchRequestTest {

    @Test
    @DisplayName("withKorean으로 생성하면 한국어와 locationBias.circle이 설정된다")
    void withKorean_setsLanguageAndLocationBias() {
        GoogleTextSearchRequest request = GoogleTextSearchRequest.withKorean("일본 맛집", 37.5, 127.0, 3000.0);

        assertThat(request.languageCode()).isEqualTo("ko");
        assertThat(request.locationBias()).isNotNull();
        assertThat(request.locationBias().circle()).isNotNull();
        assertThat(request.locationBias().circle().center().latitude()).isEqualTo(37.5);
        assertThat(request.locationBias().circle().center().longitude()).isEqualTo(127.0);
        assertThat(request.locationBias().circle().radius()).isEqualTo(3000.0);
    }
}
