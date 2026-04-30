package com.howaboutus.backend.common.integration.ai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AiTravelDateRange(
        @JsonProperty("start_date")
        String startDate,
        @JsonProperty("end_date")
        String endDate
) {
}
