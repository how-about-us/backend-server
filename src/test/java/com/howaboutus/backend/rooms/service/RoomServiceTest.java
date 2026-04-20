package com.howaboutus.backend.rooms.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.howaboutus.backend.auth.entity.User;
import com.howaboutus.backend.auth.repository.UserRepository;
import com.howaboutus.backend.common.error.CustomException;
import com.howaboutus.backend.common.error.ErrorCode;
import com.howaboutus.backend.rooms.entity.Room;
import com.howaboutus.backend.rooms.entity.RoomMember;
import com.howaboutus.backend.rooms.entity.RoomRole;
import com.howaboutus.backend.rooms.repository.RoomMemberRepository;
import com.howaboutus.backend.rooms.repository.RoomRepository;
import com.howaboutus.backend.rooms.service.dto.RoomCreateCommand;
import com.howaboutus.backend.rooms.service.dto.RoomDetailResult;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RoomServiceTest {

    @Mock private RoomRepository roomRepository;
    @Mock private RoomMemberRepository roomMemberRepository;
    @Mock private UserRepository userRepository;
    @Mock private InviteCodeGenerator inviteCodeGenerator;

    private RoomService roomService;

    @BeforeEach
    void setUp() {
        roomService = new RoomService(roomRepository, roomMemberRepository,
                userRepository, inviteCodeGenerator);
    }

    @Test
    @DisplayName("방 생성 시 Room과 HOST RoomMember를 저장하고 결과를 반환한다")
    void createRoomSavesRoomAndHostMember() {
        Long userId = 1L;
        User user = User.ofGoogle("google-id", "test@test.com", "테스터", null);
        ReflectionTestUtils.setField(user, "id", userId);

        Room savedRoom = Room.create("부산 여행", "부산",
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 3),
                "aB3xK9mQ2w", userId);
        ReflectionTestUtils.setField(savedRoom, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(savedRoom, "createdAt", Instant.now());

        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(inviteCodeGenerator.generate()).willReturn("aB3xK9mQ2w");
        given(roomRepository.save(any(Room.class))).willReturn(savedRoom);

        RoomCreateCommand command = new RoomCreateCommand("부산 여행", "부산",
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 3));

        RoomDetailResult result = roomService.create(command, userId);

        assertThat(result.title()).isEqualTo("부산 여행");
        assertThat(result.inviteCode()).isEqualTo("aB3xK9mQ2w");
        assertThat(result.role()).isEqualTo(RoomRole.HOST);
        assertThat(result.memberCount()).isEqualTo(1);

        ArgumentCaptor<RoomMember> memberCaptor = ArgumentCaptor.forClass(RoomMember.class);
        verify(roomMemberRepository).save(memberCaptor.capture());
        assertThat(memberCaptor.getValue().getRole()).isEqualTo(RoomRole.HOST);
    }

    @Test
    @DisplayName("방 상세 조회 시 방 정보와 요청자 역할, 멤버 수를 반환한다")
    void getDetailReturnsRoomInfoWithRoleAndMemberCount() {
        UUID roomId = UUID.randomUUID();
        Long userId = 1L;
        Room room = Room.create("부산 여행", "부산",
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 3), "aB3xK9mQ2w", userId);
        ReflectionTestUtils.setField(room, "id", roomId);
        ReflectionTestUtils.setField(room, "createdAt", Instant.now());

        User user = User.ofGoogle("google-id", "test@test.com", "테스터", null);
        ReflectionTestUtils.setField(user, "id", userId);
        RoomMember member = RoomMember.of(room, user, RoomRole.HOST);

        given(roomRepository.findByIdAndDeletedAtIsNull(roomId)).willReturn(Optional.of(room));
        given(roomMemberRepository.findByRoom_IdAndUser_Id(roomId, userId)).willReturn(Optional.of(member));
        given(roomMemberRepository.countByRoom_IdAndRoleIn(roomId, List.of(RoomRole.HOST, RoomRole.MEMBER)))
                .willReturn(3L);

        RoomDetailResult result = roomService.getDetail(roomId, userId);

        assertThat(result.title()).isEqualTo("부산 여행");
        assertThat(result.role()).isEqualTo(RoomRole.HOST);
        assertThat(result.memberCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("삭제된 방을 조회하면 ROOM_NOT_FOUND 예외")
    void getDetailThrowsWhenRoomDeleted() {
        UUID roomId = UUID.randomUUID();
        given(roomRepository.findByIdAndDeletedAtIsNull(roomId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> roomService.getDetail(roomId, 1L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ROOM_NOT_FOUND);
    }

    @Test
    @DisplayName("방 멤버가 아닌 사용자가 상세 조회하면 NOT_ROOM_MEMBER 예외")
    void getDetailThrowsWhenNotMember() {
        UUID roomId = UUID.randomUUID();
        Room room = Room.create("부산 여행", "부산", null, null, "aB3xK9mQ2w", 1L);
        ReflectionTestUtils.setField(room, "id", roomId);

        given(roomRepository.findByIdAndDeletedAtIsNull(roomId)).willReturn(Optional.of(room));
        given(roomMemberRepository.findByRoom_IdAndUser_Id(roomId, 99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> roomService.getDetail(roomId, 99L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_ROOM_MEMBER);
    }

    @Test
    @DisplayName("PENDING 상태 사용자가 상세 조회하면 NOT_ROOM_MEMBER 예외")
    void getDetailThrowsWhenPendingMember() {
        UUID roomId = UUID.randomUUID();
        Long userId = 2L;
        Room room = Room.create("부산 여행", "부산", null, null, "aB3xK9mQ2w", 1L);
        ReflectionTestUtils.setField(room, "id", roomId);

        User user = User.ofGoogle("google-id", "pending@test.com", "대기자", null);
        ReflectionTestUtils.setField(user, "id", userId);
        RoomMember pendingMember = RoomMember.of(room, user, RoomRole.PENDING);

        given(roomRepository.findByIdAndDeletedAtIsNull(roomId)).willReturn(Optional.of(room));
        given(roomMemberRepository.findByRoom_IdAndUser_Id(roomId, userId)).willReturn(Optional.of(pendingMember));

        assertThatThrownBy(() -> roomService.getDetail(roomId, userId))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_ROOM_MEMBER);
    }

    @Test
    @DisplayName("방 생성 시 startDate > endDate면 INVALID_DATE_RANGE 예외")
    void createRoomThrowsWhenStartDateAfterEndDate() {
        RoomCreateCommand command = new RoomCreateCommand("부산 여행", "부산",
                LocalDate.of(2026, 5, 5), LocalDate.of(2026, 5, 3));

        assertThatThrownBy(() -> roomService.create(command, 1L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_DATE_RANGE);
    }
}
