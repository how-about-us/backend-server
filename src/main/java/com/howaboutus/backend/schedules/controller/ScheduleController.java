package com.howaboutus.backend.schedules.controller;

import com.howaboutus.backend.schedules.controller.dto.CreateScheduleRequest;
import com.howaboutus.backend.schedules.controller.dto.ScheduleResponse;
import com.howaboutus.backend.schedules.service.ScheduleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@Tag(name = "Schedules", description = "일정 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/rooms/{roomId}/schedules")
public class ScheduleController {

    private final ScheduleService scheduleService;

    @Operation(
            summary = "일정 생성",
            description = "방에 일정을 생성합니다."
    )
    @PostMapping
    @SuppressWarnings("JvmTaintAnalysis")
    public ResponseEntity<ScheduleResponse> create(
            @Parameter(description = "방 ID", example = "123e4567-e89b-12d3-a456-426614174000")
            @PathVariable UUID roomId,
            @RequestBody @Valid CreateScheduleRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ScheduleResponse.from(scheduleService.create(roomId, request.toCommand())));
    }

    @Operation(
            summary = "일정 목록 조회",
            description = "방의 일정 목록을 조회합니다."
    )
    @GetMapping
    public List<ScheduleResponse> getSchedules(
            @Parameter(description = "방 ID", example = "123e4567-e89b-12d3-a456-426614174000")
            @PathVariable UUID roomId
    ) {
        return scheduleService.getSchedules(roomId).stream()
                .map(ScheduleResponse::from)
                .toList();
    }

    @Operation(
            summary = "일정 삭제",
            description = "방의 일정을 삭제합니다."
    )
    @DeleteMapping("/{scheduleId}")
    public ResponseEntity<Void> delete(
            @Parameter(description = "방 ID", example = "123e4567-e89b-12d3-a456-426614174000")
            @PathVariable UUID roomId,
            @Parameter(description = "일정 ID", example = "1")
            @PathVariable Long scheduleId
    ) {
        scheduleService.delete(roomId, scheduleId);
        return ResponseEntity.noContent().build();
    }
}
