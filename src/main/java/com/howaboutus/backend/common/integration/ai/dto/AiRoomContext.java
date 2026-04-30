package com.howaboutus.backend.common.integration.ai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AiRoomContext(
        String destination,
        @JsonProperty("travel_dates")
        AiTravelDateRange travelDates,
        @JsonProperty("participants_count")
        Integer participantsCount,
        @JsonProperty("bookmarked_places")
        List<AiContextPlace> bookmarkedPlaces,
        @JsonProperty("candidate_places")
        List<AiContextPlace> candidatePlaces
) {
    public AiRoomContext {
        bookmarkedPlaces = listOrEmpty(bookmarkedPlaces);
        candidatePlaces = listOrEmpty(candidatePlaces);
    }

    private static <T> List<T> listOrEmpty(List<T> value) {
        if (value == null) {
            return List.of();
        }
        return List.copyOf(value);
    }
}
