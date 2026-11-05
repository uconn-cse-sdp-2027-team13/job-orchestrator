package edu.uconn.cse.team13.consumer;

import edu.uconn.cse.team13.common.capacity.JobCapacity;
import edu.uconn.cse.team13.common.capacity.RepositoryJobCapacity;
import edu.uconn.cse.team13.common.model.JobMessage;
import edu.uconn.cse.team13.common.queue.JobQueue;
import edu.uconn.cse.team13.common.queue.QueuedJob;
import edu.uconn.cse.team13.common.repository.JobRepository;
import edu.uconn.cse.team13.common.scheduler.JobScheduler;
import edu.uconn.cse.team13.consumer.queue.InvalidQueueMessageException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ConsumerServiceTest {

    @Test
    void processNextWaitsWhenAtCapacityWithoutReading() {
        AtomicInteger reads = new AtomicInteger();
        JobQueue queue = recordingQueue(reads);
        JobRepository repository = fakeRepository(2);
        JobCapacity capacity = new RepositoryJobCapacity(repository, 2);

        ConsumerService consumer = newConsumer(queue, msg -> "job", repository, capacity);

        assertEquals(DispatchStepResult.WAITING_FOR_CAPACITY, consumer.processNext());
        assertEquals(0, reads.get());
    }

    @Test
    void processNextDispatchesThenAcksInOrder() {
        JobMessage message = sampleMessage(1);
        List<String> steps = new ArrayList<>();
        AtomicReference<String> acked = new AtomicReference<>();

        JobQueue queue = singleMessageQueue("1-0", message, acked, steps);
        JobScheduler scheduler = msg -> {
            steps.add("schedule");
            return "k8s-" + msg.jobId();
        };
        JobRepository repository = new JobRepository() {
            @Override
            public void markDispatched(UUID jobId, int attempt, String kubernetesJobName) {
                steps.add("markDispatched:" + kubernetesJobName);
            }

            @Override
            public int countActiveJobs() {
                return 0;
            }
        };

        ConsumerService consumer = newConsumer(queue, scheduler, repository, () -> true);

        assertEquals(DispatchStepResult.DISPATCHED, consumer.processNext());
        assertEquals(List.of("read", "schedule", "markDispatched:k8s-" + message.jobId(), "ack"), steps);
        assertEquals("1-0", acked.get());
    }

    @Test
    void processNextDoesNotAckWhenSchedulerFails() {
        JobMessage message = sampleMessage(1);
        AtomicBoolean acked = new AtomicBoolean();
        JobQueue queue = singleMessageQueue("1-0", message, id -> acked.set(true), new ArrayList<>());

        ConsumerService consumer = newConsumer(
                queue,
                msg -> {
                    throw new IllegalStateException("cluster unavailable");
                },
                fakeRepository(0),
                () -> true);

        assertEquals(DispatchStepResult.DISPATCH_FAILED, consumer.processNext());
        assertFalse(acked.get());
    }

    @Test
    void processNextDoesNotAckWhenRepositoryFails() {
        JobMessage message = sampleMessage(1);
        AtomicBoolean acked = new AtomicBoolean();
        JobQueue queue = singleMessageQueue("1-0", message, id -> acked.set(true), new ArrayList<>());

        JobRepository repository = new JobRepository() {
            @Override
            public void markDispatched(UUID jobId, int attempt, String kubernetesJobName) {
                throw new IllegalStateException("mysql unavailable");
            }

            @Override
            public int countActiveJobs() {
                return 0;
            }
        };

        ConsumerService consumer = newConsumer(queue, msg -> "k8s-job", repository, () -> true);

        assertEquals(DispatchStepResult.DISPATCH_FAILED, consumer.processNext());
        assertFalse(acked.get());
    }

    @Test
    void processNextReportsInvalidMessageWithoutAck() {
        JobQueue queue = new JobQueue() {
            @Override
            public String publish(JobMessage message) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Optional<QueuedJob> readNext(Duration blockTimeout) {
                throw new InvalidQueueMessageException("9-0", "bad payload");
            }

            @Override
            public void acknowledge(String queueMessageId) {
                throw new AssertionError("must not ack invalid message");
            }

            @Override
            public List<QueuedJob> reclaimStale(Duration minimumIdleTime, int limit) {
                return List.of();
            }
        };

        ConsumerService consumer = newConsumer(queue, msg -> "job", fakeRepository(0), () -> true);

        assertEquals(DispatchStepResult.INVALID_MESSAGE, consumer.processNext());
    }

    @Test
    void processNextRejectsUnsupportedSchemaVersionWithoutAck() {
        JobMessage message = sampleMessage(2);
        AtomicBoolean acked = new AtomicBoolean();
        JobQueue queue = singleMessageQueue("1-0", message, id -> acked.set(true), new ArrayList<>());

        ConsumerService consumer = newConsumer(queue, msg -> "job", fakeRepository(0), () -> true);

        assertEquals(DispatchStepResult.DISPATCH_FAILED, consumer.processNext());
        assertFalse(acked.get());
    }

    private static ConsumerService newConsumer(
            JobQueue queue,
            JobScheduler scheduler,
            JobRepository repository,
            JobCapacity capacity) {
        return new ConsumerService(
                queue,
                scheduler,
                repository,
                capacity,
                Duration.ofMillis(10),
                Duration.ofMillis(10));
    }

    private static JobRepository fakeRepository(int activeJobs) {
        return new JobRepository() {
            @Override
            public void markDispatched(UUID jobId, int attempt, String kubernetesJobName) {
            }

            @Override
            public int countActiveJobs() {
                return activeJobs;
            }
        };
    }

    private static JobQueue recordingQueue(AtomicInteger reads) {
        return new JobQueue() {
            @Override
            public String publish(JobMessage message) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Optional<QueuedJob> readNext(Duration blockTimeout) {
                reads.incrementAndGet();
                return Optional.empty();
            }

            @Override
            public void acknowledge(String queueMessageId) {
            }

            @Override
            public List<QueuedJob> reclaimStale(Duration minimumIdleTime, int limit) {
                return List.of();
            }
        };
    }

    private static JobMessage sampleMessage(int schemaVersion) {
        return new JobMessage(
                schemaVersion,
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "payments.csv",
                "incoming/payments.csv",
                Instant.parse("2026-11-04T15:00:00Z"),
                1);
    }

    private static JobQueue singleMessageQueue(
            String entryId,
            JobMessage message,
            AtomicReference<String> acked,
            List<String> steps) {
        return singleMessageQueue(entryId, message, acked::set, steps);
    }

    private static JobQueue singleMessageQueue(
            String entryId,
            JobMessage message,
            java.util.function.Consumer<String> onAck,
            List<String> steps) {
        AtomicBoolean read = new AtomicBoolean();
        return new JobQueue() {
            @Override
            public String publish(JobMessage jobMessage) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Optional<QueuedJob> readNext(Duration blockTimeout) {
                if (read.compareAndSet(false, true)) {
                    steps.add("read");
                    return Optional.of(new QueuedJob(entryId, message));
                }
                return Optional.empty();
            }

            @Override
            public void acknowledge(String queueMessageId) {
                steps.add("ack");
                onAck.accept(queueMessageId);
            }

            @Override
            public List<QueuedJob> reclaimStale(Duration minimumIdleTime, int limit) {
                return List.of();
            }
        };
    }
}
