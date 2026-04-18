package com.howaboutus.backend.bookmarks.repository;

import com.howaboutus.backend.bookmarks.entity.Bookmark;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookmarkRepository extends JpaRepository<Bookmark, Long> {

    boolean existsByRoom_IdAndGooglePlaceId(UUID roomId, String googlePlaceId);

    @Query("SELECT new com.howaboutus.backend.bookmarks.repository.CategoryBookmarkCount(b.category.id, COUNT(b)) FROM Bookmark b WHERE b.room.id = :roomId GROUP BY b.category.id")
    List<CategoryBookmarkCount> countGroupedByCategoryId(@Param("roomId") UUID roomId);

    List<Bookmark> findAllByRoom_IdOrderByCreatedAtDesc(UUID roomId);

    List<Bookmark> findAllByRoom_IdAndCategory_IdOrderByCreatedAtDesc(UUID roomId, Long categoryId);

    Optional<Bookmark> findByIdAndRoom_Id(Long bookmarkId, UUID roomId);

    void deleteAllByCategory_Id(Long categoryId);
}
