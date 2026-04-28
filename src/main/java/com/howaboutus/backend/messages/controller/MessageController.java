package com.howaboutus.backend.messages.controller;

import com.howaboutus.backend.messages.controller.dto.MessageResponse;
import com.howaboutus.backend.messages.service.MessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Messages", description = "채팅 메시지 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/rooms/{roomId}/messages")
public class MessageController {

    private final MessageService messageService;

    @Operation(
            summary = "메시지 목록 조회",
            description = "방 채팅 메시지를 조회합니다. afterId가 있으면 해당 메시지 이후의 메시지를 조회합니다."
    )
    @GetMapping
    public List<MessageResponse> getMessages(
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "방 ID", example = "123e4567-e89b-12d3-a456-426614174000")
            @PathVariable UUID roomId,
            @Parameter(description = "마지막으로 수신한 메시지 ID")
            @RequestParam(required = false) String afterId,
            @Parameter(description = "조회 개수", example = "50")
            @RequestParam(defaultValue = "50") int size
    ) {
        return messageService.getMessagesAfter(roomId, afterId, userId, size).stream()
                .map(MessageResponse::from)
                .toList();
    }
}
