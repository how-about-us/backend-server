package com.howaboutus.backend.auth.service.dto;

public record GoogleUserInfo(
        String providerId,
        String email,
        String nickname,
        String profileImageUrl
) {
}
