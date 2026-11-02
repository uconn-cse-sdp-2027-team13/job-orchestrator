package edu.uconn.cse.team13.common.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Message for one job as it travels through queue
 * Immutable so every service sees exact same job info
 */
public record JobMessage(String jobId, String filePath, int attempt, Instant submittedAt) {
    /**
     * Runs on every job message. Rejects null, blank, or invalid values
     * bad jobs fail here instead of pod
     */
    public JobMessage {
        Objects.requireNonNull(jobId, "jobId must not be null");
        Objects.requireNonNull(filePath, "filePath must not be null");
        Objects.requireNonNull(submittedAt, "submittedAt must not be null");

        if (jobId.isBlank()) throw new IllegalArgumentException("jobId must not be blank");
        if (filePath.isBlank()) throw new IllegalArgumentException("filePath must not be blank");
        if (attempt < 1) throw new IllegalArgumentException("attempt must be at least 1");
    }

    /**
     * Returns new job message with attempt increased by 1, used for retries
     * Original message not changed
     */
    public JobMessage nextAttempt() {
        return new JobMessage(jobId, filePath, attempt + 1, submittedAt);
    }
}