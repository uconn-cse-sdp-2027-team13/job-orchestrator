package edu.uconn.cse.team13.producer;

import edu.uconn.cse.team13.common.model.JobMessage;
import edu.uconn.cse.team13.common.model.JobState;
import edu.uconn.cse.team13.common.queue.JobQueue;
import edu.uconn.cse.team13.common.queue.QueuedJob;
import edu.uconn.cse.team13.common.repository.JobRepository;
import edu.uconn.cse.team13.producer.generator.CsvDataGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProducerServiceTest {

    private final Instant fixedInstant = Instant.parse("2026-11-07T12:00:00Z");
    private final Clock fixedClock = Clock.fixed(fixedInstant, ZoneOffset.UTC);

    @Test
    void testInitialStateIsQueued() {
        ProducerService service = new ProducerService();
        assertEquals(JobState.QUEUED, service.getInitialState());
    }

    @Test
    void produceAndPublishSyntheticJobCreatesFilePersistsAndPublishes(@TempDir Path tempDir) {
        AtomicReference<JobMessage> publishedMessage = new AtomicReference<>();
        AtomicReference<UUID> persistedJobId = new AtomicReference<>();
        AtomicReference<String> persistedFileName = new AtomicReference<>();
        AtomicReference<String> persistedFilePath = new AtomicReference<>();

        JobQueue queue = new FakeJobQueue() {
            @Override
            public String publish(JobMessage message) {
                publishedMessage.set(message);
                return "1-0";
            }
        };

        JobRepository repository = new FakeJobRepository() {
            @Override
            public void createJob(UUID jobId, String fileName, String filePath) {
                persistedJobId.set(jobId);
                persistedFileName.set(fileName);
                persistedFilePath.set(filePath);
            }
        };

        ProducerService service = new ProducerService(
                queue,
                repository,
                new CsvDataGenerator(),
                tempDir,
                fixedClock);

        UUID jobId = UUID.randomUUID();
        String fileName = "payments_test.csv";
        int recordCount = 10;

        JobMessage result = service.produceAndPublishSyntheticJob(jobId, fileName, recordCount);

        assertNotNull(result);
        assertEquals(1, result.schemaVersion());
        assertEquals(jobId, result.jobId());
        assertEquals(fileName, result.fileName());
        assertEquals(tempDir.resolve(fileName).toAbsolutePath().toString(), result.filePath());
        assertEquals(fixedInstant, result.submittedAt());
        assertEquals(1, result.attempt());

        // File created
        Path createdFile = tempDir.resolve(fileName);
        assertTrue(Files.exists(createdFile));

        // Repository called
        assertEquals(jobId, persistedJobId.get());
        assertEquals(fileName, persistedFileName.get());
        assertEquals(result.filePath(), persistedFilePath.get());

        // Queue called
        assertEquals(result, publishedMessage.get());
    }

    @Test
    void produceAndPublishRejectsMissingComponents(@TempDir Path tempDir) {
        ProducerService service = new ProducerService();
        assertThrows(IllegalStateException.class, () -> service.produceAndPublishSyntheticJob(10));
    }

    @Test
    void produceDoesNotPublishWhenRepositoryFails(@TempDir Path tempDir) {
        AtomicBoolean published = new AtomicBoolean();
        JobQueue queue = new FakeJobQueue() {
            @Override
            public String publish(JobMessage message) {
                published.set(true);
                return "1-0";
            }
        };

        JobRepository repository = new FakeJobRepository() {
            @Override
            public void createJob(UUID jobId, String fileName, String filePath) {
                throw new IllegalStateException("database down");
            }
        };

        ProducerService service = new ProducerService(
                queue,
                repository,
                new CsvDataGenerator(),
                tempDir,
                fixedClock);

        assertThrows(IllegalStateException.class, () -> service.produceAndPublishSyntheticJob(10));
        assertFalse(published.get());
    }

    @Test
    void scheduleSyntheticJobGenerationTriggersExecution(@TempDir Path tempDir) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        JobQueue queue = new FakeJobQueue() {
            @Override
            public String publish(JobMessage message) {
                latch.countDown();
                return "1-0";
            }
        };

        JobRepository repository = new FakeJobRepository();
        ProducerService service = new ProducerService(
                queue,
                repository,
                new CsvDataGenerator(),
                tempDir,
                fixedClock);

        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        try {
            ScheduledFuture<?> future = service.scheduleSyntheticJobGeneration(
                    executor,
                    0,
                    1,
                    TimeUnit.SECONDS,
                    5);

            assertTrue(latch.await(3, TimeUnit.SECONDS), "Scheduled task should publish at least one job");
            future.cancel(true);
        } finally {
            executor.shutdownNow();
        }
    }

    private static class FakeJobQueue implements JobQueue {
        @Override
        public String publish(JobMessage message) {
            return "msg-id";
        }

        @Override
        public Optional<QueuedJob> readNext(Duration blockTimeout) {
            return Optional.empty();
        }

        @Override
        public void acknowledge(String queueMessageId) {
        }

        @Override
        public List<QueuedJob> reclaimStale(Duration minimumIdleTime, int limit) {
            return List.of();
        }
    }

    private static class FakeJobRepository implements JobRepository {
        @Override
        public void createJob(UUID jobId, String fileName, String filePath) {
        }

        @Override
        public void markDispatched(UUID jobId, int attempt, String kubernetesJobName) {
        }

        @Override
        public int countActiveJobs() {
            return 0;
        }

        @Override
        public Optional<JobState> findState(UUID jobId) {
            return Optional.of(JobState.QUEUED);
        }
    }
}
