package com.howaboutus.backend.schedules.repository;

import com.howaboutus.backend.schedules.entity.Schedule;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduleRepository extends JpaRepository<Schedule, Long> {

    boolean existsByRoom_IdAndDayNumber(UUID roomId, int dayNumber);

    boolean existsByRoom_IdAndDate(UUID roomId, LocalDate date);

    List<Schedule> findAllByRoom_IdOrderByDayNumberAsc(UUID roomId);

    Optional<Schedule> findByIdAndRoom_Id(Long scheduleId, UUID roomId);
}
