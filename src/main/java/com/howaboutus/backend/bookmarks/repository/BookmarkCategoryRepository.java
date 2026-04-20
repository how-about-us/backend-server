package com.howaboutus.backend.bookmarks.repository;

import com.howaboutus.backend.bookmarks.entity.BookmarkCategory;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookmarkCategoryRepository extends JpaRepository<BookmarkCategory, Long> {

    boolean existsByRoom_Id(UUID roomId);

    boolean existsByRoom_IdAndName(UUID roomId, String name);

    List<BookmarkCategory> findAllByRoom_IdOrderByCreatedAtAsc(UUID roomId);

    Optional<BookmarkCategory> findByIdAndRoom_Id(Long categoryId, UUID roomId);
}
