package com.howaboutus.backend.common.integration.ai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AiMentionedPlace(
        String name,
        String source,
        String note
) {
}
