package com.howaboutus.backend.rooms.controller.dto;

import jakarta.validation.constraints.NotBlank;

public record JoinRequest(
        @NotBlank(message = "초대 코드는 필수입니다")
        String inviteCode
) {
}
