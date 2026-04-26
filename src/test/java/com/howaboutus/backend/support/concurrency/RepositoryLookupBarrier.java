package com.howaboutus.backend.support.concurrency;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;

public class RepositoryLookupBarrier {

    private volatile CyclicBarrier barrier;

    public void activate(CyclicBarrier barrier) {
        this.barrier = barrier;
    }

    public void deactivate() {
        this.barrier = null;
    }

    public void awaitIfActive() {
        CyclicBarrier currentBarrier = barrier;
        if (currentBarrier != null) {
            awaitBarrier(currentBarrier);
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
}
