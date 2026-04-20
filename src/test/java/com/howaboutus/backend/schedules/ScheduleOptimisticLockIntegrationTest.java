package com.howaboutus.backend.schedules;

import static org.assertj.core.api.Assertions.assertThat;

import com.howaboutus.backend.rooms.entity.Room;
import com.howaboutus.backend.rooms.repository.RoomRepository;
import com.howaboutus.backend.schedules.entity.Schedule;
import com.howaboutus.backend.schedules.repository.ScheduleItemRepository;
import com.howaboutus.backend.schedules.repository.ScheduleRepository;
import com.howaboutus.backend.schedules.service.ScheduleItemService;
import com.howaboutus.backend.schedules.service.dto.ScheduleItemCreateCommand;
import com.howaboutus.backend.schedules.service.dto.ScheduleItemResult;
import com.howaboutus.backend.support.BaseIntegrationTest;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;

@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
@Import(ScheduleOptimisticLockIntegrationTest.TestConfig.class)
class ScheduleOptimisticLockIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private ScheduleRepository scheduleRepository;

    @Autowired
    private ScheduleItemRepository scheduleItemRepository;

    @Autowired
    private ScheduleItemService scheduleItemService;

    @Autowired
    private RepositoryLookupBarrier repositoryLookupBarrier;

    @AfterEach
    void tearDown() {
        scheduleItemRepository.deleteAll();
        scheduleRepository.deleteAll();
        roomRepository.deleteAll();
    }

    @Test
    @DisplayName("같은 일정에 대한 동시 쓰기 중 하나는 낙관적 락 충돌로 실패한다")
    void concurrentCreatesOnSameScheduleCauseOptimisticLockConflict() throws Exception {
        Room room = roomRepository.save(Room.create(
                "서울 여행",
                "서울",
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 3),
                "SEOUL-LOCK-1",
                1L
        ));
        Schedule schedule = scheduleRepository.saveAndFlush(Schedule.create(room, 1, LocalDate.of(2026, 5, 1)));
        Long initialVersion = scheduleRepository.findById(schedule.getId())
                .map(Schedule::getVersion)
                .orElseThrow();

        try (ExecutorService executorService = Executors.newFixedThreadPool(2)) {
            repositoryLookupBarrier.activate(new CyclicBarrier(2));
            try {
                Future<WorkerResult> firstCreate = executorService.submit(
                        createItemTask(room.getId(), schedule.getId(), "place-a", LocalTime.of(10, 0))
                );
                Future<WorkerResult> secondCreate = executorService.submit(
                        createItemTask(room.getId(), schedule.getId(), "place-b", LocalTime.of(11, 0))
                );

                List<WorkerResult> results = Arrays.asList(
                        firstCreate.get(10, TimeUnit.SECONDS),
                        secondCreate.get(10, TimeUnit.SECONDS)
                );

                List<WorkerResult> successes = results.stream()
                        .filter(WorkerResult::isSuccess)
                        .toList();
                List<WorkerResult> failures = results.stream()
                        .filter(result -> !result.isSuccess())
                        .toList();

                assertThat(successes)
                        .withFailMessage("Worker results: %s", summarize(results))
                        .hasSize(1);
                assertThat(failures)
                        .withFailMessage("Worker results: %s", summarize(results))
                        .hasSize(1);
                assertThat(isOptimisticLockFailure(failures.getFirst().error())).isTrue();
            } finally {
                repositoryLookupBarrier.deactivate();
            }
        }

        List<String> persistedPlaceIds = scheduleItemRepository.findAllBySchedule_IdOrderByOrderIndexAsc(schedule.getId())
                .stream()
                .map(scheduleItem -> scheduleItem.getGooglePlaceId())
                .toList();

        assertThat(persistedPlaceIds).hasSize(1);
        assertThat(persistedPlaceIds).containsAnyOf("place-a", "place-b");
        assertThat(scheduleRepository.findById(schedule.getId()))
                .map(Schedule::getVersion)
                .hasValueSatisfying(version -> assertThat(version).isGreaterThan(initialVersion));
    }

    private Callable<WorkerResult> createItemTask(UUID roomId, Long scheduleId, String googlePlaceId, LocalTime startTime) {
        return () -> {
            try {
                ScheduleItemResult result = scheduleItemService.create(
                        roomId,
                        scheduleId,
                        new ScheduleItemCreateCommand(googlePlaceId, startTime, 60)
                );
                return WorkerResult.success(result);
            } catch (Throwable throwable) {
                return WorkerResult.failure(throwable);
            }
        };
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

    private String summarize(List<WorkerResult> results) {
        return results.stream()
                .map(result -> result.isSuccess()
                        ? "SUCCESS:" + result.result().itemId()
                        : "FAILURE:" + result.error().getClass().getName() + ":" + result.error().getMessage())
                .toList()
                .toString();
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        RepositoryLookupBarrier repositoryLookupBarrier() {
            return new RepositoryLookupBarrier();
        }

        @Bean
        RepositoryLookupBarrierAspect repositoryLookupBarrierAspect(RepositoryLookupBarrier repositoryLookupBarrier) {
            return new RepositoryLookupBarrierAspect(repositoryLookupBarrier);
        }
    }

    static class RepositoryLookupBarrier {

        private volatile CyclicBarrier barrier;

        void activate(CyclicBarrier barrier) {
            this.barrier = barrier;
        }

        void deactivate() {
            this.barrier = null;
        }

        void awaitIfActive() {
            CyclicBarrier currentBarrier = barrier;
            if (currentBarrier != null) {
                awaitBarrier(currentBarrier);
            }
        }
    }

    @Aspect
    static class RepositoryLookupBarrierAspect {

        private final RepositoryLookupBarrier repositoryLookupBarrier;

        RepositoryLookupBarrierAspect(RepositoryLookupBarrier repositoryLookupBarrier) {
            this.repositoryLookupBarrier = repositoryLookupBarrier;
        }

        @Around("execution(* com.howaboutus.backend.schedules.repository.ScheduleRepository.findByIdAndRoom_IdWithOptimisticLock(..))")
        Object awaitConcurrentLookup(ProceedingJoinPoint joinPoint) throws Throwable {
            Object result = joinPoint.proceed();
            repositoryLookupBarrier.awaitIfActive();
            return result;
        }
    }

    private static void awaitBarrier(CyclicBarrier barrier) {
        try {
            barrier.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Concurrent test interrupted", e);
        } catch (BrokenBarrierException e) {
            throw new IllegalStateException("Concurrent test barrier broken", e);
        } catch (java.util.concurrent.TimeoutException e) {
            throw new IllegalStateException("Concurrent test barrier timed out", e);
        }
    }

    private record WorkerResult(ScheduleItemResult result, Throwable error) {

        private static WorkerResult success(ScheduleItemResult result) {
            return new WorkerResult(result, null);
        }

        private static WorkerResult failure(Throwable error) {
            return new WorkerResult(null, error);
        }

        private boolean isSuccess() {
            return error == null;
        }
    }
}
