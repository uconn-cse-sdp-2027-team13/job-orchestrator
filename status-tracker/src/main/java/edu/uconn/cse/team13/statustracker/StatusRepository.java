package edu.uconn.cse.team13.statustracker;

import edu.uconn.cse.team13.common.model.JobState;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence the status tracker needs. Separate from {@code common}'s {@code JobRepository} so
 * adding methods here cannot break the consumer's implementations and test fakes.
 *
 * <p>Every state-changing method is guarded by the job's current attempt and state, which makes
 * replays (duplicate watch events, reconciliation after restart) harmless.
 */
public interface StatusRepository {

    Optional<JobRecord> findJob(UUID jobId);

    /**
     * Appends a pod_event row unless it repeats what the last row for this pod already says.
     *
     * @return {@code true} if a row was written
     */
    boolean recordPodEventIfChanged(PodEventRecord event);

    /**
     * {@code DISPATCHED -> RUNNING} for the given attempt, setting {@code started_at} on first start.
     *
     * @return {@code true} if the transition was applied
     */
    boolean markRunning(UUID jobId, int attempt, Instant startedAt);

    /**
     * Closes an attempt in one transaction: moves the job to {@code outcome.newState()} (only from
     * DISPATCHED or RUNNING, only for {@code outcome.attempt()}) and, if that applied, appends the
     * job_attempt row.
     *
     * @return {@code true} if the transition was applied
     */
    boolean completeAttempt(AttemptOutcome outcome);

    /** Jobs currently in DISPATCHED or RUNNING, for reconciliation reporting. */
    List<ActiveAttempt> findActiveAttempts();

    /**
     * @param maxAttempts value of {@code job.max_attempts}, or {@code null} if unset
     */
    record JobRecord(UUID jobId, JobState state, int attemptCount, Integer maxAttempts) {
    }

    record ActiveAttempt(UUID jobId, int attempt, JobState state) {
    }

    record PodEventRecord(
            UUID jobId,
            String podName,
            PodEventType type,
            PodPhase phase,
            String message,
            Instant observedAt) {
    }

    /**
     * @param newState SUCCEEDED, RETRY_WAIT, or DEAD_LETTERED
     * @param lastError text for {@code job.last_error}; {@code null} leaves it unchanged
     */
    record AttemptOutcome(
            UUID jobId,
            int attempt,
            JobState newState,
            String podName,
            String nodeName,
            Instant startedAt,
            Instant endedAt,
            Integer exitCode,
            String failureReason,
            String lastError) {
    }
}
