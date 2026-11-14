package edu.uconn.cse.team13.producer;

import edu.uconn.cse.team13.common.model.JobMessage;
import edu.uconn.cse.team13.common.model.JobState;
import edu.uconn.cse.team13.common.queue.JobQueue;
import edu.uconn.cse.team13.common.queue.QueuedJob;
import edu.uconn.cse.team13.common.repository.JobRepository;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProducerApplicationTest {

    @Test
    void createProducerServiceWiresComponentsCorrectly() {
        ProducerConfig config = ProducerConfig.fromEnv(Map.of(
                "JDBC_URL", "jdbc:mysql://localhost:3306/app",
                "DB_USER", "app",
                "DB_PASSWORD", "secret",
                "S3_ENDPOINT", "http://localhost:9000",
                "S3_BUCKET", "job-files",
                "S3_ACCESS_KEY", "local-rustfs",
                "S3_SECRET_KEY", "local-rustfs-password",
                "S3_REGION", "us-east-1",
                "RECORD_COUNT", "10"));

        AtomicReference<JobMessage> published = new AtomicReference<>();
        JobQueue queue = new JobQueue() {
            @Override
            public String publish(JobMessage message) {
                published.set(message);
                return "1-0";
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
        };

        JobRepository repository = new JobRepository() {
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
        };

        InMemoryStorageClient storage = new InMemoryStorageClient();
        ProducerService service = ProducerApplication.createProducerService(config, queue, repository, storage);
        assertNotNull(service);

        JobMessage message = service.produceAndPublishSyntheticJob(10);
        assertNotNull(message);
        assertEquals(message, published.get());
        assertTrue(storage.exists(message.filePath()));
    }
}

