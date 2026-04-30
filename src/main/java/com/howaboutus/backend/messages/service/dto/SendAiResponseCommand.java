package com.howaboutus.backend.messages.service.dto;

import java.util.List;
import java.util.Map;

public record SendAiResponseCommand(
        String requestMessageId,
        String content,
        String intent,
        List<Map<String, Object>> recommendedPlaces
) {
    public SendAiResponseCommand {
        if (recommendedPlaces == null) {
            recommendedPlaces = List.of();
        } else {
            recommendedPlaces = List.copyOf(recommendedPlaces);
        }
    }
}
