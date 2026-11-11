package edu.uconn.cse.team13.consumer;

import edu.uconn.cse.team13.common.model.JobState;
import edu.uconn.cse.team13.common.queue.JobQueue;
import edu.uconn.cse.team13.common.queue.QueuedJob;
import edu.uconn.cse.team13.common.repository.JobRepository;
import edu.uconn.cse.team13.common.scheduler.JobScheduler;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ConsumerApplicationTest {

    @Test
    void createConsumerServiceWiresComponentsCorrectly() {
        ConsumerConfig config = ConsumerConfig.fromEnv(Map.of(
                "JDBC_URL", "jdbc:mysql://localhost:3306/app",
                "DB_USER", "app",
                "DB_PASSWORD", "secret",
                "MAX_CONCURRENT_JOBS", "3"));

        JobQueue queue = new JobQueue() {
            @Override
            public String publish(edu.uconn.cse.team13.common.model.JobMessage message) {
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

        JobScheduler scheduler = message -> "k8s-job-1";

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

        ConsumerService service = ConsumerApplication.createConsumerService(config, queue, scheduler, repository);
        assertNotNull(service);
        assertEquals(DispatchStepResult.IDLE, service.processNext());
    }
}
