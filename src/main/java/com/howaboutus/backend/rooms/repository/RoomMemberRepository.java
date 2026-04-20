package com.howaboutus.backend.rooms.repository;

import com.howaboutus.backend.rooms.entity.RoomMember;
import com.howaboutus.backend.rooms.entity.RoomRole;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoomMemberRepository extends JpaRepository<RoomMember, Long> {

    Optional<RoomMember> findByRoom_IdAndUser_Id(UUID roomId, Long userId);

    @Query("""
           SELECT rm FROM RoomMember rm
           JOIN FETCH rm.room r
           WHERE rm.user.id = :userId
             AND rm.role IN (:roles)
             AND r.deletedAt IS NULL
             AND (:cursor IS NULL OR rm.joinedAt < :cursor)
           ORDER BY rm.joinedAt DESC
           """)
    List<RoomMember> findMyRooms(
            @Param("userId") Long userId,
            @Param("roles") List<RoomRole> roles,
            @Param("cursor") Instant cursor,
            Pageable pageable);

    long countByRoom_IdAndRoleIn(UUID roomId, List<RoomRole> roles);
}
