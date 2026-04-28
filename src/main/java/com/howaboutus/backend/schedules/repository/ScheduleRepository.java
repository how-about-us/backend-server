package com.howaboutus.backend.schedules.repository;

import com.howaboutus.backend.schedules.entity.Schedule;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScheduleRepository extends JpaRepository<Schedule, Long> {

    boolean existsByRoom_IdAndDayNumber(UUID roomId, int dayNumber);

    boolean existsByRoom_IdAndDate(UUID roomId, LocalDate date);

    List<Schedule> findAllByRoom_IdOrderByDayNumberAsc(UUID roomId);

    Optional<Schedule> findByIdAndRoom_Id(Long scheduleId, UUID roomId);

    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query("""
            update Schedule schedule
            set schedule.version = schedule.version + 1
            where schedule.id = :scheduleId
              and schedule.room.id = :roomId
              and schedule.version = :version
            """)
    int incrementVersionIfCurrent(@Param("scheduleId") Long scheduleId,
                                  @Param("roomId") UUID roomId,
                                  @Param("version") Long version);

}
