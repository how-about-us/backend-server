package com.howaboutus.backend.bookmarks.entity;

import com.howaboutus.backend.bookmarkcategories.entity.BookmarkCategory;
import com.howaboutus.backend.common.entity.BaseTimeEntity;
import com.howaboutus.backend.rooms.entity.Room;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "bookmarks",
        uniqueConstraints = @UniqueConstraint(columnNames = {"room_id", "google_place_id"}),
        indexes = {
                @Index(name = "idx_bookmarks_room_id", columnList = "room_id"),
                @Index(name = "idx_bookmarks_category_id", columnList = "category_id"),
                @Index(name = "idx_bookmarks_google_place_id", columnList = "google_place_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Bookmark extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Column(name = "google_place_id", nullable = false, length = 300)
    private String googlePlaceId;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumns({
            @JoinColumn(name = "category_id", referencedColumnName = "id", nullable = false, insertable = false, updatable = false),
            @JoinColumn(name = "room_id", referencedColumnName = "room_id", nullable = false, insertable = false, updatable = false)
    })
    private BookmarkCategory category;

    @Column(name = "added_by")
    private Long addedBy;

    private Bookmark(Room room, String googlePlaceId, BookmarkCategory category, Long addedBy) {
        this.room = room;
        this.googlePlaceId = googlePlaceId;
        this.categoryId = category.getId();
        this.category = category;
        this.addedBy = addedBy;
    }

    public static Bookmark create(Room room, String googlePlaceId, BookmarkCategory category, Long addedBy) {
        return new Bookmark(room, googlePlaceId, category, addedBy);
    }

    public void changeCategory(BookmarkCategory category) {
        this.categoryId = category.getId();
        this.category = category;
    }

    public String getCategoryName() {
        return category.getName();
    }
}
