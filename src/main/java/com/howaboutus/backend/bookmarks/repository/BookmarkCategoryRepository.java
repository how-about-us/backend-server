package com.howaboutus.backend.bookmarks.repository;

import com.howaboutus.backend.bookmarks.entity.BookmarkCategory;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookmarkCategoryRepository extends JpaRepository<BookmarkCategory, Long> {

    boolean existsByRoom_Id(UUID roomId);

    boolean existsByRoom_IdAndName(UUID roomId, String name);

    List<BookmarkCategory> findAllByRoom_IdOrderByCreatedAtAsc(UUID roomId);

    Optional<BookmarkCategory> findByIdAndRoom_Id(Long categoryId, UUID roomId);

    @Modifying
    @Query("DELETE FROM BookmarkCategory bc WHERE bc.room.id = :roomId")
    void deleteAllByRoomId(@Param("roomId") UUID roomId);
}
