package com.howaboutus.backend.rooms.service;

import com.howaboutus.backend.user.entity.User;
import com.howaboutus.backend.user.repository.UserRepository;
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
import com.howaboutus.backend.rooms.service.dto.RoomListResult.RoomSummary;
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
    private final RoomAuthorizationService roomAuthorizationService;

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
        RoomMember member = roomAuthorizationService.requireActiveMember(roomId, userId);
        long memberCount = roomMemberRepository.countByRoom_IdAndRoleIn(roomId, ACTIVE_ROLES);
        return RoomDetailResult.of(room, member.getRole(), memberCount);
    }

    @Transactional
    public RoomDetailResult update(UUID roomId, RoomUpdateCommand command, Long userId) {
        Room room = getActiveRoom(roomId);
        roomAuthorizationService.requireHost(roomId, userId);
        LocalDate effectiveStart = command.startDate() != null ? command.startDate() : room.getStartDate();
        LocalDate effectiveEnd = command.endDate() != null ? command.endDate() : room.getEndDate();
        validateDateRange(effectiveStart, effectiveEnd);
        room.update(command.title(), command.destination(), command.startDate(), command.endDate());
        long memberCount = roomMemberRepository.countByRoom_IdAndRoleIn(roomId, ACTIVE_ROLES);
        return RoomDetailResult.of(room, RoomRole.HOST, memberCount);
    }

    @Transactional
    public void delete(UUID roomId, Long userId) {
        Room room = getActiveRoom(roomId);
        roomAuthorizationService.requireHost(roomId, userId);
        roomMemberRepository.deleteByRoomId(roomId);
        roomRepository.delete(room);
    }

    public RoomListResult getMyRooms(Long userId, Instant cursor, int size) {
        if (size < 1) {
            throw new CustomException(ErrorCode.INVALID_PAGE_SIZE);
        }
        PageRequest pageable = PageRequest.of(0, size + 1);
        List<RoomMember> members;
        if (cursor == null) {
            members = roomMemberRepository
                    .findByUser_IdAndRoleInOrderByJoinedAtDesc(
                            userId, ACTIVE_ROLES, pageable);
        } else {
            members = roomMemberRepository
                    .findByUser_IdAndRoleInAndJoinedAtBeforeOrderByJoinedAtDesc(
                            userId, ACTIVE_ROLES, cursor, pageable);
        }

        boolean hasNext = members.size() > size;
        List<RoomMember> page = hasNext ? members.subList(0, size) : members;

        Instant nextCursor = null;
        if (hasNext) {
            nextCursor = page.get(page.size() - 1).getJoinedAt();
        }
        //DB에서 가져온 List<RoomMember>를 Room Summary로 바꿈.
        //m.get -> @EntityGraph로 이미 로드된, room정보를 가져와서, 정보를 조합해서 summary를 만들고, List에 추가함.
        List<RoomSummary> summaries = page.stream().map(m -> {
            Room room = m.getRoom();
            return new RoomSummary(room.getId(), room.getTitle(), room.getDestination(),
                    room.getStartDate(), room.getEndDate(),
                    m.getRole().name(), m.getJoinedAt());
        }).toList();

        return new RoomListResult(summaries, nextCursor, hasNext);
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new CustomException(ErrorCode.INVALID_DATE_RANGE);
        }
    }

    private Room getActiveRoom(UUID roomId) {
        return roomRepository.findById(roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.ROOM_NOT_FOUND));
    }

}
