package com.howaboutus.backend.rooms.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.howaboutus.backend.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RoomMemberTest {

    private RoomMember createMember(RoomRole role) {
        Room room = Room.create("여행", "부산", null, null, "invite1", 1L);
        return RoomMember.of(room, User.ofGoogle("g1", "test@test.com", "테스터", null), role);
    }

    @Test
    @DisplayName("promoteToHost - MEMBER를 HOST로 승격")
    void promoteToHostSuccess() {
        RoomMember member = createMember(RoomRole.MEMBER);
        member.promoteToHost();
        assertThat(member.getRole()).isEqualTo(RoomRole.HOST);
    }

    @Test
    @DisplayName("promoteToHost - MEMBER가 아니면 예외")
    void promoteToHostFailsWhenNotMember() {
        RoomMember host = createMember(RoomRole.HOST);
        assertThatThrownBy(host::promoteToHost).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("promoteToHost - PENDING이면 예외")
    void promoteToHostFailsWhenPending() {
        RoomMember pending = createMember(RoomRole.PENDING);
        assertThatThrownBy(pending::promoteToHost).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("demoteToMember - HOST를 MEMBER로 강등")
    void demoteToMemberSuccess() {
        RoomMember host = createMember(RoomRole.HOST);
        host.demoteToMember();
        assertThat(host.getRole()).isEqualTo(RoomRole.MEMBER);
    }

    @Test
    @DisplayName("demoteToMember - HOST가 아니면 예외")
    void demoteToMemberFailsWhenNotHost() {
        RoomMember member = createMember(RoomRole.MEMBER);
        assertThatThrownBy(member::demoteToMember).isInstanceOf(IllegalStateException.class);
    }
}
