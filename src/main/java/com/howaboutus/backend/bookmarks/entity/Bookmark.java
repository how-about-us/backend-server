package com.howaboutus.backend.bookmarks.entity;

import com.howaboutus.backend.common.entity.BaseTimeEntity;
import com.howaboutus.backend.rooms.entity.Room;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "bookmarks",
        uniqueConstraints = @UniqueConstraint(columnNames = {"room_id", "google_place_id"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Bookmark extends BaseTimeEntity {

    public static final String DEFAULT_CATEGORY = "ALL";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Column(name = "google_place_id", nullable = false, length = 300)
    private String googlePlaceId;

    @Column(nullable = false, length = 30)
    private String category;

    @Column(name = "added_by")
    private Long addedBy;

    private Bookmark(Room room, String googlePlaceId, String category, Long addedBy) {
        this.room = room;
        this.googlePlaceId = googlePlaceId;
        this.category = normalizeCategory(category);
        this.addedBy = addedBy;
    }

    public static Bookmark create(Room room, String googlePlaceId, String category, Long addedBy) {
        return new Bookmark(room, googlePlaceId, category, addedBy);
    }

    private static String normalizeCategory(String category) {
        if (category == null || category.isBlank()) {
            return DEFAULT_CATEGORY;
        }
        return category;
    }
}
