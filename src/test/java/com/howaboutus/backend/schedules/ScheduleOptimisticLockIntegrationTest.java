package com.howaboutus.backend.schedules;

import static org.assertj.core.api.Assertions.assertThat;

import com.howaboutus.backend.rooms.entity.Room;
import com.howaboutus.backend.rooms.repository.RoomRepository;
import com.howaboutus.backend.schedules.entity.Schedule;
import com.howaboutus.backend.schedules.entity.ScheduleItem;
import com.howaboutus.backend.schedules.repository.ScheduleItemRepository;
import com.howaboutus.backend.schedules.repository.ScheduleRepository;
import com.howaboutus.backend.support.BaseIntegrationTest;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
class ScheduleOptimisticLockIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private ScheduleRepository scheduleRepository;

    @Autowired
    private ScheduleItemRepository scheduleItemRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @AfterEach
    void tearDown() {
        scheduleItemRepository.deleteAll();
        scheduleRepository.deleteAll();
        roomRepository.deleteAll();
    }

    @Test
    @DisplayName("같은 일정에 대한 동시 쓰기 중 하나는 낙관적 락 충돌로 실패한다")
    void concurrentWritesOnSameScheduleCauseOptimisticLockConflict() throws Exception {
        Room room = roomRepository.save(Room.create(
                "서울 여행",
                "서울",
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 3),
                "SEOUL-LOCK-1",
                1L
        ));
        Schedule schedule = scheduleRepository.saveAndFlush(Schedule.create(room, 1, LocalDate.of(2026, 5, 1)));

        CountDownLatch loadedLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);

        try (ExecutorService executorService = Executors.newFixedThreadPool(2)) {
            Future<Throwable> firstWrite = executorService.submit(
                    concurrentWriteTask(room.getId(), schedule.getId(), loadedLatch, startLatch, "place-a", 0)
            );
            Future<Throwable> secondWrite = executorService.submit(
                    concurrentWriteTask(room.getId(), schedule.getId(), loadedLatch, startLatch, "place-b", 1)
            );

            assertThat(loadedLatch.await(5, TimeUnit.SECONDS)).isTrue();
            startLatch.countDown();

            List<Throwable> failures = Arrays.asList(firstWrite.get(5, TimeUnit.SECONDS), secondWrite.get(5, TimeUnit.SECONDS))
                    .stream()
                    .filter(Objects::nonNull)
                    .toList();

            assertThat(failures).hasSize(1);
            assertThat(isOptimisticLockFailure(failures.getFirst())).isTrue();
        }

        assertThat(scheduleItemRepository.findAllBySchedule_IdOrderByOrderIndexAsc(schedule.getId())).hasSize(1);
        assertThat(scheduleRepository.findById(schedule.getId()))
                .map(Schedule::getVersion)
                .hasValue(1L);
    }

    private Callable<Throwable> concurrentWriteTask(
            java.util.UUID roomId,
            Long scheduleId,
            CountDownLatch loadedLatch,
            CountDownLatch startLatch,
            String googlePlaceId,
            int orderIndex
    ) {
        return () -> {
            TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
            transactionTemplate.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);

            try {
                transactionTemplate.executeWithoutResult(status -> {
                    Schedule lockedSchedule = scheduleRepository.findByIdAndRoom_IdWithOptimisticLock(scheduleId, roomId)
                            .orElseThrow();

                    loadedLatch.countDown();
                    await(startLatch);

                    scheduleItemRepository.saveAndFlush(ScheduleItem.create(
                            lockedSchedule,
                            googlePlaceId,
                            LocalTime.of(10 + orderIndex, 0),
                            60,
                            orderIndex
                    ));
                });
                return null;
            } catch (Throwable throwable) {
                return throwable;
            }
        };
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Concurrent test did not start in time");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Concurrent test interrupted", e);
        }
    }

    private boolean isOptimisticLockFailure(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof OptimisticLockingFailureException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
