package edu.uconn.cse.team13.common.queue;

import edu.uconn.cse.team13.common.model.JobMessage;

import java.util.Objects;

/** A delivered queue entry: broker identity plus the decoded domain message. */
public record QueuedJob(String queueMessageId, JobMessage message) {

    public QueuedJob {
        Objects.requireNonNull(queueMessageId, "queueMessageId must not be null");
        if (queueMessageId.isBlank()) {
            throw new IllegalArgumentException("queueMessageId must not be blank");
        }
        Objects.requireNonNull(message, "message must not be null");
    }
}
