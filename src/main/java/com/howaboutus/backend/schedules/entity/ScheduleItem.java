package com.howaboutus.backend.schedules.entity;

import com.howaboutus.backend.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "schedule_items",
        indexes = {
                @Index(name = "idx_schedule_items_schedule_order", columnList = "schedule_id, order_index"),
                @Index(name = "idx_schedule_items_google_place_id", columnList = "google_place_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_schedule_items_schedule_order", columnNames = {"schedule_id", "order_index"})
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScheduleItem extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "schedule_id", nullable = false)
    private Schedule schedule;

    @Column(name = "google_place_id", nullable = false, length = 300)
    private String googlePlaceId;

    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Column(name = "order_index", nullable = false)
    private int orderIndex;

    @Column(name = "travel_mode", length = 20)
    private String travelMode;

    @Column(name = "distance_meters")
    private Integer distanceMeters;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    private ScheduleItem(
            Schedule schedule,
            String googlePlaceId,
            LocalTime startTime,
            Integer durationMinutes,
            int orderIndex
    ) {
        this.schedule = schedule;
        this.googlePlaceId = googlePlaceId;
        this.startTime = startTime;
        this.durationMinutes = durationMinutes;
        this.orderIndex = orderIndex;
    }

    public static ScheduleItem create(
            Schedule schedule,
            String googlePlaceId,
            LocalTime startTime,
            Integer durationMinutes,
            int orderIndex
    ) {
        return new ScheduleItem(schedule, googlePlaceId, startTime, durationMinutes, orderIndex);
    }

    public void updateTimeInfo(LocalTime startTime, Integer durationMinutes) {
        this.startTime = startTime;
        this.durationMinutes = durationMinutes;
    }

    public void changeOrderIndex(int orderIndex) {
        this.orderIndex = orderIndex;
    }
}
