package com.laexclusiva.service;

import com.laexclusiva.repository.InMemoryUserRepository;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class InMemoryUserAdministrationBoundaryTest {
    @Test
    void rejectsMissingRepositoryAndOperation() {
        assertThrows(IllegalArgumentException.class, () -> new InMemoryUserAdministrationBoundary(null));
        var boundary = new InMemoryUserAdministrationBoundary(new InMemoryUserRepository());
        assertThrows(IllegalArgumentException.class, () -> boundary.execute(null));
    }

    @Test
    void holdsRepositoryMonitorAndReturnsResult() {
        var users = new InMemoryUserRepository();
        var boundary = new InMemoryUserAdministrationBoundary(users);
        assertEquals("result", boundary.execute(() -> {
            assertTrue(Thread.holdsLock(users));
            assertTrue(users.findAll().isEmpty());
            return "result";
        }));
    }

    @Test
    void permitsNestedOperationsOnTheSameRepository() {
        var users = new InMemoryUserRepository();
        var first = new InMemoryUserAdministrationBoundary(users);
        var second = new InMemoryUserAdministrationBoundary(users);
        assertEquals(7, first.execute(() -> second.execute(() -> 7)));
    }

    @Test
    void propagatesFailureAndReleasesMonitor() throws Exception {
        var boundary = new InMemoryUserAdministrationBoundary(new InMemoryUserRepository());
        var failure = new IllegalStateException("expected");
        assertSame(failure, assertThrows(IllegalStateException.class, () -> boundary.execute(() -> { throw failure; })));
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            assertEquals(9, executor.submit(() -> boundary.execute(() -> 9)).get(5, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void serializesSectionsAcrossTheSameOrDifferentBoundaryInstances(boolean distinctBoundary) throws Exception {
        var users = new InMemoryUserRepository();
        var first = new InMemoryUserAdministrationBoundary(users);
        var second = distinctBoundary ? new InMemoryUserAdministrationBoundary(users) : first;
        CountDownLatch holding = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch attempting = new CountDownLatch(1);
        AtomicReference<Thread> contender = new AtomicReference<>();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> owner = executor.submit(() -> first.execute(() -> {
                assertTrue(Thread.holdsLock(users));
                holding.countDown();
                await(release);
                return 1;
            }));
            assertTrue(holding.await(5, TimeUnit.SECONDS));
            Future<Integer> waiter = executor.submit(() -> {
                contender.set(Thread.currentThread());
                attempting.countDown();
                return second.execute(() -> {
                    assertTrue(Thread.holdsLock(users));
                    assertEquals(0, release.getCount());
                    return 2;
                });
            });
            assertTrue(attempting.await(5, TimeUnit.SECONDS));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            boolean blockedOnRepository = false;
            while (System.nanoTime() < deadline) {
                ThreadInfo info = ManagementFactory.getThreadMXBean().getThreadInfo(contender.get().getId());
                if (info != null && info.getThreadState() == Thread.State.BLOCKED
                        && info.getLockInfo() != null
                        && info.getLockInfo().getIdentityHashCode() == System.identityHashCode(users)) {
                    blockedOnRepository = true;
                    break;
                }
                Thread.onSpinWait();
            }
            assertTrue(blockedOnRepository, "La segunda operación debe esperar en el monitor del repositorio.");
            assertFalse(waiter.isDone());
            release.countDown();
            assertEquals(1, owner.get(5, TimeUnit.SECONDS));
            assertEquals(2, waiter.get(5, TimeUnit.SECONDS));
        } finally {
            release.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(10, TimeUnit.SECONDS), "Tiempo de espera agotado.");
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Espera interrumpida.", failure);
        }
    }
}
