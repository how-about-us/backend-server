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
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    //벌크 쿼리 사용
    //jpa에서 기본 deletedByRoomId를 사용하면, select이후, delete 쿼리가 실행되어, N+1문제가 발생함.
    //아래의 쿼리 형식을 사용하면, 1번의 쿼리로 삭제가 가능함.
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM RoomMember rm WHERE rm.room.id = :roomId")
    void deleteByRoomId(@Param("roomId") UUID roomId);
}
