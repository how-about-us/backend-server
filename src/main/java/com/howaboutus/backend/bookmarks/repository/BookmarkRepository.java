package com.howaboutus.backend.bookmarks.repository;

import com.howaboutus.backend.bookmarks.entity.Bookmark;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookmarkRepository extends JpaRepository<Bookmark, Long> {

    boolean existsByRoom_IdAndGooglePlaceId(UUID roomId, String googlePlaceId);

    List<Bookmark> findAllByRoom_IdOrderByCreatedAtDesc(UUID roomId);

    Optional<Bookmark> findByIdAndRoom_Id(Long bookmarkId, UUID roomId);
}
