package com.howaboutus.backend.rooms.service;

import com.howaboutus.backend.auth.entity.User;
import com.howaboutus.backend.auth.repository.UserRepository;
import com.howaboutus.backend.common.error.CustomException;
import com.howaboutus.backend.common.error.ErrorCode;
import com.howaboutus.backend.rooms.entity.Room;
import com.howaboutus.backend.rooms.entity.RoomMember;
import com.howaboutus.backend.rooms.entity.RoomRole;
import com.howaboutus.backend.rooms.repository.RoomMemberRepository;
import com.howaboutus.backend.rooms.repository.RoomRepository;
import com.howaboutus.backend.rooms.service.dto.RoomCreateCommand;
import com.howaboutus.backend.rooms.service.dto.RoomDetailResult;
import com.howaboutus.backend.rooms.service.dto.RoomListResult;
import com.howaboutus.backend.rooms.service.dto.RoomUpdateCommand;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoomService {

    private static final List<RoomRole> ACTIVE_ROLES = List.of(RoomRole.HOST, RoomRole.MEMBER);

    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final UserRepository userRepository;
    private final InviteCodeGenerator inviteCodeGenerator;

    @Transactional
    public RoomDetailResult create(RoomCreateCommand command, Long userId) {
        validateDateRange(command.startDate(), command.endDate());

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        String inviteCode = inviteCodeGenerator.generate();
        Room room = Room.create(command.title(), command.destination(),
                command.startDate(), command.endDate(), inviteCode, userId);
        room = roomRepository.save(room);

        RoomMember hostMember = RoomMember.of(room, user, RoomRole.HOST);
        roomMemberRepository.save(hostMember);

        return RoomDetailResult.of(room, RoomRole.HOST, 1);
    }

    public RoomDetailResult getDetail(UUID roomId, Long userId) {
        Room room = getActiveRoom(roomId);
        RoomMember member = getActiveMember(roomId, userId);
        long memberCount = roomMemberRepository.countByRoom_IdAndRoleIn(roomId, ACTIVE_ROLES);
        return RoomDetailResult.of(room, member.getRole(), memberCount);
    }

    @Transactional
    public RoomDetailResult update(UUID roomId, RoomUpdateCommand command, Long userId) {
        validateDateRange(command.startDate(), command.endDate());
        Room room = getActiveRoom(roomId);
        getHostMember(roomId, userId);
        room.update(command.title(), command.destination(), command.startDate(), command.endDate());
        long memberCount = roomMemberRepository.countByRoom_IdAndRoleIn(roomId, ACTIVE_ROLES);
        return RoomDetailResult.of(room, RoomRole.HOST, memberCount);
    }

    @Transactional
    public void delete(UUID roomId, Long userId) {
        Room room = getActiveRoom(roomId);
        getHostMember(roomId, userId);
        room.delete();
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new CustomException(ErrorCode.INVALID_DATE_RANGE);
        }
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
