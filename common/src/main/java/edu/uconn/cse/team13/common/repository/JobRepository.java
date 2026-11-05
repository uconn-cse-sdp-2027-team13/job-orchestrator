package edu.uconn.cse.team13.common.repository;

import java.util.UUID;

/**
 * Authoritative job lifecycle persistence (MySQL in production).
 * Only the dispatch transition needed by the consumer is declared here for now.
 */
public interface JobRepository {

    /**
     * Records {@code QUEUED -> DISPATCHED} with the created Kubernetes Job name.
     * Must be idempotent for the same {@code jobId} + {@code attempt} when already dispatched.
     */
    void markDispatched(UUID jobId, int attempt, String kubernetesJobName);
}
