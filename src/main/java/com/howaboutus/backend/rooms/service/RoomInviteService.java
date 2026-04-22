package com.howaboutus.backend.rooms.service;

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

    @Transactional
    public String regenerateInviteCode(UUID roomId, Long userId) {
        Room room = getActiveRoom(roomId);
        getHostMember(roomId, userId);
        String newCode = inviteCodeGenerator.generate();
        room.regenerateInviteCode(newCode);
        return newCode;
    }

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
        roomMemberRepository.save(RoomMember.of(room, user, RoomRole.PENDING));
        return JoinResult.pending(room.getTitle());
    }

    private Room getActiveRoom(UUID roomId) {
        return roomRepository.findByIdAndDeletedAtIsNull(roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.ROOM_NOT_FOUND));
    }

    private RoomMember getActiveMember(UUID roomId, Long userId) {
        RoomMember member = roomMemberRepository.findByRoom_IdAndUser_Id(roomId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_ROOM_MEMBER));
        if (member.getRole() == RoomRole.PENDING) {
            throw new CustomException(ErrorCode.NOT_ROOM_MEMBER);
        }
        return member;
    }

    private RoomMember getHostMember(UUID roomId, Long userId) {
        RoomMember member = getActiveMember(roomId, userId);
        if (member.getRole() != RoomRole.HOST) {
            throw new CustomException(ErrorCode.NOT_ROOM_HOST);
        }
        return member;
    }
}
