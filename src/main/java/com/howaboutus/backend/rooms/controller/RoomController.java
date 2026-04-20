package com.howaboutus.backend.rooms.controller;

import com.howaboutus.backend.rooms.controller.dto.CreateRoomRequest;
import com.howaboutus.backend.rooms.controller.dto.RoomDetailResponse;
import com.howaboutus.backend.rooms.controller.dto.RoomListResponse;
import com.howaboutus.backend.rooms.controller.dto.UpdateRoomRequest;
import com.howaboutus.backend.rooms.service.RoomService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Rooms", description = "여행 방 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/rooms")
public class RoomController {

    private final RoomService roomService;

    @Operation(summary = "방 생성", description = "새 여행 방을 생성합니다. 생성자는 자동으로 HOST가 됩니다.")
    @PostMapping
    public ResponseEntity<RoomDetailResponse> create(
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody @Valid CreateRoomRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(RoomDetailResponse.from(roomService.create(request.toCommand(), userId)));
    }

    @Operation(summary = "내 방 목록 조회", description = "참여 중인 방 목록을 커서 기반 페이지네이션으로 조회합니다.")
    @GetMapping
    public RoomListResponse getMyRooms(
            @RequestHeader("X-User-Id") Long userId,
            @Parameter(description = "커서 (joinedAt)") @RequestParam(required = false) Instant cursor,
            @Parameter(description = "페이지 크기") @RequestParam(defaultValue = "20") int size
    ) {
        return RoomListResponse.from(roomService.getMyRooms(userId, cursor, size));
    }

    @Operation(summary = "방 상세 조회", description = "방 메타정보를 조회합니다. 방 멤버만 접근 가능합니다.")
    @GetMapping("/{roomId}")
    public RoomDetailResponse getDetail(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable UUID roomId
    ) {
        return RoomDetailResponse.from(roomService.getDetail(roomId, userId));
    }

    @Operation(summary = "방 수정", description = "방 제목, 여행지, 날짜를 수정합니다. HOST만 가능합니다.")
    @PatchMapping("/{roomId}")
    public RoomDetailResponse update(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable UUID roomId,
            @RequestBody @Valid UpdateRoomRequest request
    ) {
        return RoomDetailResponse.from(roomService.update(roomId, request.toCommand(), userId));
    }

    @Operation(summary = "방 삭제", description = "방을 삭제합니다 (soft delete). HOST만 가능합니다.")
    @DeleteMapping("/{roomId}")
    public ResponseEntity<Void> delete(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable UUID roomId
    ) {
        roomService.delete(roomId, userId);
        return ResponseEntity.noContent().build();
    }
}
