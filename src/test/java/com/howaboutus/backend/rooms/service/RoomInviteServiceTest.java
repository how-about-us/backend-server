package com.howaboutus.backend.rooms.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.howaboutus.backend.common.error.CustomException;
import com.howaboutus.backend.common.error.ErrorCode;
import com.howaboutus.backend.rooms.entity.Room;
import com.howaboutus.backend.rooms.entity.RoomMember;
import com.howaboutus.backend.rooms.entity.RoomRole;
import com.howaboutus.backend.rooms.repository.RoomMemberRepository;
import com.howaboutus.backend.rooms.repository.RoomRepository;
import com.howaboutus.backend.rooms.service.dto.JoinResult;
import com.howaboutus.backend.user.entity.User;
import com.howaboutus.backend.user.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RoomInviteServiceTest {

    @Mock private RoomRepository roomRepository;
    @Mock private RoomMemberRepository roomMemberRepository;
    @Mock private UserRepository userRepository;
    @Mock private InviteCodeGenerator inviteCodeGenerator;

    private RoomInviteService roomInviteService;

    private static final UUID ROOM_ID = UUID.randomUUID();
    private static final Long HOST_ID = 1L;
    private static final Long MEMBER_ID = 2L;
    private static final Long STRANGER_ID = 99L;

    private Room room;
    private RoomMember hostMember;
    private RoomMember regularMember;

    @BeforeEach
    void setUp() {
        roomInviteService = new RoomInviteService(
                roomRepository, roomMemberRepository, userRepository, inviteCodeGenerator);

        room = Room.create("부산 여행", "부산", null, null, "oldCode123", HOST_ID);
        ReflectionTestUtils.setField(room, "id", ROOM_ID);

        User hostUser = User.ofGoogle("google-host", "host@test.com", "호스트", null);
        ReflectionTestUtils.setField(hostUser, "id", HOST_ID);

        User memberUser = User.ofGoogle("google-member", "member@test.com", "멤버", null);
        ReflectionTestUtils.setField(memberUser, "id", MEMBER_ID);

        hostMember = RoomMember.of(room, hostUser, RoomRole.HOST);
        regularMember = RoomMember.of(room, memberUser, RoomRole.MEMBER);
    }

    @Test
    @DisplayName("HOST가 초대 코드를 재발급하면 새 코드를 반환한다")
    void regenerateInviteCodeSuccess() {
        given(roomRepository.findByIdAndDeletedAtIsNull(ROOM_ID)).willReturn(Optional.of(room));
        given(roomMemberRepository.findByRoom_IdAndUser_Id(ROOM_ID, HOST_ID))
                .willReturn(Optional.of(hostMember));
        given(inviteCodeGenerator.generate()).willReturn("newCode9876");

        String result = roomInviteService.regenerateInviteCode(ROOM_ID, HOST_ID);

        assertThat(result).isEqualTo("newCode9876");
        assertThat(room.getInviteCode()).isEqualTo("newCode9876");
    }

    @Test
    @DisplayName("MEMBER가 초대 코드 재발급하면 NOT_ROOM_HOST 예외")
    void regenerateInviteCodeThrowsWhenNotHost() {
        given(roomRepository.findByIdAndDeletedAtIsNull(ROOM_ID)).willReturn(Optional.of(room));
        given(roomMemberRepository.findByRoom_IdAndUser_Id(ROOM_ID, MEMBER_ID))
                .willReturn(Optional.of(regularMember));

        assertThatThrownBy(() -> roomInviteService.regenerateInviteCode(ROOM_ID, MEMBER_ID))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_ROOM_HOST);
    }

    @Test
    @DisplayName("초대 코드로 입장 요청 시 PENDING 멤버로 등록한다")
    void requestJoinCreatesPendingMember() {
        given(roomRepository.findByInviteCodeAndDeletedAtIsNull("aB3xK9mQ2w"))
                .willReturn(Optional.of(room));
        given(roomMemberRepository.findByRoom_IdAndUser_Id(ROOM_ID, STRANGER_ID))
                .willReturn(Optional.empty());
        User stranger = User.ofGoogle("google-stranger", "stranger@test.com", "낯선이", null);
        ReflectionTestUtils.setField(stranger, "id", STRANGER_ID);
        given(userRepository.findById(STRANGER_ID)).willReturn(Optional.of(stranger));

        JoinResult result = roomInviteService.requestJoin("aB3xK9mQ2w", STRANGER_ID);

        assertThat(result.status()).isEqualTo("pending");
        assertThat(result.roomTitle()).isEqualTo("부산 여행");
    }

    @Test
    @DisplayName("이미 MEMBER인 사용자가 입장 요청하면 already_member를 반환한다")
    void requestJoinReturnsAlreadyMemberWhenMember() {
        given(roomRepository.findByInviteCodeAndDeletedAtIsNull("aB3xK9mQ2w"))
                .willReturn(Optional.of(room));
        given(roomMemberRepository.findByRoom_IdAndUser_Id(ROOM_ID, MEMBER_ID))
                .willReturn(Optional.of(regularMember));

        JoinResult result = roomInviteService.requestJoin("aB3xK9mQ2w", MEMBER_ID);

        assertThat(result.status()).isEqualTo("already_member");
        assertThat(result.roomId()).isEqualTo(ROOM_ID);
        assertThat(result.role()).isEqualTo(RoomRole.MEMBER);
    }

    @Test
    @DisplayName("이미 PENDING인 사용자가 입장 요청하면 pending을 반환한다")
    void requestJoinReturnsPendingWhenAlreadyPending() {
        User pendingUser = User.ofGoogle("google-pending", "pending@test.com", "대기자", null);
        ReflectionTestUtils.setField(pendingUser, "id", 3L);
        RoomMember pendingMember = RoomMember.of(room, pendingUser, RoomRole.PENDING);

        given(roomRepository.findByInviteCodeAndDeletedAtIsNull("aB3xK9mQ2w"))
                .willReturn(Optional.of(room));
        given(roomMemberRepository.findByRoom_IdAndUser_Id(ROOM_ID, 3L))
                .willReturn(Optional.of(pendingMember));

        JoinResult result = roomInviteService.requestJoin("aB3xK9mQ2w", 3L);

        assertThat(result.status()).isEqualTo("pending");
        assertThat(result.roomTitle()).isEqualTo("부산 여행");
    }

    @Test
    @DisplayName("존재하지 않는 초대 코드로 입장 요청하면 ROOM_NOT_FOUND 예외")
    void requestJoinThrowsWhenInvalidCode() {
        given(roomRepository.findByInviteCodeAndDeletedAtIsNull("invalidCode"))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> roomInviteService.requestJoin("invalidCode", STRANGER_ID))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ROOM_NOT_FOUND);
    }
}
