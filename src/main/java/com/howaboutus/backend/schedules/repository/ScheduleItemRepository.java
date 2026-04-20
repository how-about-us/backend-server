package com.howaboutus.backend.schedules.repository;

import com.howaboutus.backend.schedules.entity.ScheduleItem;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScheduleItemRepository extends JpaRepository<ScheduleItem, Long> {

    @Query("select max(si.orderIndex) from ScheduleItem si where si.schedule.id = :scheduleId")
    Optional<Integer> findMaxOrderIndexBySchedule_Id(@Param("scheduleId") Long scheduleId);

    List<ScheduleItem> findAllBySchedule_IdOrderByOrderIndexAsc(Long scheduleId);

    Optional<ScheduleItem> findByIdAndSchedule_Id(Long itemId, Long scheduleId);

    void deleteAllBySchedule_Id(Long scheduleId);
}
