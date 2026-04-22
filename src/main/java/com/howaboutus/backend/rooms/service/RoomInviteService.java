package com.howaboutus.backend.rooms.service;

import com.howaboutus.backend.common.error.CustomException;
import com.howaboutus.backend.common.error.ErrorCode;
import com.howaboutus.backend.rooms.entity.Room;
import com.howaboutus.backend.rooms.entity.RoomMember;
import com.howaboutus.backend.rooms.entity.RoomRole;
import com.howaboutus.backend.rooms.repository.RoomMemberRepository;
import com.howaboutus.backend.rooms.repository.RoomRepository;
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
    private final InviteCodeGenerator inviteCodeGenerator;

    @Transactional
    public String regenerateInviteCode(UUID roomId, Long userId) {
        Room room = getActiveRoom(roomId);
        getHostMember(roomId, userId);
        String newCode = inviteCodeGenerator.generate();
        room.regenerateInviteCode(newCode);
        return newCode;
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
