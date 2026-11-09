package edu.uconn.cse.team13.statustracker;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Cluster-library-neutral view of one pod at one moment. The Kubernetes adapter builds these so
 * the state logic never touches Fabric8 types and can be tested without a cluster.
 *
 * @param podName pod name
 * @param jobId value of the job-id label, or {@code null} if missing or not a UUID
 * @param attempt value of the attempt label, or {@code null} if missing or not a positive int
 * @param phase pod phase
 * @param nodeName node the pod was scheduled to, if any
 * @param exitCode exit code of the workload container once it has terminated, else {@code null}
 * @param reason pod-level reason (for example {@code Evicted}) or container reason
 *     (for example {@code OOMKilled}), if any
 * @param message human-readable detail, if any
 * @param startedAt when the workload container started, if known
 * @param finishedAt when the workload container finished, if known
 */
public record PodSnapshot(
        String podName,
        UUID jobId,
        Integer attempt,
        PodPhase phase,
        String nodeName,
        Integer exitCode,
        String reason,
        String message,
        Instant startedAt,
        Instant finishedAt) {

    public PodSnapshot {
        Objects.requireNonNull(podName, "podName must not be null");
        Objects.requireNonNull(phase, "phase must not be null");
    }

    /** {@code true} when both labels were present and valid, so the pod maps to a job attempt. */
    public boolean hasJobIdentity() {
        return jobId != null && attempt != null && attempt >= 1;
    }
}
