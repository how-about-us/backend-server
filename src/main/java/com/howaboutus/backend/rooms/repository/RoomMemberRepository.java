package com.howaboutus.backend.rooms.repository;

import com.howaboutus.backend.rooms.entity.RoomMember;
import com.howaboutus.backend.rooms.entity.RoomRole;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomMemberRepository extends JpaRepository<RoomMember, Long> {

    Optional<RoomMember> findByRoom_IdAndUser_Id(UUID roomId, Long userId);

    List<RoomMember> findByUser_IdAndRoleInAndRoom_DeletedAtIsNullOrderByJoinedAtDesc(
            Long userId, List<RoomRole> roles, Pageable pageable);

    List<RoomMember> findByUser_IdAndRoleInAndRoom_DeletedAtIsNullAndJoinedAtBeforeOrderByJoinedAtDesc(
            Long userId, List<RoomRole> roles, Instant cursor, Pageable pageable);

    long countByRoom_IdAndRoleIn(UUID roomId, List<RoomRole> roles);
}
