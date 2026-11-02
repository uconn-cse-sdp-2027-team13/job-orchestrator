package edu.uconn.cse.team13.common.contract;

import edu.uconn.cse.team13.common.model.JobMessage;

/**
 * Talks to Kubernetes to start and stop workload pods.
 */
public interface ClusterClient {

    /** Creates a Kubernetes Job for this message and returns the Kubernetes Job name. */
    String submit(JobMessage message);

    void delete(String jobId);
}