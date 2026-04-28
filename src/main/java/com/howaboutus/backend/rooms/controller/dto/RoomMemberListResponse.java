package com.howaboutus.backend.rooms.controller.dto;

import com.howaboutus.backend.rooms.service.dto.RoomMemberResult;
import java.util.List;

public record RoomMemberListResponse(
        List<RoomMemberResponse> members
) {
    public static RoomMemberListResponse from(List<RoomMemberResult> results) {
        List<RoomMemberResponse> members = results.stream()
                .map(RoomMemberResponse::from)
                .toList();
        return new RoomMemberListResponse(members);
    }
}
