package edu.uconn.cse.team13.common.queue;

import edu.uconn.cse.team13.common.model.JobMessage;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Broker-neutral queue contract shared by the producer, API, and consumer.
 * Implementations own broker configuration such as stream/group names and consumer identity.
 */
public interface JobQueue {

    /** Publishes a job and returns its broker-assigned message ID. */
    String publish(JobMessage message);

    /**
     * Reads at most the next job, waiting up to {@code blockTimeout} when no job is available.
     * A Redis Streams implementation should use a consumer group and preserve stream order.
     */
    Optional<QueuedJob> readNext(Duration blockTimeout);

    /** Acknowledges a delivered broker entry after dispatch has been durably recorded. */
    void acknowledge(String queueMessageId);

    /** Reclaims pending entries that have been idle for at least {@code minimumIdleTime}. */
    List<QueuedJob> reclaimStale(Duration minimumIdleTime, int limit);
}
