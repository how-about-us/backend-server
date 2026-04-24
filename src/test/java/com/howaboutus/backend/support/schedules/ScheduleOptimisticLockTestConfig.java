package com.howaboutus.backend.support.schedules;

import com.howaboutus.backend.support.concurrency.RepositoryLookupBarrier;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration
public class ScheduleOptimisticLockTestConfig {

    @Bean
    RepositoryLookupBarrier repositoryLookupBarrier() {
        return new RepositoryLookupBarrier();
    }

    @Bean
    ScheduleRepositoryLookupBarrierAspect scheduleRepositoryLookupBarrierAspect(
            RepositoryLookupBarrier repositoryLookupBarrier
    ) {
        return new ScheduleRepositoryLookupBarrierAspect(repositoryLookupBarrier);
    }

    @Aspect
    static class ScheduleRepositoryLookupBarrierAspect {

        private final RepositoryLookupBarrier repositoryLookupBarrier;

        ScheduleRepositoryLookupBarrierAspect(RepositoryLookupBarrier repositoryLookupBarrier) {
            this.repositoryLookupBarrier = repositoryLookupBarrier;
        }

        @Around("execution(* com.howaboutus.backend.schedules.repository.ScheduleRepository.findByIdAndRoom_IdWithOptimisticLock(..))")
        Object awaitConcurrentLookup(ProceedingJoinPoint joinPoint) throws Throwable {
            Object result = joinPoint.proceed();
            repositoryLookupBarrier.awaitIfActive();
            return result;
        }
    }
}
