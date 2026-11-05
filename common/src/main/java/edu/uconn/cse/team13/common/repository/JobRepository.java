package edu.uconn.cse.team13.common.repository;

import java.util.UUID;

/**
 * Authoritative job lifecycle persistence (MySQL in production).
 * Table DDL is owned by {@code platform-infra} Flyway; this module only queries it.
 */
public interface JobRepository {

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
}
