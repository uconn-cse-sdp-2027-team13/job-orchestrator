package edu.uconn.cse.team13.statustracker;

import edu.uconn.cse.team13.common.model.JobState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PodEventProcessorTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-11-08T13:00:00Z"), ZoneOffset.UTC);

    private FakeStatusRepository repo;
    private PodEventProcessor processor;
    private UUID jobId;

    @BeforeEach
    void setUp() {
        repo = new FakeStatusRepository();
        processor = new PodEventProcessor(repo, RetryPolicy.defaults(), ExitCodePolicy.defaults(), CLOCK);
        jobId = UUID.randomUUID();
    }

    @Test
    void pendingIsRecordedButDoesNotChangeJobState() {
        repo.addJob(jobId, JobState.DISPATCHED, 1, 3);

        Optional<JobState> result = processor.handle(Pods.pending(jobId, 1), PodEventType.OBSERVED);

        assertTrue(result.isEmpty());
        assertEquals(JobState.DISPATCHED, repo.jobs.get(jobId).state);
        assertEquals(1, repo.events.size());
        assertEquals(PodPhase.PENDING, repo.events.get(0).phase());
    }

    @Test
    void runningMovesDispatchedToRunningAndSetsStartedAt() {
        repo.addJob(jobId, JobState.DISPATCHED, 1, 3);

        Optional<JobState> result = processor.handle(Pods.running(jobId, 1), PodEventType.OBSERVED);

        assertEquals(Optional.of(JobState.RUNNING), result);
        assertEquals(JobState.RUNNING, repo.jobs.get(jobId).state);
        assertEquals(Pods.STARTED, repo.jobs.get(jobId).startedAt);
    }

    @Test
    void duplicateEventsAreIdempotent() {
        repo.addJob(jobId, JobState.DISPATCHED, 1, 3);

        processor.handle(Pods.running(jobId, 1), PodEventType.OBSERVED);
        Optional<JobState> second = processor.handle(Pods.running(jobId, 1), PodEventType.OBSERVED);

        assertTrue(second.isEmpty());
        assertEquals(1, repo.events.size());
    }

    @Test
    void succeededMovesRunningToSucceededAndClosesAttemptOnce() {
        repo.addJob(jobId, JobState.RUNNING, 1, 3);

        Optional<JobState> result = processor.handle(Pods.succeeded(jobId, 1), PodEventType.OBSERVED);
        Optional<JobState> replay = processor.handle(Pods.succeeded(jobId, 1), PodEventType.OBSERVED);

        assertEquals(Optional.of(JobState.SUCCEEDED), result);
        assertTrue(replay.isEmpty());
        assertEquals(1, repo.attempts.size());
        assertEquals(Integer.valueOf(0), repo.attempts.get(0).exitCode());
        assertEquals(Pods.FINISHED, repo.jobs.get(jobId).finishedAt);
    }

    @Test
    void succeededDirectlyFromDispatchedWhenRunningWasMissed() {
        repo.addJob(jobId, JobState.DISPATCHED, 1, 3);

        Optional<JobState> result = processor.handle(Pods.succeeded(jobId, 1), PodEventType.OBSERVED);

        assertEquals(Optional.of(JobState.SUCCEEDED), result);
        assertEquals(Pods.STARTED, repo.jobs.get(jobId).startedAt);
    }

    @Test
    void retriableFailureWithAttemptsLeftGoesToRetryWait() {
        repo.addJob(jobId, JobState.RUNNING, 1, 3);

        Optional<JobState> result =
                processor.handle(Pods.failed(jobId, 1, 137, "OOMKilled"), PodEventType.OBSERVED);

        assertEquals(Optional.of(JobState.RETRY_WAIT), result);
        FakeStatusRepository.Job job = repo.jobs.get(jobId);
        assertEquals(JobState.RETRY_WAIT, job.state);
        assertNull(job.finishedAt);
        assertTrue(job.lastError.startsWith("OOMKilled"));
        assertEquals("OOMKilled", repo.attempts.get(0).failureReason());
        assertEquals(Integer.valueOf(137), repo.attempts.get(0).exitCode());
    }

    @Test
    void failureOnLastAttemptIsDeadLettered() {
        repo.addJob(jobId, JobState.RUNNING, 3, 3);

        Optional<JobState> result =
                processor.handle(Pods.failed(jobId, 3, 1, "Error"), PodEventType.OBSERVED);

        assertEquals(Optional.of(JobState.DEAD_LETTERED), result);
        assertNotNull(repo.jobs.get(jobId).finishedAt);
    }

    @Test
    void nonRetriableExitCodeSkipsRetries() {
        repo.addJob(jobId, JobState.RUNNING, 1, 3);

        Optional<JobState> result =
                processor.handle(Pods.failed(jobId, 1, 2, "Error"), PodEventType.OBSERVED);

        assertEquals(Optional.of(JobState.DEAD_LETTERED), result);
    }

    @Test
    void jobMaxAttemptsOverridesPolicyDefault() {
        repo.addJob(jobId, JobState.RUNNING, 1, 1);

        Optional<JobState> result =
                processor.handle(Pods.failed(jobId, 1, 1, "Error"), PodEventType.OBSERVED);

        assertEquals(Optional.of(JobState.DEAD_LETTERED), result);
    }

    @Test
    void missingMaxAttemptsFallsBackToPolicyDefault() {
        repo.addJob(jobId, JobState.RUNNING, 2, null);

        Optional<JobState> result =
                processor.handle(Pods.failed(jobId, 2, 1, "Error"), PodEventType.OBSERVED);

        assertEquals(Optional.of(JobState.RETRY_WAIT), result);
    }

    @Test
    void podOfAnOlderAttemptIsRecordedButDoesNotMoveTheJob() {
        repo.addJob(jobId, JobState.RUNNING, 2, 3);

        Optional<JobState> result =
                processor.handle(Pods.failed(jobId, 1, 1, "Error"), PodEventType.OBSERVED);

        assertTrue(result.isEmpty());
        assertEquals(JobState.RUNNING, repo.jobs.get(jobId).state);
        assertEquals(1, repo.events.size());
        assertTrue(repo.attempts.isEmpty());
    }

    @Test
    void podDeletedWhileRunningIsARetriableFailure() {
        repo.addJob(jobId, JobState.RUNNING, 1, 3);

        Optional<JobState> result = processor.handle(Pods.running(jobId, 1), PodEventType.DELETED);

        assertEquals(Optional.of(JobState.RETRY_WAIT), result);
        assertEquals("PodDeleted", repo.attempts.get(0).failureReason());
        assertNull(repo.attempts.get(0).exitCode());
    }

    @Test
    void podDeletedAfterItSucceededChangesNothing() {
        repo.addJob(jobId, JobState.SUCCEEDED, 1, 3);

        Optional<JobState> result = processor.handle(Pods.succeeded(jobId, 1), PodEventType.DELETED);

        assertTrue(result.isEmpty());
        assertEquals(JobState.SUCCEEDED, repo.jobs.get(jobId).state);
        assertTrue(repo.attempts.isEmpty());
    }

    @Test
    void settledJobsAreNotReopenedByLateEvents() {
        repo.addJob(jobId, JobState.RETRY_WAIT, 1, 3);

        assertTrue(processor.handle(Pods.running(jobId, 1), PodEventType.OBSERVED).isEmpty());
        assertEquals(JobState.RETRY_WAIT, repo.jobs.get(jobId).state);
    }

    @Test
    void unknownJobAndMissingLabelsAreIgnored() {
        assertTrue(processor.handle(Pods.running(UUID.randomUUID(), 1), PodEventType.OBSERVED).isEmpty());

        PodSnapshot unlabelled = new PodSnapshot("stray", null, null, PodPhase.RUNNING,
                null, null, null, null, null, null);
        assertTrue(processor.handle(unlabelled, PodEventType.OBSERVED).isEmpty());
        assertTrue(repo.events.isEmpty());
    }
}
