package edu.uconn.cse.team13.statustracker;

import edu.uconn.cse.team13.common.model.JobState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatusTrackerServiceTest {

    private FakeStatusRepository repo;
    private ScriptedPodSource source;
    private StatusTrackerService service;

    @BeforeEach
    void setUp() {
        repo = new FakeStatusRepository();
        source = new ScriptedPodSource();
        PodEventProcessor processor = new PodEventProcessor(
                repo, RetryPolicy.defaults(), ExitCodePolicy.defaults(),
                Clock.fixed(Instant.parse("2026-11-08T13:00:00Z"), ZoneOffset.UTC));
        service = new StatusTrackerService(
                source, processor, new Reconciler(source, processor, repo),
                Duration.ofMillis(10), Duration.ofMillis(50));
    }

    @AfterEach
    void tearDown() {
        service.close();
    }

    @Test
    void reconcilesThenAppliesLiveWatchEvents() {
        UUID job = UUID.randomUUID();
        repo.addJob(job, JobState.DISPATCHED, 1, 3);

        service.start();
        await(() -> source.listener != null);
        source.listener.onChange(new PodSource.PodChange(PodSource.ChangeType.MODIFIED, Pods.running(job, 1)));
        source.listener.onChange(new PodSource.PodChange(PodSource.ChangeType.MODIFIED, Pods.succeeded(job, 1)));

        await(() -> repo.jobs.get(job).state == JobState.SUCCEEDED);
        assertEquals(1, source.listCalls.get());
    }

    @Test
    void relistsAfterTheWatchDropsAndRepairsWhatItMissed() {
        UUID job = UUID.randomUUID();
        repo.addJob(job, JobState.RUNNING, 1, 3);

        service.start();
        await(() -> source.listener != null);
        PodSource.PodWatchListener first = source.listener;

        // The watch drops; while it is down the pod finishes, which only the re-list can see.
        source.pods = List.of(Pods.succeeded(job, 1));
        first.onClosed(new RuntimeException("connection reset"));

        await(() -> repo.jobs.get(job).state == JobState.SUCCEEDED);
        assertTrue(source.listCalls.get() >= 2);
    }

    @Test
    void relistsWhenHandlingAnEventFails() {
        UUID job = UUID.randomUUID();
        repo.addJob(job, JobState.DISPATCHED, 1, 3);

        service.start();
        await(() -> source.listener != null);

        // A database failure while handling an event drops the watch so the re-list repairs state.
        source.pods = List.of(Pods.running(job, 1));
        repo.failingJob = job;
        source.listener.onChange(new PodSource.PodChange(PodSource.ChangeType.MODIFIED, Pods.running(job, 1)));
        repo.failingJob = null;

        await(() -> repo.jobs.get(job).state == JobState.RUNNING);
        assertTrue(source.listCalls.get() >= 2);
    }

    private static void await(BooleanSupplier condition) {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("condition not met within 5 seconds");
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new AssertionError(ex);
            }
        }
    }

    /** Lists whatever {@code pods} holds and exposes the latest watch listener to the test. */
    static final class ScriptedPodSource implements PodSource {
        volatile List<PodSnapshot> pods = List.of();
        volatile PodWatchListener listener;
        final AtomicInteger listCalls = new AtomicInteger();

        @Override
        public PodListing listPods() {
            listCalls.incrementAndGet();
            listener = null;
            return new PodListing(pods, "rv-" + listCalls.get());
        }

        @Override
        public PodWatch watch(String resourceVersion, PodWatchListener l) {
            this.listener = l;
            return () -> { };
        }
    }
}
