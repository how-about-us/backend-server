package com.howaboutus.backend.rooms.service;

import com.howaboutus.backend.common.error.CustomException;
import com.howaboutus.backend.common.error.ErrorCode;
import com.howaboutus.backend.realtime.event.HostDelegatedEvent;
import com.howaboutus.backend.realtime.event.MemberKickedEvent;
import com.howaboutus.backend.realtime.event.MemberLeftEvent;
import com.howaboutus.backend.realtime.service.RoomPresenceService;
import com.howaboutus.backend.rooms.entity.RoomMember;
import com.howaboutus.backend.rooms.entity.RoomRole;
import com.howaboutus.backend.rooms.repository.RoomMemberRepository;
import com.howaboutus.backend.rooms.service.dto.RoomMemberResult;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.springframework.dao.DataAccessException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
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
    private final ApplicationEventPublisher eventPublisher;

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

    @Transactional
    public void kick(UUID roomId, Long targetUserId, Long hostUserId) {
        roomAuthorizationService.requireHost(roomId, hostUserId);

        RoomMember target = roomMemberRepository.findByRoom_IdAndUser_Id(roomId, targetUserId)
                .orElseThrow(() -> new CustomException(ErrorCode.ROOM_MEMBER_NOT_FOUND));

        if (target.getRole() == RoomRole.HOST) {
            throw new CustomException(ErrorCode.CANNOT_KICK_HOST);
        }
        if (target.getRole() != RoomRole.MEMBER) {
            throw new CustomException(ErrorCode.KICK_TARGET_NOT_MEMBER);
        }

        roomMemberRepository.delete(target);
        eventPublisher.publishEvent(new MemberKickedEvent(
                roomId,
                target.getUser().getId(),
                target.getUser().getNickname(),
                target.getUser().getProfileImageUrl()
        ));
    }

    @Transactional
    public void leave(UUID roomId, Long userId) {
        RoomMember member = roomAuthorizationService.requireActiveMember(roomId, userId);

        if (member.getRole() == RoomRole.HOST) {
            throw new CustomException(ErrorCode.CANNOT_LEAVE_AS_HOST);
        }

        roomMemberRepository.delete(member);
        eventPublisher.publishEvent(new MemberLeftEvent(
                roomId,
                member.getUser().getId(),
                member.getUser().getNickname(),
                member.getUser().getProfileImageUrl()
        ));
    }

    @Transactional
    public void delegateHost(UUID roomId, Long targetUserId, Long hostUserId) {
        if (hostUserId.equals(targetUserId)) {
            throw new CustomException(ErrorCode.CANNOT_DELEGATE_TO_SELF);
        }

        RoomMember hostMember = roomAuthorizationService.requireHost(roomId, hostUserId);

        RoomMember target = roomMemberRepository.findByRoom_IdAndUser_Id(roomId, targetUserId)
                .orElseThrow(() -> new CustomException(ErrorCode.ROOM_MEMBER_NOT_FOUND));

        if (target.getRole() != RoomRole.MEMBER) {
            throw new CustomException(ErrorCode.DELEGATE_TARGET_NOT_MEMBER);
        }

        hostMember.demoteToMember();
        target.promoteToHost();

        eventPublisher.publishEvent(new HostDelegatedEvent(
                roomId,
                hostMember.getUser().getId(),
                hostMember.getUser().getNickname(),
                target.getUser().getId(),
                target.getUser().getNickname()
        ));
    }

    private Set<Long> getOnlineUserIdsSafe(UUID roomId) {
        try {
            return roomPresenceService.getOnlineUserIds(roomId);
        } catch (DataAccessException e) {
            log.warn("Redis 접속 상태 조회 실패, 모든 멤버를 offline으로 처리: roomId={}", roomId, e);
            return Collections.emptySet();
        }
    }
}
