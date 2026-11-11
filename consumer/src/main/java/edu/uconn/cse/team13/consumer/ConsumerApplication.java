package edu.uconn.cse.team13.consumer;

import edu.uconn.cse.team13.common.capacity.JobCapacity;
import edu.uconn.cse.team13.common.capacity.RepositoryJobCapacity;
import edu.uconn.cse.team13.common.db.DataSourceFactory;
import edu.uconn.cse.team13.common.queue.JobQueue;
import edu.uconn.cse.team13.common.queue.RedisJobQueue;
import edu.uconn.cse.team13.common.queue.RedisJobQueueConfig;
import edu.uconn.cse.team13.common.repository.JobRepository;
import edu.uconn.cse.team13.common.repository.MysqlJobRepository;
import edu.uconn.cse.team13.common.scheduler.JobScheduler;
import edu.uconn.cse.team13.scheduler.Fabric8JobScheduler;
import edu.uconn.cse.team13.scheduler.KubernetesJobConfig;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;

/**
 * Entry point: wires the queue consumer from environment variables and runs until stopped.
 */
public final class ConsumerApplication {

    private static final Logger logger = LoggerFactory.getLogger(ConsumerApplication.class);

    private ConsumerApplication() {
    }

    public static void main(String[] args) {
        ConsumerConfig config = ConsumerConfig.fromEnv(System.getenv());
        logger.info("Starting ConsumerApplication with stream={}, group={}, consumerName={}, maxConcurrentJobs={}",
                config.streamKey(), config.consumerGroup(), config.consumerName(), config.maxConcurrentJobs());

        DataSource dataSource = DataSourceFactory.create(config.jdbcUrl(), config.dbUser(), config.dbPassword());
        JobRepository jobRepository = new MysqlJobRepository(dataSource);

        RedisJobQueueConfig queueConfig = new RedisJobQueueConfig(
                config.redisUri(),
                config.streamKey(),
                config.consumerGroup(),
                config.consumerName());
        RedisJobQueue queue = new RedisJobQueue(queueConfig);

        KubernetesClient k8sClient = new KubernetesClientBuilder().build();
        KubernetesJobConfig k8sConfig = new KubernetesJobConfig(
                config.k8sNamespace(),
                config.workloadImage(),
                config.dataHostPath(),
                config.workloadEnvironment());
        JobScheduler scheduler = new Fabric8JobScheduler(k8sClient, k8sConfig);

        ConsumerService service = createConsumerService(config, queue, scheduler, jobRepository);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutting down consumer service...");
            service.stop();
            try {
                queue.close();
            } catch (Exception ex) {
                logger.warn("Error closing RedisJobQueue", ex);
            }
            try {
                k8sClient.close();
            } catch (Exception ex) {
                logger.warn("Error closing KubernetesClient", ex);
            }
        }, "consumer-shutdown"));

        logger.info("Consumer service listening for jobs...");
        service.run();
    }

    static ConsumerService createConsumerService(
            ConsumerConfig config,
            JobQueue queue,
            JobScheduler scheduler,
            JobRepository jobRepository) {
        JobCapacity capacity = new RepositoryJobCapacity(jobRepository, config.maxConcurrentJobs());
        return new ConsumerService(
                queue,
                scheduler,
                jobRepository,
                capacity,
                config.readBlockTimeout(),
                config.capacityPollInterval());
    }
}

