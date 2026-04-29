package com.howaboutus.backend.rooms.controller;

import com.howaboutus.backend.rooms.controller.dto.CreateRoomRequest;
import com.howaboutus.backend.rooms.controller.dto.InviteCodeResponse;
import com.howaboutus.backend.rooms.controller.dto.JoinRequest;
import com.howaboutus.backend.rooms.controller.dto.JoinRequestListResponse;
import com.howaboutus.backend.rooms.controller.dto.JoinResponse;
import com.howaboutus.backend.rooms.controller.dto.JoinStatusResponse;
import com.howaboutus.backend.rooms.controller.dto.RoomDetailResponse;
import com.howaboutus.backend.rooms.controller.dto.RoomListResponse;
import com.howaboutus.backend.rooms.controller.dto.RoomMemberListResponse;
import com.howaboutus.backend.rooms.controller.dto.UpdateRoomRequest;
import com.howaboutus.backend.rooms.service.RoomInviteService;
import com.howaboutus.backend.rooms.service.RoomMemberService;
import com.howaboutus.backend.rooms.service.RoomService;
import com.howaboutus.backend.rooms.service.dto.JoinResult;
import com.howaboutus.backend.rooms.service.dto.JoinStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Rooms", description = "여행 방 API")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/rooms")
public class RoomController {

    private final RoomService roomService;
    private final RoomInviteService roomInviteService;
    private final RoomMemberService roomMemberService;

    @Operation(summary = "방 생성", description = "새 여행 방을 생성합니다. 생성자는 자동으로 HOST가 됩니다.")
    @PostMapping
    public ResponseEntity<RoomDetailResponse> create(
            @AuthenticationPrincipal Long userId,
            @RequestBody @Valid CreateRoomRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(RoomDetailResponse.from(roomService.create(request.toCommand(), userId)));
    }

    @Operation(summary = "내 방 목록 조회", description = "참여 중인 방 목록을 커서 기반 페이지네이션으로 조회합니다.")
    @GetMapping
    public RoomListResponse getMyRooms(
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "커서 (joinedAt)") @RequestParam(required = false) Instant cursor,
            @Parameter(description = "페이지 크기") @RequestParam(defaultValue = "20") @Max(100) int size
    ) {
        return RoomListResponse.from(roomService.getMyRooms(userId, cursor, size));
    }

    @Operation(summary = "방 상세 조회", description = "방 메타정보를 조회합니다. 방 멤버만 접근 가능합니다.")
    @GetMapping("/{roomId}")
    public RoomDetailResponse getDetail(
            @AuthenticationPrincipal Long userId,
            @PathVariable UUID roomId
    ) {
        return RoomDetailResponse.from(roomService.getDetail(roomId, userId));
    }

    @Operation(summary = "방 수정", description = "방 제목, 여행지, 날짜를 수정합니다. HOST만 가능합니다.")
    @PatchMapping("/{roomId}")
    public RoomDetailResponse update(
            @AuthenticationPrincipal Long userId,
            @PathVariable UUID roomId,
            @RequestBody @Valid UpdateRoomRequest request
    ) {
        return RoomDetailResponse.from(roomService.update(roomId, request.toCommand(), userId));
    }

    @Operation(summary = "방 삭제", description = "방을 삭제합니다 (hard delete). HOST만 가능합니다.")
    @DeleteMapping("/{roomId}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal Long userId,
            @PathVariable UUID roomId
    ) {
        roomService.delete(roomId, userId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "초대 코드 재발급", description = "새 초대 코드를 발급합니다. HOST만 가능합니다.")
    @PostMapping("/{roomId}/invite-code")
    public InviteCodeResponse regenerateInviteCode(
            @AuthenticationPrincipal Long userId,
            @PathVariable UUID roomId
    ) {
        return new InviteCodeResponse(roomInviteService.regenerateInviteCode(roomId, userId));
    }

    @Operation(summary = "초대 코드로 입장 요청", description = "초대 코드를 사용해 방 입장을 요청합니다.")
    @PostMapping("/join")
    public ResponseEntity<JoinResponse> requestJoin(
            @AuthenticationPrincipal Long userId,
            @RequestBody @Valid JoinRequest request
    ) {
        JoinResult result = roomInviteService.requestJoin(request.inviteCode(), userId);
        HttpStatus status;
        if (result.status() == JoinStatus.ALREADY_MEMBER) {
            status = HttpStatus.OK;
        } else {
            status = HttpStatus.ACCEPTED;
        }
        return ResponseEntity.status(status).body(JoinResponse.from(result));
    }

    @Operation(summary = "입장 상태 조회", description = "입장 요청의 현재 상태를 확인합니다.")
    @GetMapping("/{roomId}/join/status")
    public JoinStatusResponse getJoinStatus(
            @AuthenticationPrincipal Long userId,
            @PathVariable UUID roomId
    ) {
        return JoinStatusResponse.from(roomInviteService.getJoinStatus(roomId, userId));
    }

    @Operation(summary = "대기 중인 입장 요청 목록", description = "방의 입장 대기 요청 목록을 조회합니다. HOST만 가능합니다.")
    @GetMapping("/{roomId}/join-requests")
    public JoinRequestListResponse getJoinRequests(
            @AuthenticationPrincipal Long userId,
            @PathVariable UUID roomId
    ) {
        return JoinRequestListResponse.from(roomInviteService.getJoinRequests(roomId, userId));
    }

    @Operation(summary = "입장 승인", description = "대기 중인 입장 요청을 승인합니다. HOST만 가능합니다.")
    @PostMapping("/{roomId}/join-requests/{requestId}/approve")
    public ResponseEntity<Void> approveJoinRequest(
            @AuthenticationPrincipal Long userId,
            @PathVariable UUID roomId,
            @PathVariable Long requestId
    ) {
        roomInviteService.approve(roomId, requestId, userId);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "입장 거절", description = "대기 중인 입장 요청을 거절합니다. HOST만 가능합니다.")
    @PostMapping("/{roomId}/join-requests/{requestId}/reject")
    public ResponseEntity<Void> rejectJoinRequest(
            @AuthenticationPrincipal Long userId,
            @PathVariable UUID roomId,
            @PathVariable Long requestId
    ) {
        roomInviteService.reject(roomId, requestId, userId);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "멤버 목록 조회", description = "방의 멤버 목록과 접속 상태를 조회합니다. HOST 또는 MEMBER만 접근 가능합니다.")
    @GetMapping("/{roomId}/members")
    public RoomMemberListResponse getMembers(
            @AuthenticationPrincipal Long userId,
            @PathVariable UUID roomId
    ) {
        return RoomMemberListResponse.from(roomMemberService.getMembers(roomId, userId));
    }

    @Operation(summary = "방 나가기", description = "방에서 나갑니다. MEMBER만 가능합니다. HOST는 나갈 수 없습니다.")
    @DeleteMapping("/{roomId}/members/me")
    public ResponseEntity<Void> leaveRoom(
            @AuthenticationPrincipal Long userId,
            @PathVariable UUID roomId
    ) {
        roomMemberService.leave(roomId, userId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "멤버 추방", description = "방에서 멤버를 추방합니다. HOST만 가능합니다.")
    @DeleteMapping("/{roomId}/members/{userId}")
    public ResponseEntity<Void> kickMember(
            @AuthenticationPrincipal Long hostUserId,
            @PathVariable UUID roomId,
            @PathVariable Long userId
    ) {
        roomMemberService.kick(roomId, userId, hostUserId);
        return ResponseEntity.noContent().build();
    }
}
