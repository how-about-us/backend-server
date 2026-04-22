package com.howaboutus.backend.rooms.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.howaboutus.backend.auth.filter.JwtAuthenticationFilter;
import com.howaboutus.backend.auth.service.JwtProvider;
import com.howaboutus.backend.common.config.SecurityConfig;
import com.howaboutus.backend.common.error.CustomException;
import com.howaboutus.backend.common.error.ErrorCode;
import com.howaboutus.backend.common.error.GlobalExceptionHandler;
import com.howaboutus.backend.common.security.JwtAuthenticationEntryPoint;
import com.howaboutus.backend.rooms.entity.RoomRole;
import com.howaboutus.backend.rooms.service.RoomInviteService;
import com.howaboutus.backend.rooms.service.RoomService;
import com.howaboutus.backend.rooms.service.dto.JoinRequestResult;
import com.howaboutus.backend.rooms.service.dto.JoinResult;
import com.howaboutus.backend.rooms.service.dto.JoinStatusResult;
import com.howaboutus.backend.rooms.service.dto.RoomCreateCommand;
import com.howaboutus.backend.rooms.service.dto.RoomDetailResult;
import com.howaboutus.backend.rooms.service.dto.RoomListResult;
import com.howaboutus.backend.rooms.service.dto.RoomUpdateCommand;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RoomController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtAuthenticationEntryPoint.class, GlobalExceptionHandler.class})
class RoomControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtProvider jwtProvider;

    @MockitoBean
    private RoomService roomService;

    @MockitoBean
    private RoomInviteService roomInviteService;

    private static final UUID ROOM_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Long USER_ID = 1L;
    private static final RoomDetailResult ROOM_DETAIL = new RoomDetailResult(
            ROOM_ID, "부산 여행", "부산",
            LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 3),
            "aB3xK9mQ2w", 4, RoomRole.HOST,
            Instant.parse("2026-04-20T00:00:00Z"));

    @Test
    @DisplayName("방 생성 성공 시 201을 반환한다")
    void createRoomReturns201() throws Exception {
        given(roomService.create(any(RoomCreateCommand.class), eq(USER_ID))).willReturn(ROOM_DETAIL);

        mockMvc.perform(post("/rooms")
                        .header("X-User-Id", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"부산 여행","destination":"부산","startDate":"2026-05-01","endDate":"2026-05-03"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(ROOM_ID.toString()))
                .andExpect(jsonPath("$.title").value("부산 여행"))
                .andExpect(jsonPath("$.inviteCode").value("aB3xK9mQ2w"))
                .andExpect(jsonPath("$.role").value("HOST"));
    }

    @Test
    @DisplayName("title이 없으면 방 생성 시 400을 반환한다")
    void createRoomReturns400WhenTitleMissing() throws Exception {
        mockMvc.perform(post("/rooms")
                        .header("X-User-Id", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"destination":"부산"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("방 제목은 필수입니다"));

        verifyNoInteractions(roomService);
    }

    @Test
    @DisplayName("방 상세 조회 성공 시 200을 반환한다")
    void getDetailReturns200() throws Exception {
        given(roomService.getDetail(ROOM_ID, USER_ID)).willReturn(ROOM_DETAIL);

        mockMvc.perform(get("/rooms/{roomId}", ROOM_ID)
                        .header("X-User-Id", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("부산 여행"))
                .andExpect(jsonPath("$.memberCount").value(4));
    }

    @Test
    @DisplayName("방 멤버가 아니면 상세 조회 시 403을 반환한다")
    void getDetailReturns403WhenNotMember() throws Exception {
        given(roomService.getDetail(ROOM_ID, USER_ID))
                .willThrow(new CustomException(ErrorCode.NOT_ROOM_MEMBER));

        mockMvc.perform(get("/rooms/{roomId}", ROOM_ID)
                        .header("X-User-Id", USER_ID))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_ROOM_MEMBER"));
    }

    @Test
    @DisplayName("내 방 목록 조회 성공 시 200을 반환한다")
    void getMyRoomsReturns200() throws Exception {
        RoomListResult listResult = new RoomListResult(
                List.of(new RoomListResult.RoomSummary(
                        ROOM_ID, "부산 여행", "부산",
                        LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 3),
                        "HOST", Instant.parse("2026-04-20T00:00:00Z"))),
                null, false);
        given(roomService.getMyRooms(eq(USER_ID), eq(null), eq(20))).willReturn(listResult);

        mockMvc.perform(get("/rooms")
                        .header("X-User-Id", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rooms[0].title").value("부산 여행"))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    @DisplayName("방 수정 성공 시 200을 반환한다")
    void updateRoomReturns200() throws Exception {
        given(roomService.update(eq(ROOM_ID), any(RoomUpdateCommand.class), eq(USER_ID)))
                .willReturn(ROOM_DETAIL);

        mockMvc.perform(patch("/rooms/{roomId}", ROOM_ID)
                        .header("X-User-Id", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"부산 맛집 여행"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("부산 여행"));
    }

    @Test
    @DisplayName("HOST가 아니면 방 수정 시 403을 반환한다")
    void updateRoomReturns403WhenNotHost() throws Exception {
        given(roomService.update(eq(ROOM_ID), any(RoomUpdateCommand.class), eq(USER_ID)))
                .willThrow(new CustomException(ErrorCode.NOT_ROOM_HOST));

        mockMvc.perform(patch("/rooms/{roomId}", ROOM_ID)
                        .header("X-User-Id", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"변경"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_ROOM_HOST"));
    }

    @Test
    @DisplayName("방 삭제 성공 시 204를 반환한다")
    void deleteRoomReturns204() throws Exception {
        mockMvc.perform(delete("/rooms/{roomId}", ROOM_ID)
                        .header("X-User-Id", USER_ID))
                .andExpect(status().isNoContent());

        then(roomService).should().delete(ROOM_ID, USER_ID);
    }

    @Test
    @DisplayName("초대 코드 재발급 성공 시 200을 반환한다")
    void regenerateInviteCodeReturns200() throws Exception {
        given(roomInviteService.regenerateInviteCode(ROOM_ID, USER_ID))
                .willReturn("newCode1234");

        mockMvc.perform(post("/rooms/{roomId}/invite-code", ROOM_ID)
                        .header("X-User-Id", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inviteCode").value("newCode1234"));
    }

    @Test
    @DisplayName("입장 요청 성공 시 202를 반환한다")
    void requestJoinReturns202() throws Exception {
        given(roomInviteService.requestJoin("aB3xK9mQ2w", USER_ID))
                .willReturn(JoinResult.pending("부산 여행"));

        mockMvc.perform(post("/rooms/join")
                        .header("X-User-Id", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"inviteCode":"aB3xK9mQ2w"}
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("pending"))
                .andExpect(jsonPath("$.roomTitle").value("부산 여행"));
    }

    @Test
    @DisplayName("이미 멤버인 사용자가 입장 요청하면 200을 반환한다")
    void requestJoinReturns200WhenAlreadyMember() throws Exception {
        given(roomInviteService.requestJoin("aB3xK9mQ2w", USER_ID))
                .willReturn(JoinResult.alreadyMember(ROOM_ID, "부산 여행", RoomRole.MEMBER));

        mockMvc.perform(post("/rooms/join")
                        .header("X-User-Id", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"inviteCode":"aB3xK9mQ2w"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("already_member"))
                .andExpect(jsonPath("$.role").value("MEMBER"));
    }

    @Test
    @DisplayName("입장 상태 조회 시 200을 반환한다")
    void getJoinStatusReturns200() throws Exception {
        given(roomInviteService.getJoinStatus("aB3xK9mQ2w", USER_ID))
                .willReturn(JoinStatusResult.pending("부산 여행"));

        mockMvc.perform(get("/rooms/join/status")
                        .header("X-User-Id", USER_ID)
                        .param("inviteCode", "aB3xK9mQ2w"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("pending"));
    }

    @Test
    @DisplayName("대기 요청 목록 조회 시 200을 반환한다")
    void getJoinRequestsReturns200() throws Exception {
        given(roomInviteService.getJoinRequests(ROOM_ID, USER_ID))
                .willReturn(List.of(new JoinRequestResult(
                        42L, 3L, "김철수", "http://img.png",
                        Instant.parse("2026-04-20T00:00:00Z"))));

        mockMvc.perform(get("/rooms/{roomId}/join-requests", ROOM_ID)
                        .header("X-User-Id", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requests[0].requestId").value(42))
                .andExpect(jsonPath("$.requests[0].nickname").value("김철수"));
    }

    @Test
    @DisplayName("입장 승인 성공 시 200을 반환한다")
    void approveJoinRequestReturns200() throws Exception {
        mockMvc.perform(post("/rooms/{roomId}/join-requests/{requestId}/approve", ROOM_ID, 42)
                        .header("X-User-Id", USER_ID))
                .andExpect(status().isOk());

        then(roomInviteService).should().approve(ROOM_ID, 42L, USER_ID);
    }

    @Test
    @DisplayName("입장 거절 성공 시 200을 반환한다")
    void rejectJoinRequestReturns200() throws Exception {
        mockMvc.perform(post("/rooms/{roomId}/join-requests/{requestId}/reject", ROOM_ID, 42)
                        .header("X-User-Id", USER_ID))
                .andExpect(status().isOk());

        then(roomInviteService).should().reject(ROOM_ID, 42L, USER_ID);
    }
}
