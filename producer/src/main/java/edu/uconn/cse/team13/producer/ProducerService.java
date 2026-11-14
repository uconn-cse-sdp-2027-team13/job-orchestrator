package edu.uconn.cse.team13.producer;

import edu.uconn.cse.team13.common.model.JobMessage;
import edu.uconn.cse.team13.common.model.JobState;
import edu.uconn.cse.team13.common.queue.JobQueue;
import edu.uconn.cse.team13.common.repository.JobRepository;
import edu.uconn.cse.team13.common.storage.StorageClient;
import edu.uconn.cse.team13.producer.generator.CsvDataGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Coordinates file generation, upload of the file to object storage, persistence of the initial
 * {@code QUEUED} job state, and dispatch of {@link JobMessage} payloads to the Redis Streams queue.
 */
public class ProducerService {

    private static final Logger logger = LoggerFactory.getLogger(ProducerService.class);

    private final JobQueue queue;
    private final JobRepository repository;
    private final CsvDataGenerator generator;
    private final StorageClient storage;
    private final Clock clock;

    public ProducerService() {
        this.queue = null;
        this.repository = null;
        this.generator = new CsvDataGenerator();
        this.storage = null;
        this.clock = Clock.systemUTC();
    }

    public ProducerService(
            JobQueue queue,
            JobRepository repository,
            CsvDataGenerator generator,
            StorageClient storage) {
        this(queue, repository, generator, storage, Clock.systemUTC());
    }

    public ProducerService(
            JobQueue queue,
            JobRepository repository,
            CsvDataGenerator generator,
            StorageClient storage,
            Clock clock) {
        this.queue = Objects.requireNonNull(queue, "queue must not be null");
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.generator = Objects.requireNonNull(generator, "generator must not be null");
        this.storage = Objects.requireNonNull(storage, "storage must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /** Returns the initial state for jobs submitted by the producer. */
    public JobState getInitialState() {
        return JobState.QUEUED;
    }

    /**
     * Produces a synthetic CSV file matching Section 6.1, uploads it to object storage,
     * registers the job as {@code QUEUED} in the repository, and publishes a {@link JobMessage}
     * to the queue.
     *
     * @param recordCount number of synthetic records to generate
     * @return the published JobMessage
     */
    public JobMessage produceAndPublishSyntheticJob(int recordCount) {
        UUID jobId = UUID.randomUUID();
        String fileName = "payments_" + jobId + ".csv";
        return produceAndPublishSyntheticJob(jobId, fileName, recordCount);
    }

    /**
     * Produces a synthetic CSV file with the given jobId and fileName, uploads it to object storage,
     * registers as {@code QUEUED}, and publishes to the queue.
     *
     * @param jobId stable identifier for the job
     * @param fileName display file name, also used as the object key the file is stored under
     * @param recordCount number of synthetic records to generate
     * @return the published JobMessage
     */
    public JobMessage produceAndPublishSyntheticJob(UUID jobId, String fileName, int recordCount) {
        Objects.requireNonNull(jobId, "jobId must not be null");
        Objects.requireNonNull(fileName, "fileName must not be null");
        if (fileName.isBlank()) {
            throw new IllegalArgumentException("fileName must not be blank");
        }
        if (queue == null || repository == null || storage == null) {
            throw new IllegalStateException("ProducerService requires configured queue, repository, and storage");
        }

        // The object key is relative (no host directory), so a pod can fetch the file from storage.
        String filePath = fileName;

        try {
            logger.info("Generating synthetic CSV with {} records and uploading it as {}", recordCount, filePath);
            uploadSyntheticCsv(filePath, recordCount);
        } catch (IOException ex) {
            logger.error("Failed to generate or upload synthetic CSV for jobId={}", jobId, ex);
            throw new IllegalStateException("Failed to generate or upload CSV for jobId=" + jobId, ex);
        }

        try {
            logger.info("Recording initial QUEUED state in repository for jobId={}", jobId);
            repository.createJob(jobId, fileName, filePath);
        } catch (RuntimeException ex) {
            logger.error("Failed to persist QUEUED state for jobId={}", jobId, ex);
            throw ex;
        }

        JobMessage message = new JobMessage(
                JobMessage.CURRENT_SCHEMA_VERSION,
                jobId,
                fileName,
                filePath,
                Instant.now(clock),
                1);

        try {
            String messageId = queue.publish(message);
            logger.info("Published JobMessage to queue with messageId={} for jobId={}", messageId, jobId);
        } catch (RuntimeException ex) {
            logger.error("Failed to publish JobMessage to queue for jobId={}", jobId, ex);
            throw ex;
        }

        return message;
    }

    /**
     * Generates the CSV into a temporary local file, streams that file into object storage under
     * {@code objectKey}, and removes the temporary file whether or not the upload worked.
     */
    private void uploadSyntheticCsv(String objectKey, int recordCount) throws IOException {
        Path tempFile = Files.createTempFile("producer-", ".csv");
        try {
            generator.generateSyntheticCsv(tempFile, recordCount);
            try (InputStream csv = Files.newInputStream(tempFile)) {
                storage.write(objectKey, csv);
            }
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    /**
     * Starts recurring scheduled synthetic job generation on the supplied executor.
     *
     * @param scheduler executor service to schedule tasks
     * @param initialDelay initial delay before first execution
     * @param period period between successive executions
     * @param unit time unit of delay and period
     * @param recordCount number of records per generated job
     * @return scheduled future representing pending completion of the task
     */
    public ScheduledFuture<?> scheduleSyntheticJobGeneration(
            ScheduledExecutorService scheduler,
            long initialDelay,
            long period,
            TimeUnit unit,
            int recordCount) {
        Objects.requireNonNull(scheduler, "scheduler must not be null");
        return scheduler.scheduleAtFixedRate(
                () -> {
                    try {
                        produceAndPublishSyntheticJob(recordCount);
                    } catch (Exception ex) {
                        logger.error("Scheduled synthetic job generation failed", ex);
                    }
                },
                initialDelay,
                period,
                unit);
    }
}
