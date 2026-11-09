package edu.uconn.cse.team13.consumer;

import edu.uconn.cse.team13.common.capacity.JobCapacity;
import edu.uconn.cse.team13.common.model.JobMessage;
import edu.uconn.cse.team13.common.queue.JobQueue;
import edu.uconn.cse.team13.common.queue.QueuedJob;
import edu.uconn.cse.team13.common.repository.JobRepository;
import edu.uconn.cse.team13.common.scheduler.JobScheduler;
import edu.uconn.cse.team13.common.queue.InvalidQueueMessageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * FIFO dispatch loop: capacity check, read one, schedule, persist {@code DISPATCHED}, then ack.
 */
public class ConsumerService {

    private static final Logger logger = LoggerFactory.getLogger(ConsumerService.class);

    /** Only schema version 1 is accepted by this consumer. */
    private static final int SUPPORTED_SCHEMA_VERSION = 1;

    private final JobQueue queue;
    private final JobScheduler scheduler;
    private final JobRepository jobRepository;
    private final JobCapacity capacity;
    private final Duration readBlockTimeout;
    private final Duration capacityPollInterval;

    private volatile boolean running = true;

    public ConsumerService(
            JobQueue queue,
            JobScheduler scheduler,
            JobRepository jobRepository,
            JobCapacity capacity,
            Duration readBlockTimeout,
            Duration capacityPollInterval) {
        this.queue = Objects.requireNonNull(queue, "queue must not be null");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler must not be null");
        this.jobRepository = Objects.requireNonNull(jobRepository, "jobRepository must not be null");
        this.capacity = Objects.requireNonNull(capacity, "capacity must not be null");
        this.readBlockTimeout = requireNonNegative(readBlockTimeout, "readBlockTimeout");
        this.capacityPollInterval = requireNonNegative(capacityPollInterval, "capacityPollInterval");
    }

    /** Stops {@link #run()} after the current step finishes. */
    public void stop() {
        running = false;
    }

    /** Blocks, repeatedly calling {@link #processNext()} until {@link #stop()} is called. */
    public void run() {
        while (running) {
            DispatchStepResult result = processNext();
            if (result == DispatchStepResult.WAITING_FOR_CAPACITY) {
                sleepQuietly(capacityPollInterval);
            }
        }
    }

    /**
     * One dispatch step. Checks capacity before reading so a full cluster does not pull more work.
     */
    public DispatchStepResult processNext() {
        if (!capacity.hasAvailableSlot()) {
            logger.debug("At concurrent job capacity; skipping queue read");
            return DispatchStepResult.WAITING_FOR_CAPACITY;
        }

        Optional<QueuedJob> next;
        try {
            next = queue.readNext(readBlockTimeout);
        } catch (InvalidQueueMessageException ex) {
            logger.error(
                    "Malformed queue entry {}; leaving unacked for recovery/DLQ handling",
                    ex.queueMessageId(),
                    ex);
            return DispatchStepResult.INVALID_MESSAGE;
        }

        if (next.isEmpty()) {
            return DispatchStepResult.IDLE;
        }

        return dispatch(next.get());
    }

    private DispatchStepResult dispatch(QueuedJob queued) {
        JobMessage message = queued.message();
        try {
            validateMessage(message);
            String kubernetesJobName = scheduler.submit(message);
            jobRepository.markDispatched(message.jobId(), message.attempt(), kubernetesJobName);
            queue.acknowledge(queued.queueMessageId());
            logger.info(
                    "Dispatched jobId={} attempt={} k8sJob={} queueMessageId={}",
                    message.jobId(),
                    message.attempt(),
                    kubernetesJobName,
                    queued.queueMessageId());
            return DispatchStepResult.DISPATCHED;
        } catch (RuntimeException ex) {
            logger.error(
                    "Dispatch failed for jobId={} queueMessageId={}; leaving unacked",
                    message.jobId(),
                    queued.queueMessageId(),
                    ex);
            return DispatchStepResult.DISPATCH_FAILED;
        }
    }

    private static void validateMessage(JobMessage message) {
        if (message.schemaVersion() != SUPPORTED_SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "unsupported schemaVersion: " + message.schemaVersion());
        }
    }

    private static Duration requireNonNegative(Duration value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isNegative()) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
        return value;
    }

    private static void sleepQuietly(Duration duration) {
        if (duration.isZero()) {
            return;
        }
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
