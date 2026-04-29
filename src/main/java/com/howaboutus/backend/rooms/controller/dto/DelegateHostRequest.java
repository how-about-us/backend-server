package com.howaboutus.backend.rooms.controller.dto;

import jakarta.validation.constraints.NotNull;

public record DelegateHostRequest(
        @NotNull(message = "위임 대상 userId는 필수입니다")
        Long targetUserId
) {}
