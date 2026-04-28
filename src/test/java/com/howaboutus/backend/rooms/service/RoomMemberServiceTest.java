package com.howaboutus.backend.rooms.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.howaboutus.backend.common.error.CustomException;
import com.howaboutus.backend.common.error.ErrorCode;
import com.howaboutus.backend.realtime.service.RoomPresenceService;
import com.howaboutus.backend.rooms.entity.Room;
import com.howaboutus.backend.rooms.entity.RoomMember;
import com.howaboutus.backend.rooms.entity.RoomRole;
import com.howaboutus.backend.rooms.repository.RoomMemberRepository;
import com.howaboutus.backend.rooms.service.dto.RoomMemberResult;
import com.howaboutus.backend.user.entity.User;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RoomMemberServiceTest {

    private static final UUID ROOM_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Long USER_ID = 1L;
    private static final List<RoomRole> ACTIVE_ROLES = List.of(RoomRole.HOST, RoomRole.MEMBER);

    @Mock private RoomMemberRepository roomMemberRepository;
    @Mock private RoomPresenceService roomPresenceService;
    @Mock private RoomAuthorizationService roomAuthorizationService;

    private RoomMemberService roomMemberService;

    @BeforeEach
    void setUp() {
        roomMemberService = new RoomMemberService(
                roomMemberRepository, roomPresenceService, roomAuthorizationService);
    }

    @Test
    @DisplayName("HOST와 MEMBER를 조회하고 온라인 상태를 매핑한다")
    void getMembersReturnsActiveMembers() {
        User host = User.ofGoogle("g1", "host@test.com", "호스트", "https://img/host.jpg");
        ReflectionTestUtils.setField(host, "id", 1L);
        User member = User.ofGoogle("g2", "member@test.com", "멤버", null);
        ReflectionTestUtils.setField(member, "id", 2L);

        Room room = Room.create("여행", "부산", null, null, "invite1", 1L);
        ReflectionTestUtils.setField(room, "id", ROOM_ID);

        RoomMember hostMember = RoomMember.of(room, host, RoomRole.HOST);
        RoomMember regularMember = RoomMember.of(room, member, RoomRole.MEMBER);

        given(roomAuthorizationService.requireActiveMember(ROOM_ID, USER_ID)).willReturn(hostMember);
        given(roomMemberRepository.findByRoom_IdAndRoleIn(ROOM_ID, ACTIVE_ROLES))
                .willReturn(List.of(hostMember, regularMember));
        given(roomPresenceService.getOnlineUserIds(ROOM_ID)).willReturn(Set.of(1L));

        List<RoomMemberResult> results = roomMemberService.getMembers(ROOM_ID, USER_ID);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).userId()).isEqualTo(1L);
        assertThat(results.get(0).isOnline()).isTrue();
        assertThat(results.get(0).role()).isEqualTo(RoomRole.HOST);
        assertThat(results.get(0).nickname()).isEqualTo("호스트");
        assertThat(results.get(1).userId()).isEqualTo(2L);
        assertThat(results.get(1).isOnline()).isFalse();
    }

    @Test
    @DisplayName("비멤버 접근 시 예외를 던진다")
    void getMembersThrowsForNonMember() {
        given(roomAuthorizationService.requireActiveMember(ROOM_ID, USER_ID))
                .willThrow(new CustomException(ErrorCode.NOT_ROOM_MEMBER));

        assertThatThrownBy(() -> roomMemberService.getMembers(ROOM_ID, USER_ID))
                .isInstanceOf(CustomException.class);
    }

    @Test
    @DisplayName("Redis 장애 시 모든 멤버를 offline으로 처리한다")
    void getMembersHandlesRedisFailure() {
        User host = User.ofGoogle("g1", "host@test.com", "호스트", null);
        ReflectionTestUtils.setField(host, "id", 1L);

        Room room = Room.create("여행", "부산", null, null, "invite1", 1L);
        ReflectionTestUtils.setField(room, "id", ROOM_ID);

        RoomMember hostMember = RoomMember.of(room, host, RoomRole.HOST);

        given(roomAuthorizationService.requireActiveMember(ROOM_ID, USER_ID)).willReturn(hostMember);
        given(roomMemberRepository.findByRoom_IdAndRoleIn(ROOM_ID, ACTIVE_ROLES))
                .willReturn(List.of(hostMember));
        given(roomPresenceService.getOnlineUserIds(ROOM_ID))
                .willThrow(new RuntimeException("Redis connection refused"));

        List<RoomMemberResult> results = roomMemberService.getMembers(ROOM_ID, USER_ID);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).isOnline()).isFalse();
    }

    @Test
    @DisplayName("멤버가 없으면 빈 리스트를 반환한다")
    void getMembersReturnsEmptyList() {
        RoomMember dummyMember = createDummyMember();
        given(roomAuthorizationService.requireActiveMember(ROOM_ID, USER_ID)).willReturn(dummyMember);
        given(roomMemberRepository.findByRoom_IdAndRoleIn(ROOM_ID, ACTIVE_ROLES))
                .willReturn(List.of());
        given(roomPresenceService.getOnlineUserIds(ROOM_ID)).willReturn(Set.of());

        List<RoomMemberResult> results = roomMemberService.getMembers(ROOM_ID, USER_ID);

        assertThat(results).isEmpty();
    }

    private RoomMember createDummyMember() {
        User user = User.ofGoogle("g1", "test@test.com", "테스터", null);
        ReflectionTestUtils.setField(user, "id", USER_ID);
        Room room = Room.create("여행", "부산", null, null, "invite1", USER_ID);
        ReflectionTestUtils.setField(room, "id", ROOM_ID);
        return RoomMember.of(room, user, RoomRole.HOST);
    }
}
