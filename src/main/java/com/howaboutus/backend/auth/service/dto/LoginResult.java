package com.howaboutus.backend.auth.service.dto;

public record LoginResult(
        String accessToken,
        String refreshToken,
        Long userId
) {
}
