package com.howaboutus.backend.messages.controller.dto;

import com.howaboutus.backend.messages.service.dto.SendPlaceMessageCommand;

public record SendPlaceMessageRequest(
        String clientMessageId,
        String googlePlaceId,
        String name,
        String formattedAddress,
        Double latitude,
        Double longitude,
        Double rating,
        String photoName
) {

    public static SendPlaceMessageCommand toCommand(SendPlaceMessageRequest request) {
        return new SendPlaceMessageCommand(
                request.clientMessageId,
                request.googlePlaceId,
                request.name,
                request.formattedAddress,
                request.latitude,
                request.longitude,
                request.rating,
                request.photoName
        );
    }
}
