package com.howaboutus.backend.schedules.controller;

import com.howaboutus.backend.schedules.controller.dto.CreateScheduleItemRequest;
import com.howaboutus.backend.schedules.controller.dto.ScheduleItemResponse;
import com.howaboutus.backend.schedules.controller.dto.UpdateScheduleItemRequest;
import com.howaboutus.backend.schedules.service.ScheduleItemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Schedule Items", description = "일정 항목 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/rooms/{roomId}/schedules/{scheduleId}/items")
public class ScheduleItemController {

    private final ScheduleItemService scheduleItemService;

    @Operation(
            summary = "일정 항목 생성",
            description = "일정에 장소를 추가합니다."
    )
    @PostMapping
    @SuppressWarnings("JvmTaintAnalysis")
    public ResponseEntity<ScheduleItemResponse> create(
            @Parameter(description = "방 ID", example = "123e4567-e89b-12d3-a456-426614174000")
            @PathVariable UUID roomId,
            @Parameter(description = "일정 ID", example = "1")
            @PathVariable Long scheduleId,
            @RequestBody @Valid CreateScheduleItemRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ScheduleItemResponse.from(scheduleItemService.create(roomId, scheduleId, request.toCommand())));
    }

    @Operation(
            summary = "일정 항목 목록 조회",
            description = "일정의 장소 목록을 조회합니다."
    )
    @GetMapping
    public List<ScheduleItemResponse> getItems(
            @Parameter(description = "방 ID", example = "123e4567-e89b-12d3-a456-426614174000")
            @PathVariable UUID roomId,
            @Parameter(description = "일정 ID", example = "1")
            @PathVariable Long scheduleId
    ) {
        return scheduleItemService.getItems(roomId, scheduleId).stream()
                .map(ScheduleItemResponse::from)
                .toList();
    }

    @Operation(
            summary = "일정 항목 수정",
            description = "일정 항목의 시간 정보를 수정합니다."
    )
    @PatchMapping("/{itemId}")
    public ScheduleItemResponse update(
            @Parameter(description = "방 ID", example = "123e4567-e89b-12d3-a456-426614174000")
            @PathVariable UUID roomId,
            @Parameter(description = "일정 ID", example = "1")
            @PathVariable Long scheduleId,
            @Parameter(description = "일정 항목 ID", example = "1")
            @PathVariable Long itemId,
            @RequestBody @Valid UpdateScheduleItemRequest request
    ) {
        return ScheduleItemResponse.from(
                scheduleItemService.update(roomId, scheduleId, itemId, request.toCommand())
        );
    }

    @Operation(
            summary = "일정 항목 삭제",
            description = "일정에서 장소를 삭제합니다."
    )
    @DeleteMapping("/{itemId}")
    public ResponseEntity<Void> delete(
            @Parameter(description = "방 ID", example = "123e4567-e89b-12d3-a456-426614174000")
            @PathVariable UUID roomId,
            @Parameter(description = "일정 ID", example = "1")
            @PathVariable Long scheduleId,
            @Parameter(description = "일정 항목 ID", example = "1")
            @PathVariable Long itemId
    ) {
        scheduleItemService.delete(roomId, scheduleId, itemId);
        return ResponseEntity.noContent().build();
    }
}
