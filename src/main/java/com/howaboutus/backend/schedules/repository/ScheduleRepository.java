package com.howaboutus.backend.schedules.repository;

import com.howaboutus.backend.schedules.entity.Schedule;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScheduleRepository extends JpaRepository<Schedule, Long> {

    boolean existsByRoom_IdAndDayNumber(UUID roomId, int dayNumber);

    boolean existsByRoom_IdAndDate(UUID roomId, LocalDate date);

    List<Schedule> findAllByRoom_IdOrderByDayNumberAsc(UUID roomId);

    Optional<Schedule> findByIdAndRoom_Id(Long scheduleId, UUID roomId);

    @Lock(LockModeType.OPTIMISTIC_FORCE_INCREMENT)
    @Query("""
            select schedule
            from Schedule schedule
            where schedule.id = :scheduleId
              and schedule.room.id = :roomId
            """)
    Optional<Schedule> findByIdAndRoom_IdWithOptimisticLock(@Param("scheduleId") Long scheduleId,
                                                            @Param("roomId") UUID roomId);
}
