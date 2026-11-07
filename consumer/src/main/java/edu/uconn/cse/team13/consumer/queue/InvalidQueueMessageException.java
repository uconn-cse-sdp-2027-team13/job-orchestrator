package edu.uconn.cse.team13.consumer.queue;

/**
 * A broker entry was delivered but could not be mapped to a valid {@code JobMessage}.
 * Carries the Redis stream entry ID so a later slice can dead-letter or acknowledge
 * without retrying forever.
 */
public class InvalidQueueMessageException extends RuntimeException {

    private final String queueMessageId;

    public InvalidQueueMessageException(String queueMessageId, String reason) {
        super(reason);
        this.queueMessageId = queueMessageId;
    }

    public InvalidQueueMessageException(String queueMessageId, String reason, Throwable cause) {
        super(reason, cause);
        this.queueMessageId = queueMessageId;
    }

    public String queueMessageId() {
        return queueMessageId;
    }
}
