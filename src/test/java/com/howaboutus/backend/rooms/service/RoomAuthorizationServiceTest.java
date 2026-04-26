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
import com.howaboutus.backend.user.entity.User;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RoomAuthorizationServiceTest {

    @Mock
    private RoomMemberRepository roomMemberRepository;

    @Test
    @DisplayName("HOST는 활성 방 멤버 검증을 통과한다")
    void requireActiveMemberAllowsHost() {
        UUID roomId = UUID.randomUUID();
        Long userId = 1L;
        RoomMember host = createMember(RoomRole.HOST);
        RoomAuthorizationService service = new RoomAuthorizationService(roomMemberRepository);

        given(roomMemberRepository.findByRoom_IdAndUser_Id(roomId, userId)).willReturn(Optional.of(host));

        RoomMember result = service.requireActiveMember(roomId, userId);

        assertThat(result).isSameAs(host);
    }

    @Test
    @DisplayName("MEMBER는 활성 방 멤버 검증을 통과한다")
    void requireActiveMemberAllowsMember() {
        UUID roomId = UUID.randomUUID();
        Long userId = 2L;
        RoomMember member = createMember(RoomRole.MEMBER);
        RoomAuthorizationService service = new RoomAuthorizationService(roomMemberRepository);

        given(roomMemberRepository.findByRoom_IdAndUser_Id(roomId, userId)).willReturn(Optional.of(member));

        RoomMember result = service.requireActiveMember(roomId, userId);

        assertThat(result).isSameAs(member);
    }

    @Test
    @DisplayName("PENDING 사용자는 활성 방 멤버 검증에서 거절된다")
    void requireActiveMemberRejectsPendingMember() {
        UUID roomId = UUID.randomUUID();
        Long userId = 1L;
        RoomAuthorizationService service = new RoomAuthorizationService(roomMemberRepository);

        given(roomMemberRepository.findByRoom_IdAndUser_Id(roomId, userId))
                .willReturn(Optional.of(createMember(RoomRole.PENDING)));

        assertThatThrownBy(() -> service.requireActiveMember(roomId, userId))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_ROOM_MEMBER);
    }

    @Test
    @DisplayName("MEMBER는 HOST 검증에서 거절된다")
    void requireHostRejectsMember() {
        UUID roomId = UUID.randomUUID();
        Long userId = 1L;
        RoomAuthorizationService service = new RoomAuthorizationService(roomMemberRepository);

        given(roomMemberRepository.findByRoom_IdAndUser_Id(roomId, userId))
                .willReturn(Optional.of(createMember(RoomRole.MEMBER)));

        assertThatThrownBy(() -> service.requireHost(roomId, userId))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_ROOM_HOST);
    }

    @Test
    @DisplayName("HOST는 HOST 검증을 통과한다")
    void requireHostAllowsHost() {
        UUID roomId = UUID.randomUUID();
        Long userId = 1L;
        RoomAuthorizationService service = new RoomAuthorizationService(roomMemberRepository);

        given(roomMemberRepository.findByRoom_IdAndUser_Id(roomId, userId))
                .willReturn(Optional.of(createMember(RoomRole.HOST)));

        service.requireHost(roomId, userId);
    }

    @Test
    @DisplayName("멤버가 없으면 HOST 검증에서 거절된다")
    void requireHostRejectsMissingMember() {
        UUID roomId = UUID.randomUUID();
        Long userId = 1L;
        RoomAuthorizationService service = new RoomAuthorizationService(roomMemberRepository);

        given(roomMemberRepository.findByRoom_IdAndUser_Id(roomId, userId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.requireHost(roomId, userId))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_ROOM_MEMBER);
    }

    private RoomMember createMember(RoomRole role) {
        Room room = Room.create("도쿄 여행", "도쿄", null, null, "INVITE", 1L);
        User user = User.ofGoogle("google-1", "user@example.com", "사용자", null);
        return RoomMember.of(room, user, role);
    }
}
