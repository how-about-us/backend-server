package com.howaboutus.backend.user.service.dto;

import com.howaboutus.backend.user.entity.User;

public record UserResponse(
        Long id,
        String email,
        String nickname,
        String profileImageUrl,
        String provider
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.getProfileImageUrl(),
                user.getProvider()
        );
    }
}
