package com.howaboutus.backend.schedules.entity;

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
import jakarta.persistence.Version;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "schedules",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"room_id", "day_number"}),
                @UniqueConstraint(columnNames = {"room_id", "date"})
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Schedule extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Column(name = "day_number", nullable = false)
    private int dayNumber;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Version
    private Long version;

    private Schedule(Room room, int dayNumber, LocalDate date) {
        this.room = room;
        this.dayNumber = dayNumber;
        this.date = date;
    }

    public static Schedule create(Room room, int dayNumber, LocalDate date) {
        return new Schedule(room, dayNumber, date);
    }
}
