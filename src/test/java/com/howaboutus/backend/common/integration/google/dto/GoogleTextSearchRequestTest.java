package com.howaboutus.backend.common.integration.google.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class GoogleTextSearchRequestTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("withKorean으로 생성하면 locationBias.circle이 JSON에 포함된다")
    void withKorean_includesLocationBiasInJson() throws Exception {
        GoogleTextSearchRequest request = GoogleTextSearchRequest.withKorean("일본 맛집", 37.5, 127.0, 3000.0);

        String json = objectMapper.writeValueAsString(request);

        assertThat(json).contains("\"locationBias\"");
        assertThat(json).contains("\"circle\"");
        assertThat(json).contains("37.5");
        assertThat(json).contains("127.0");
        assertThat(json).contains("3000.0");
    }
}
