package edu.uconn.cse.team13.common.repository;

import edu.uconn.cse.team13.common.model.JobState;

import java.util.Optional;
import java.util.UUID;

/**
 * Authoritative job lifecycle persistence (MySQL in production).
 * Table DDL is owned by {@code platform-infra} Flyway; this module only queries it.
 */
public interface JobRepository {

    /**
     * Inserts an initial job record with state {@code QUEUED} and {@code attempt_count = 0}.
     * Idempotent if the job row already exists with the same {@code jobId}.
     */
    default void createJob(UUID jobId, String fileName, String filePath) {
        throw new UnsupportedOperationException();
    }

    /**
     * Records {@code QUEUED -> DISPATCHED} with the created Kubernetes Job name.
     * Idempotent when already {@code DISPATCHED} for the same {@code jobId}, attempt, and name.
     */
    void markDispatched(UUID jobId, int attempt, String kubernetesJobName);

    /**
     * Jobs that consume concurrency slots ({@code DISPATCHED} + {@code RUNNING}).
     * Stand-in for active pods until a cluster client exists.
     */
    int countActiveJobs();

    /**
     * Returns the current state of a job, or empty if no row exists for the given {@code jobId}.
     */
    default Optional<JobState> findState(UUID jobId) {
        return Optional.empty();
    }
}

