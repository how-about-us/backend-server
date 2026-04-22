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
import com.howaboutus.backend.user.entity.User;
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
    @Mock private InviteCodeGenerator inviteCodeGenerator;

    private RoomInviteService roomInviteService;

    private static final UUID ROOM_ID = UUID.randomUUID();
    private static final Long HOST_ID = 1L;
    private static final Long MEMBER_ID = 2L;

    private Room room;
    private RoomMember hostMember;
    private RoomMember regularMember;

    @BeforeEach
    void setUp() {
        roomInviteService = new RoomInviteService(
                roomRepository, roomMemberRepository, inviteCodeGenerator);

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
}
