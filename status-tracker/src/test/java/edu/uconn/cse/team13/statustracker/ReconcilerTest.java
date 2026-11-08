package edu.uconn.cse.team13.statustracker;

import edu.uconn.cse.team13.common.model.JobState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReconcilerTest {

    private FakeStatusRepository repo;
    private StubPodSource source;
    private Reconciler reconciler;

    @BeforeEach
    void setUp() {
        repo = new FakeStatusRepository();
        source = new StubPodSource();
        PodEventProcessor processor = new PodEventProcessor(
                repo, RetryPolicy.defaults(), ExitCodePolicy.defaults(),
                Clock.fixed(Instant.parse("2026-11-08T13:00:00Z"), ZoneOffset.UTC));
        reconciler = new Reconciler(source, processor, repo);
    }

    @Test
    void repairsJobsWhoseTransitionsWereMissedWhileTrackerWasDown() {
        UUID finished = UUID.randomUUID();
        UUID crashed = UUID.randomUUID();
        UUID started = UUID.randomUUID();
        repo.addJob(finished, JobState.RUNNING, 1, 3);
        repo.addJob(crashed, JobState.RUNNING, 1, 3);
        repo.addJob(started, JobState.DISPATCHED, 1, 3);
        source.pods = List.of(
                Pods.succeeded(finished, 1),
                Pods.failed(crashed, 1, 137, "OOMKilled"),
                Pods.running(started, 1));
        source.resourceVersion = "42";

        Reconciler.Result result = reconciler.reconcile();

        assertEquals(JobState.SUCCEEDED, repo.jobs.get(finished).state);
        assertEquals(JobState.RETRY_WAIT, repo.jobs.get(crashed).state);
        assertEquals(JobState.RUNNING, repo.jobs.get(started).state);
        assertEquals("42", result.resourceVersion());
        assertEquals(3, result.podsSeen());
        assertEquals(3, result.transitions());
        assertTrue(repo.events.stream().allMatch(e -> e.type() == PodEventType.RECONCILED));
    }

    @Test
    void reconcilingTwiceChangesNothingTheSecondTime() {
        UUID job = UUID.randomUUID();
        repo.addJob(job, JobState.RUNNING, 1, 3);
        source.pods = List.of(Pods.succeeded(job, 1));

        reconciler.reconcile();
        int events = repo.events.size();
        Reconciler.Result second = reconciler.reconcile();

        assertEquals(0, second.transitions());
        assertEquals(events, repo.events.size());
        assertEquals(1, repo.attempts.size());
    }

    @Test
    void reportsActiveJobsThatHaveNoPodWithoutChangingThem() {
        UUID orphan = UUID.randomUUID();
        UUID healthy = UUID.randomUUID();
        repo.addJob(orphan, JobState.RUNNING, 1, 3);
        repo.addJob(healthy, JobState.RUNNING, 1, 3);
        source.pods = List.of(Pods.running(healthy, 1));

        Reconciler.Result result = reconciler.reconcile();

        assertEquals(1, result.activeWithoutPod().size());
        assertEquals(orphan, result.activeWithoutPod().get(0).jobId());
        assertEquals(JobState.RUNNING, repo.jobs.get(orphan).state);
    }

    @Test
    void jobsClosedDuringReconcileAreNotReportedAsOrphans() {
        UUID job = UUID.randomUUID();
        repo.addJob(job, JobState.RUNNING, 1, 3);
        source.pods = List.of(Pods.succeeded(job, 1));

        assertTrue(reconciler.reconcile().activeWithoutPod().isEmpty());
    }

    /** Fixed listing; watch is not used by the reconciler. */
    static final class StubPodSource implements PodSource {
        List<PodSnapshot> pods = List.of();
        String resourceVersion = "1";

        @Override
        public PodListing listPods() {
            return new PodListing(pods, resourceVersion);
        }

        @Override
        public PodWatch watch(String resourceVersion, PodWatchListener listener) {
            throw new UnsupportedOperationException();
        }
    }
}
