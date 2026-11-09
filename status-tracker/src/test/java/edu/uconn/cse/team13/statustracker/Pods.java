package edu.uconn.cse.team13.statustracker;

import java.time.Instant;
import java.util.UUID;

/** Test helpers for building pod snapshots. */
final class Pods {

    static final Instant STARTED = Instant.parse("2026-11-08T12:00:00Z");
    static final Instant FINISHED = Instant.parse("2026-11-08T12:05:00Z");

    private Pods() {
    }

    static PodSnapshot pending(UUID job, int attempt) {
        return new PodSnapshot(name(job, attempt), job, attempt, PodPhase.PENDING,
                null, null, null, null, null, null);
    }

    static PodSnapshot running(UUID job, int attempt) {
        return new PodSnapshot(name(job, attempt), job, attempt, PodPhase.RUNNING,
                "node-1", null, null, null, STARTED, null);
    }

    static PodSnapshot succeeded(UUID job, int attempt) {
        return new PodSnapshot(name(job, attempt), job, attempt, PodPhase.SUCCEEDED,
                "node-1", 0, null, null, STARTED, FINISHED);
    }

    static PodSnapshot failed(UUID job, int attempt, int exitCode, String reason) {
        return new PodSnapshot(name(job, attempt), job, attempt, PodPhase.FAILED,
                "node-1", exitCode, reason, null, STARTED, FINISHED);
    }

    static String name(UUID job, int attempt) {
        return "job-" + job.toString().substring(0, 8) + "-" + attempt;
    }
}
