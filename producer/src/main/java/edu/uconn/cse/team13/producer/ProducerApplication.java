package edu.uconn.cse.team13.producer;

import edu.uconn.cse.team13.common.db.DataSourceFactory;
import edu.uconn.cse.team13.common.model.JobMessage;
import edu.uconn.cse.team13.common.queue.JobQueue;
import edu.uconn.cse.team13.common.queue.RedisJobQueue;
import edu.uconn.cse.team13.common.queue.RedisJobQueueConfig;
import edu.uconn.cse.team13.common.repository.JobRepository;
import edu.uconn.cse.team13.common.repository.MysqlJobRepository;
import edu.uconn.cse.team13.producer.generator.CsvDataGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Entry point: wires the producer from environment variables and either executes a one-shot job
 * or runs a recurring scheduled job generator until stopped.
 */
public final class ProducerApplication {

    private static final Logger logger = LoggerFactory.getLogger(ProducerApplication.class);

    private ProducerApplication() {
    }

    public static void main(String[] args) {
        ProducerConfig config = ProducerConfig.fromEnv(System.getenv());
        logger.info("Starting ProducerApplication with dataDir={}, recordCount={}, oneShot={}, intervalSeconds={}",
                config.dataDir(), config.recordCount(), config.oneShot(), config.intervalSeconds());

        DataSource dataSource = DataSourceFactory.create(config.jdbcUrl(), config.dbUser(), config.dbPassword());
        JobRepository repository = new MysqlJobRepository(dataSource);

        RedisJobQueueConfig queueConfig = new RedisJobQueueConfig(
                config.redisUri(),
                config.streamKey(),
                config.consumerGroup(),
                config.producerName());
        RedisJobQueue queue = new RedisJobQueue(queueConfig);

        ProducerService producerService = createProducerService(config, queue, repository);

        if (config.oneShot() || config.intervalSeconds() <= 0) {
            logger.info("Executing one-shot synthetic job generation (recordCount={})...", config.recordCount());
            try {
                JobMessage message = producerService.produceAndPublishSyntheticJob(config.recordCount());
                logger.info("Successfully produced and published job {} for file {}", message.jobId(), message.fileName());
            } finally {
                try {
                    queue.close();
                } catch (Exception ex) {
                    logger.warn("Error closing RedisJobQueue", ex);
                }
            }
            return;
        }

        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "producer-scheduler");
            t.setDaemon(true);
            return t;
        });
        CountDownLatch stopLatch = new CountDownLatch(1);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutting down producer scheduler...");
            stopLatch.countDown();
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException ex) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            try {
                queue.close();
            } catch (Exception ex) {
                logger.warn("Error closing RedisJobQueue", ex);
            }
        }, "producer-shutdown"));

        long initialDelay = config.produceOnStart() ? 0 : config.intervalSeconds();
        logger.info("Scheduling synthetic job generation every {} seconds (initialDelay={}s)...",
                config.intervalSeconds(), initialDelay);

        producerService.scheduleSyntheticJobGeneration(
                executor,
                initialDelay,
                config.intervalSeconds(),
                TimeUnit.SECONDS,
                config.recordCount());

        try {
            stopLatch.await();
        } catch (InterruptedException ex) {
            logger.info("Producer interrupted; shutting down.");
            Thread.currentThread().interrupt();
        }
    }

    static ProducerService createProducerService(
            ProducerConfig config,
            JobQueue queue,
            JobRepository repository) {
        return new ProducerService(
                queue,
                repository,
                new CsvDataGenerator(),
                config.dataDir());
    }
}

