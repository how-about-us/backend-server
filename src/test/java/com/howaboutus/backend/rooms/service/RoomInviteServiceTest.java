package com.howaboutus.backend.rooms.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.howaboutus.backend.common.error.CustomException;
import com.howaboutus.backend.common.error.ErrorCode;
import com.howaboutus.backend.messages.service.MessageService;
import com.howaboutus.backend.rooms.entity.Room;
import com.howaboutus.backend.rooms.entity.RoomMember;
import com.howaboutus.backend.rooms.entity.RoomRole;
import com.howaboutus.backend.rooms.repository.RoomMemberRepository;
import com.howaboutus.backend.rooms.repository.RoomRepository;
import com.howaboutus.backend.rooms.service.dto.JoinRequestResult;
import com.howaboutus.backend.rooms.service.dto.JoinResult;
import com.howaboutus.backend.rooms.service.dto.JoinStatus;
import com.howaboutus.backend.rooms.service.dto.JoinStatusResult;
import com.howaboutus.backend.user.entity.User;
import com.howaboutus.backend.user.repository.UserRepository;
import java.util.List;
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
    @Mock private MessageService messageService;
    private RoomAuthorizationService roomAuthorizationService;

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
        roomAuthorizationService = new RoomAuthorizationService(roomMemberRepository);
        roomInviteService = new RoomInviteService(
                roomRepository, roomMemberRepository, userRepository, inviteCodeGenerator, roomAuthorizationService,
                messageService);

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
        given(roomRepository.findById(ROOM_ID)).willReturn(Optional.of(room));
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
        given(roomRepository.findById(ROOM_ID)).willReturn(Optional.of(room));
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
        given(roomRepository.findByInviteCode("aB3xK9mQ2w"))
                .willReturn(Optional.of(room));
        given(roomMemberRepository.findByRoom_IdAndUser_Id(ROOM_ID, STRANGER_ID))
                .willReturn(Optional.empty());
        User stranger = User.ofGoogle("google-stranger", "stranger@test.com", "낯선이", null);
        ReflectionTestUtils.setField(stranger, "id", STRANGER_ID);
        given(userRepository.findById(STRANGER_ID)).willReturn(Optional.of(stranger));

        JoinResult result = roomInviteService.requestJoin("aB3xK9mQ2w", STRANGER_ID);

        assertThat(result.status()).isEqualTo(JoinStatus.PENDING);
        assertThat(result.roomId()).isEqualTo(ROOM_ID);
        assertThat(result.roomTitle()).isEqualTo("부산 여행");
    }

    @Test
    @DisplayName("이미 MEMBER인 사용자가 입장 요청하면 ALREADY_MEMBER를 반환한다")
    void requestJoinReturnsAlreadyMemberWhenMember() {
        given(roomRepository.findByInviteCode("aB3xK9mQ2w"))
                .willReturn(Optional.of(room));
        given(roomMemberRepository.findByRoom_IdAndUser_Id(ROOM_ID, MEMBER_ID))
                .willReturn(Optional.of(regularMember));

        JoinResult result = roomInviteService.requestJoin("aB3xK9mQ2w", MEMBER_ID);

        assertThat(result.status()).isEqualTo(JoinStatus.ALREADY_MEMBER);
        assertThat(result.roomId()).isEqualTo(ROOM_ID);
        assertThat(result.role()).isEqualTo(RoomRole.MEMBER);
    }

    @Test
    @DisplayName("이미 PENDING인 사용자가 입장 요청하면 pending을 반환한다")
    void requestJoinReturnsPendingWhenAlreadyPending() {
        User pendingUser = User.ofGoogle("google-pending", "pending@test.com", "대기자", null);
        ReflectionTestUtils.setField(pendingUser, "id", 3L);
        RoomMember pendingMember = RoomMember.of(room, pendingUser, RoomRole.PENDING);

        given(roomRepository.findByInviteCode("aB3xK9mQ2w"))
                .willReturn(Optional.of(room));
        given(roomMemberRepository.findByRoom_IdAndUser_Id(ROOM_ID, 3L))
                .willReturn(Optional.of(pendingMember));

        JoinResult result = roomInviteService.requestJoin("aB3xK9mQ2w", 3L);

        assertThat(result.status()).isEqualTo(JoinStatus.PENDING);
        assertThat(result.roomId()).isEqualTo(ROOM_ID);
        assertThat(result.roomTitle()).isEqualTo("부산 여행");
    }

    @Test
    @DisplayName("존재하지 않는 초대 코드로 입장 요청하면 ROOM_NOT_FOUND 예외")
    void requestJoinThrowsWhenInvalidCode() {
        given(roomRepository.findByInviteCode("invalidCode"))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> roomInviteService.requestJoin("invalidCode", STRANGER_ID))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ROOM_NOT_FOUND);
    }

    // --- getJoinStatus ---

    @Test
    @DisplayName("PENDING 상태 사용자가 상태 조회하면 pending을 반환한다")
    void getJoinStatusReturnsPending() {
        User pendingUser = User.ofGoogle("google-pending", "pending@test.com", "대기자", null);
        ReflectionTestUtils.setField(pendingUser, "id", 3L);
        RoomMember pendingMember = RoomMember.of(room, pendingUser, RoomRole.PENDING);

        given(roomRepository.findById(ROOM_ID))
                .willReturn(Optional.of(room));
        given(roomMemberRepository.findByRoom_IdAndUser_Id(ROOM_ID, 3L))
                .willReturn(Optional.of(pendingMember));

        JoinStatusResult result = roomInviteService.getJoinStatus(ROOM_ID, 3L);

        assertThat(result.status()).isEqualTo(JoinStatus.PENDING);
        assertThat(result.roomId()).isEqualTo(ROOM_ID);
    }

    @Test
    @DisplayName("승인된 사용자가 상태 조회하면 approved를 반환한다")
    void getJoinStatusReturnsApproved() {
        given(roomRepository.findById(ROOM_ID))
                .willReturn(Optional.of(room));
        given(roomMemberRepository.findByRoom_IdAndUser_Id(ROOM_ID, MEMBER_ID))
                .willReturn(Optional.of(regularMember));

        JoinStatusResult result = roomInviteService.getJoinStatus(ROOM_ID, MEMBER_ID);

        assertThat(result.status()).isEqualTo(JoinStatus.APPROVED);
        assertThat(result.roomId()).isEqualTo(ROOM_ID);
    }

    @Test
    @DisplayName("거절된(레코드 없는) 사용자가 상태 조회하면 JOIN_REQUEST_NOT_FOUND 예외")
    void getJoinStatusThrowsWhenRejected() {
        given(roomRepository.findById(ROOM_ID))
                .willReturn(Optional.of(room));
        given(roomMemberRepository.findByRoom_IdAndUser_Id(ROOM_ID, STRANGER_ID))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> roomInviteService.getJoinStatus(ROOM_ID, STRANGER_ID))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.JOIN_REQUEST_NOT_FOUND);
    }

    // --- approve / reject ---

    @Test
    @DisplayName("HOST가 입장 요청을 승인하면 PENDING → MEMBER로 변경된다")
    void approveChangesRoleToMember() {
        User pendingUser = User.ofGoogle("google-pending", "pending@test.com", "대기자", null);
        ReflectionTestUtils.setField(pendingUser, "id", 3L);
        RoomMember pendingMember = RoomMember.of(room, pendingUser, RoomRole.PENDING);
        ReflectionTestUtils.setField(pendingMember, "id", 42L);

        given(roomRepository.findById(ROOM_ID)).willReturn(Optional.of(room));
        given(roomMemberRepository.findByRoom_IdAndUser_Id(ROOM_ID, HOST_ID))
                .willReturn(Optional.of(hostMember));
        given(roomMemberRepository.findByIdAndRoom_Id(42L, ROOM_ID))
                .willReturn(Optional.of(pendingMember));

        roomInviteService.approve(ROOM_ID, 42L, HOST_ID);

        assertThat(pendingMember.getRole()).isEqualTo(RoomRole.MEMBER);
    }

    @Test
    @DisplayName("HOST가 입장 요청을 승인하면 멤버 입장 시스템 메시지를 저장한다")
    void approveSendsMemberJoinedSystemMessage() {
        User pendingUser = User.ofGoogle("google-pending", "pending@test.com", "대기자", "https://example.com/p.png");
        ReflectionTestUtils.setField(pendingUser, "id", 3L);
        RoomMember pendingMember = RoomMember.of(room, pendingUser, RoomRole.PENDING);
        ReflectionTestUtils.setField(pendingMember, "id", 42L);

        given(roomRepository.findByIdAndDeletedAtIsNull(ROOM_ID)).willReturn(Optional.of(room));
        given(roomMemberRepository.findByRoom_IdAndUser_Id(ROOM_ID, HOST_ID))
                .willReturn(Optional.of(hostMember));
        given(roomMemberRepository.findByIdAndRoom_Id(42L, ROOM_ID))
                .willReturn(Optional.of(pendingMember));

        roomInviteService.approve(ROOM_ID, 42L, HOST_ID);

        verify(messageService).sendMemberJoinedSystemMessage(
                ROOM_ID,
                3L,
                "대기자",
                "https://example.com/p.png"
        );
    }

    @Test
    @DisplayName("HOST가 입장 요청을 거절하면 레코드가 삭제된다")
    void rejectDeletesMember() {
        User pendingUser = User.ofGoogle("google-pending", "pending@test.com", "대기자", null);
        ReflectionTestUtils.setField(pendingUser, "id", 3L);
        RoomMember pendingMember = RoomMember.of(room, pendingUser, RoomRole.PENDING);
        ReflectionTestUtils.setField(pendingMember, "id", 42L);

        given(roomRepository.findById(ROOM_ID)).willReturn(Optional.of(room));
        given(roomMemberRepository.findByRoom_IdAndUser_Id(ROOM_ID, HOST_ID))
                .willReturn(Optional.of(hostMember));
        given(roomMemberRepository.findByIdAndRoom_Id(42L, ROOM_ID))
                .willReturn(Optional.of(pendingMember));

        roomInviteService.reject(ROOM_ID, 42L, HOST_ID);

        verify(roomMemberRepository).delete(pendingMember);
    }

    @Test
    @DisplayName("존재하지 않는 요청을 승인하면 JOIN_REQUEST_NOT_FOUND 예외")
    void approveThrowsWhenRequestNotFound() {
        given(roomRepository.findById(ROOM_ID)).willReturn(Optional.of(room));
        given(roomMemberRepository.findByRoom_IdAndUser_Id(ROOM_ID, HOST_ID))
                .willReturn(Optional.of(hostMember));
        given(roomMemberRepository.findByIdAndRoom_Id(999L, ROOM_ID))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> roomInviteService.approve(ROOM_ID, 999L, HOST_ID))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.JOIN_REQUEST_NOT_FOUND);
    }

    @Test
    @DisplayName("MEMBER가 승인을 시도하면 NOT_ROOM_HOST 예외")
    void approveThrowsWhenNotHost() {
        given(roomRepository.findById(ROOM_ID)).willReturn(Optional.of(room));
        given(roomMemberRepository.findByRoom_IdAndUser_Id(ROOM_ID, MEMBER_ID))
                .willReturn(Optional.of(regularMember));

        assertThatThrownBy(() -> roomInviteService.approve(ROOM_ID, 42L, MEMBER_ID))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_ROOM_HOST);
    }

    // --- getJoinRequests ---

    @Test
    @DisplayName("HOST가 대기 요청 목록을 조회하면 PENDING 멤버 목록을 반환한다")
    void getJoinRequestsReturnsPendingMembers() {
        User pendingUser = User.ofGoogle("google-pending", "pending@test.com", "대기자", "http://img.png");
        ReflectionTestUtils.setField(pendingUser, "id", 3L);
        RoomMember pendingMember = RoomMember.of(room, pendingUser, RoomRole.PENDING);
        ReflectionTestUtils.setField(pendingMember, "id", 42L);

        given(roomRepository.findById(ROOM_ID)).willReturn(Optional.of(room));
        given(roomMemberRepository.findByRoom_IdAndUser_Id(ROOM_ID, HOST_ID))
                .willReturn(Optional.of(hostMember));
        given(roomMemberRepository.findByRoom_IdAndRole(ROOM_ID, RoomRole.PENDING))
                .willReturn(List.of(pendingMember));

        List<JoinRequestResult> results = roomInviteService.getJoinRequests(ROOM_ID, HOST_ID);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).requestId()).isEqualTo(42L);
        assertThat(results.get(0).nickname()).isEqualTo("대기자");
    }
}
