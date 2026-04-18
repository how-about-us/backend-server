package com.howaboutus.backend.bookmarkcategories.entity;

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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "bookmark_categories",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"room_id", "name"}),
                @UniqueConstraint(columnNames = {"id", "room_id"})
        },
        indexes = @Index(name = "idx_bookmark_categories_room_id", columnList = "room_id")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BookmarkCategory extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(name = "created_by")
    private Long createdBy;

    private BookmarkCategory(Room room, String name, Long createdBy) {
        this.room = room;
        this.name = name;
        this.createdBy = createdBy;
    }

    public static BookmarkCategory create(Room room, String name, Long createdBy) {
        return new BookmarkCategory(room, name, createdBy);
    }

    public void rename(String name) {
        this.name = name;
    }
}
