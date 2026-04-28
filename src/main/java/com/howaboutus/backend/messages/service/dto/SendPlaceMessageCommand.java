package com.howaboutus.backend.messages.service.dto;

public record SendPlaceMessageCommand(
        String clientMessageId,
        String googlePlaceId,
        String name,
        String formattedAddress,
        Double latitude,
        Double longitude,
        Double rating,
        String photoName
) {
}
