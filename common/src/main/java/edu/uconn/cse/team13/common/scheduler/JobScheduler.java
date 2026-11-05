package edu.uconn.cse.team13.common.scheduler;

import edu.uconn.cse.team13.common.model.JobMessage;

/**
 * Creates a Kubernetes Job for one queue delivery.
 * Implementations must be idempotent for the same {@code jobId} + {@code attempt}.
 */
public interface JobScheduler {

    /**
     * Submits the job to the cluster.
     *
     * @return the created Kubernetes Job name
     */
    String submit(JobMessage message);
}
