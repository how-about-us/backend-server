package com.howaboutus.backend.common.integration.ai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AiRecommendedPlace(
        @JsonProperty("place_id")
        String placeId,
        String name,
        String address,
        Double lat,
        Double lng,
        @JsonProperty("primary_type")
        String primaryType,
        String reason,
        @JsonProperty("google_maps_uri")
        String googleMapsUri
) {
}
