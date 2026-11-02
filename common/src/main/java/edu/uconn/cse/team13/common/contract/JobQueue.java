package edu.uconn.cse.team13.common.contract;

import edu.uconn.cse.team13.common.model.JobMessage;
import java.time.Duration;
import java.util.Optional;

/**
 * The message queue jobs travel through (Redis Streams in our design).
 */
public interface JobQueue {

    void publish(JobMessage message);

    /** Waits up to the timeout for the next job. Empty means no job arrived in time. */
    Optional<JobMessage> poll(Duration timeout);

    /** Tells the queue this job was handled so it is not delivered again. */
    void acknowledge(String jobId);

    /** Moves a job that cannot succeed to the dead letter queue. */
    void deadLetter(JobMessage message, String reason);
}