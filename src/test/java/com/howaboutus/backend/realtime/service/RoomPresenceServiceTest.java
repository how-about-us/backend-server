package com.howaboutus.backend.realtime.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

@ExtendWith(MockitoExtension.class)
class RoomPresenceServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private SetOperations<String, String> setOperations;

    @InjectMocks
    private RoomPresenceService roomPresenceService;

    @BeforeEach
    void setUp() {
        given(redisTemplate.opsForSet()).willReturn(setOperations);
    }

    @Test
    @DisplayName("connect는 방의 connected_users와 유저별 session set에 저장한다")
    void connectStoresUserAndSession() {
        UUID roomId = UUID.randomUUID();
        given(setOperations.add("room:" + roomId + ":connected_users", "42")).willReturn(1L);

        boolean result = roomPresenceService.connect(roomId, 42L, "session-1");

        assertThat(result).isTrue();
        verify(setOperations).add("room:" + roomId + ":connected_users", "42");
        verify(setOperations).add("room:" + roomId + ":sessions:42", "session-1");
    }

    @Test
    @DisplayName("connect는 이미 접속 중인 유저이면 false를 반환한다")
    void connectReturnsFalseWhenUserAlreadyOnline() {
        UUID roomId = UUID.randomUUID();
        given(setOperations.add("room:" + roomId + ":connected_users", "42")).willReturn(0L);

        boolean result = roomPresenceService.connect(roomId, 42L, "session-1");

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("disconnect는 마지막 session이면 connected_users에서 유저를 제거한다")
    void disconnectRemovesUserWhenLastSessionEnds() {
        UUID roomId = UUID.randomUUID();
        given(setOperations.remove("room:" + roomId + ":sessions:42", "session-1")).willReturn(1L);
        given(setOperations.size("room:" + roomId + ":sessions:42")).willReturn(0L);

        boolean result = roomPresenceService.disconnect(roomId, 42L, "session-1");

        assertThat(result).isTrue();
        verify(setOperations).remove("room:" + roomId + ":sessions:42", "session-1");
        verify(setOperations).remove("room:" + roomId + ":connected_users", "42");
        verify(redisTemplate).delete("room:" + roomId + ":sessions:42");
    }

    @Test
    @DisplayName("disconnect는 남은 session이 있으면 connected_users에서 유저를 제거하지 않는다")
    void disconnectKeepsUserWhenOtherSessionsRemain() {
        UUID roomId = UUID.randomUUID();
        given(setOperations.remove("room:" + roomId + ":sessions:42", "session-1")).willReturn(1L);
        given(setOperations.size("room:" + roomId + ":sessions:42")).willReturn(1L);

        boolean result = roomPresenceService.disconnect(roomId, 42L, "session-1");

        assertThat(result).isFalse();
        verify(setOperations).remove("room:" + roomId + ":sessions:42", "session-1");
        verify(setOperations, never()).remove("room:" + roomId + ":connected_users", "42");
        verify(redisTemplate, never()).delete("room:" + roomId + ":sessions:42");
    }

    @Test
    @DisplayName("disconnect는 이미 처리된 session이면 false를 반환하고 connected_users를 건드리지 않는다")
    void disconnectIsIdempotentWhenSessionNotPresent() {
        UUID roomId = UUID.randomUUID();
        given(setOperations.remove("room:" + roomId + ":sessions:42", "session-1")).willReturn(0L);

        boolean result = roomPresenceService.disconnect(roomId, 42L, "session-1");

        assertThat(result).isFalse();
        verify(setOperations, never()).size("room:" + roomId + ":sessions:42");
        verify(setOperations, never()).remove("room:" + roomId + ":connected_users", "42");
    }

    @Test
    @DisplayName("getOnlineUserIds는 Redis connected_users 값을 Long 집합으로 변환한다")
    void getOnlineUserIds() {
        UUID roomId = UUID.randomUUID();
        given(setOperations.members("room:" + roomId + ":connected_users"))
                .willReturn(Set.of("42", "100"));

        Set<Long> result = roomPresenceService.getOnlineUserIds(roomId);

        assertThat(result).containsExactlyInAnyOrder(42L, 100L);
    }

    @Test
    @DisplayName("getOnlineUserIds는 파싱 불가 값을 건너뛴다")
    void getOnlineUserIdsSkipsUnparseableEntries() {
        UUID roomId = UUID.randomUUID();
        given(setOperations.members("room:" + roomId + ":connected_users"))
                .willReturn(Set.of("42", "not-a-number"));

        Set<Long> result = roomPresenceService.getOnlineUserIds(roomId);

        assertThat(result).containsExactly(42L);
    }
}
