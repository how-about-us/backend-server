package com.howaboutus.backend.rooms.service;

import com.howaboutus.backend.common.error.CustomException;
import com.howaboutus.backend.common.error.ErrorCode;
import com.howaboutus.backend.rooms.entity.Room;
import com.howaboutus.backend.rooms.entity.RoomMember;
import com.howaboutus.backend.rooms.entity.RoomRole;
import com.howaboutus.backend.rooms.repository.RoomMemberRepository;
import com.howaboutus.backend.rooms.repository.RoomRepository;
import com.howaboutus.backend.rooms.service.dto.JoinRequestResult;
import com.howaboutus.backend.rooms.service.dto.JoinResult;
import com.howaboutus.backend.rooms.service.dto.JoinStatusResult;
import com.howaboutus.backend.user.entity.User;
import com.howaboutus.backend.user.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoomInviteService {

    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final UserRepository userRepository;
    private final InviteCodeGenerator inviteCodeGenerator;

    // 초대 코드 재생성
    @Transactional
    public String regenerateInviteCode(UUID roomId, Long userId) {
        Room room = getActiveRoom(roomId);
        getHostMember(roomId, userId);
        String newCode = inviteCodeGenerator.generate();
        room.regenerateInviteCode(newCode);
        return newCode;
    }

    // 입장 요청
    @Transactional
    public JoinResult requestJoin(String inviteCode, Long userId) {
        Room room = roomRepository.findByInviteCodeAndDeletedAtIsNull(inviteCode)
                .orElseThrow(() -> new CustomException(ErrorCode.ROOM_NOT_FOUND));

        Optional<RoomMember> existing = roomMemberRepository.findByRoom_IdAndUser_Id(room.getId(), userId);

        if (existing.isPresent()) {
            RoomMember member = existing.get();
            if (member.getRole() == RoomRole.PENDING) {
                return JoinResult.pending(room.getTitle());
            }
            return JoinResult.alreadyMember(room.getId(), room.getTitle(), member.getRole());
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        try {
            roomMemberRepository.saveAndFlush(RoomMember.of(room, user, RoomRole.PENDING));
        } catch (DataIntegrityViolationException e) {
            RoomMember member = roomMemberRepository.findByRoom_IdAndUser_Id(room.getId(), userId)
                    .orElseThrow(() -> new CustomException(ErrorCode.ROOM_NOT_FOUND));
            if (member.getRole() == RoomRole.PENDING) {
                return JoinResult.pending(room.getTitle());
            }
            return JoinResult.alreadyMember(room.getId(), room.getTitle(), member.getRole());
        }
        return JoinResult.pending(room.getTitle());
    }

    // 입장 요청 상태 조회
    public JoinStatusResult getJoinStatus(String inviteCode, Long userId) {
        Room room = roomRepository.findByInviteCodeAndDeletedAtIsNull(inviteCode)
                .orElseThrow(() -> new CustomException(ErrorCode.ROOM_NOT_FOUND));

        RoomMember member = roomMemberRepository.findByRoom_IdAndUser_Id(room.getId(), userId)
                .orElseThrow(() -> new CustomException(ErrorCode.JOIN_REQUEST_NOT_FOUND));

        if (member.getRole() == RoomRole.PENDING) {
            return JoinStatusResult.pending(room.getTitle());
        }
        return JoinStatusResult.approved(room.getId(), room.getTitle(), member.getRole());
    }

    // 대기 목록 조회
    public List<JoinRequestResult> getJoinRequests(UUID roomId, Long userId) {
        getActiveRoom(roomId);
        getHostMember(roomId, userId);

        List<RoomMember> pendingMembers = roomMemberRepository.findByRoom_IdAndRole(roomId, RoomRole.PENDING);

        return pendingMembers.stream()
                .map(m -> new JoinRequestResult(
                        m.getId(),
                        m.getUser().getId(),
                        m.getUser().getNickname(),
                        m.getUser().getProfileImageUrl(),
                        m.getJoinedAt()))
                .toList();
    }

    // 입장 승인
    @Transactional
    public void approve(UUID roomId, Long requestId, Long userId) {
        getActiveRoom(roomId);
        getHostMember(roomId, userId);

        RoomMember target = roomMemberRepository.findByIdAndRoom_Id(requestId, roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.JOIN_REQUEST_NOT_FOUND));

        if (target.getRole() != RoomRole.PENDING) {
            throw new CustomException(ErrorCode.JOIN_REQUEST_NOT_FOUND);
        }

        target.approve();
    }

    // 입장 거절
    @Transactional
    public void reject(UUID roomId, Long requestId, Long userId) {
        getActiveRoom(roomId);
        getHostMember(roomId, userId);

        RoomMember target = roomMemberRepository.findByIdAndRoom_Id(requestId, roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.JOIN_REQUEST_NOT_FOUND));

        if (target.getRole() != RoomRole.PENDING) {
            throw new CustomException(ErrorCode.JOIN_REQUEST_NOT_FOUND);
        }

        roomMemberRepository.delete(target);
    }

    // 활성 방 조회
    // 다른 메서드 호출전에, 해당 방이 존재하는지 확인하는 용도
    private Room getActiveRoom(UUID roomId) {
        return roomRepository.findByIdAndDeletedAtIsNull(roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.ROOM_NOT_FOUND));
    }

    // 활성 멤버 검증
    private RoomMember getActiveMember(UUID roomId, Long userId) {
        RoomMember member = roomMemberRepository.findByRoom_IdAndUser_Id(roomId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_ROOM_MEMBER));
        if (member.getRole() == RoomRole.PENDING) {
            throw new CustomException(ErrorCode.NOT_ROOM_MEMBER);
        }
        return member;
    }

    // 방장 검증
    private RoomMember getHostMember(UUID roomId, Long userId) {
        RoomMember member = getActiveMember(roomId, userId);
        if (member.getRole() != RoomRole.HOST) {
            throw new CustomException(ErrorCode.NOT_ROOM_HOST);
        }
        return member;
    }
}
