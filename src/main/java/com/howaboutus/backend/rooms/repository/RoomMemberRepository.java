package com.howaboutus.backend.rooms.repository;

import com.howaboutus.backend.rooms.entity.RoomMember;
import com.howaboutus.backend.rooms.entity.RoomRole;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomMemberRepository extends JpaRepository<RoomMember, Long> {

    Optional<RoomMember> findByRoom_IdAndUser_Id(UUID roomId, Long userId);

    @EntityGraph(attributePaths = "user")
    List<RoomMember> findByRoom_IdAndRole(UUID roomId, RoomRole role);

    Optional<RoomMember> findByIdAndRoom_Id(Long id, UUID roomId);

    @EntityGraph(attributePaths = "room")
    List<RoomMember> findByUser_IdAndRoleInOrderByJoinedAtDesc(
            Long userId, List<RoomRole> roles, Pageable pageable);

    @EntityGraph(attributePaths = "room")
    List<RoomMember> findByUser_IdAndRoleInAndJoinedAtBeforeOrderByJoinedAtDesc(
            Long userId, List<RoomRole> roles, Instant cursor, Pageable pageable);

    long countByRoom_IdAndRoleIn(UUID roomId, List<RoomRole> roles);
}
