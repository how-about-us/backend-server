package com.howaboutus.backend.rooms.repository;

import com.howaboutus.backend.rooms.entity.Room;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomRepository extends JpaRepository<Room, UUID> {

    Optional<Room> findByIdAndDeletedAtIsNull(UUID id);
}
