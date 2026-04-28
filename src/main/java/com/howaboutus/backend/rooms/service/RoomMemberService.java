package com.howaboutus.backend.rooms.service;

import com.howaboutus.backend.realtime.service.RoomPresenceService;
import com.howaboutus.backend.rooms.entity.RoomMember;
import com.howaboutus.backend.rooms.entity.RoomRole;
import com.howaboutus.backend.rooms.repository.RoomMemberRepository;
import com.howaboutus.backend.rooms.service.dto.RoomMemberResult;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoomMemberService {

    private static final List<RoomRole> ACTIVE_ROLES = List.of(RoomRole.HOST, RoomRole.MEMBER);

    private final RoomMemberRepository roomMemberRepository;
    private final RoomPresenceService roomPresenceService;
    private final RoomAuthorizationService roomAuthorizationService;

    public List<RoomMemberResult> getMembers(UUID roomId, Long userId) {
        roomAuthorizationService.requireActiveMember(roomId, userId);

        List<RoomMember> members = roomMemberRepository.findByRoom_IdAndRoleIn(roomId, ACTIVE_ROLES);
        Set<Long> onlineUserIds = getOnlineUserIdsSafe(roomId);

        return members.stream()
                .map(m -> new RoomMemberResult(
                        m.getUser().getId(),
                        m.getUser().getNickname(),
                        m.getUser().getProfileImageUrl(),
                        m.getRole(),
                        onlineUserIds.contains(m.getUser().getId()),
                        m.getJoinedAt()))
                .toList();
    }

    private Set<Long> getOnlineUserIdsSafe(UUID roomId) {
        try {
            return roomPresenceService.getOnlineUserIds(roomId);
        } catch (Exception e) {
            log.warn("Redis 접속 상태 조회 실패, 모든 멤버를 offline으로 처리: roomId={}", roomId, e);
            return Collections.emptySet();
        }
    }
}
