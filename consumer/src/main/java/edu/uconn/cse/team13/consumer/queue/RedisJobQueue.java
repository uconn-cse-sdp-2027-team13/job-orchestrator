package edu.uconn.cse.team13.consumer.queue;

import edu.uconn.cse.team13.common.model.JobMessage;
import edu.uconn.cse.team13.common.queue.JobQueue;
import edu.uconn.cse.team13.common.queue.QueuedJob;
import io.lettuce.core.Consumer;
import io.lettuce.core.RedisBusyException;
import io.lettuce.core.RedisClient;
import io.lettuce.core.StreamMessage;
import io.lettuce.core.XAutoClaimArgs;
import io.lettuce.core.XGroupCreateArgs;
import io.lettuce.core.XReadArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.models.stream.ClaimedMessages;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Redis Streams implementation of {@link JobQueue}.
 * Creates the consumer group on construction (idempotent) and reads with {@code COUNT 1}.
 */
public final class RedisJobQueue implements JobQueue, AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(RedisJobQueue.class);

    private final RedisJobQueueConfig config;
    private final RedisClient client;
    private final StatefulRedisConnection<String, String> connection;
    private final RedisCommands<String, String> redis;

    public RedisJobQueue(RedisJobQueueConfig config) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.client = RedisClient.create(config.redisUri());
        this.connection = client.connect();
        this.redis = connection.sync();
        ensureConsumerGroup();
    }

    /** Test-facing constructor that skips client lifecycle. */
    RedisJobQueue(RedisJobQueueConfig config, RedisCommands<String, String> redis) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.client = null;
        this.connection = null;
        this.redis = Objects.requireNonNull(redis, "redis must not be null");
        ensureConsumerGroup();
    }

    @Override
    public String publish(JobMessage message) {
        Objects.requireNonNull(message, "message must not be null");
        return redis.xadd(config.streamKey(), JobMessageCodec.encode(message));
    }

    @Override
    public Optional<QueuedJob> readNext(Duration blockTimeout) {
        Objects.requireNonNull(blockTimeout, "blockTimeout must not be null");
        if (blockTimeout.isNegative()) {
            throw new IllegalArgumentException("blockTimeout must not be negative");
        }

        XReadArgs readArgs = XReadArgs.Builder.count(1).block(blockTimeout.toMillis());
        List<StreamMessage<String, String>> messages = redis.xreadgroup(
                Consumer.from(config.consumerGroup(), config.consumerName()),
                readArgs,
                XReadArgs.StreamOffset.from(config.streamKey(), ">"));

        if (messages == null || messages.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(toQueuedJob(messages.get(0)));
    }

    @Override
    public void acknowledge(String queueMessageId) {
        Objects.requireNonNull(queueMessageId, "queueMessageId must not be null");
        if (queueMessageId.isBlank()) {
            throw new IllegalArgumentException("queueMessageId must not be blank");
        }
        redis.xack(config.streamKey(), config.consumerGroup(), queueMessageId);
    }

    @Override
    public List<QueuedJob> reclaimStale(Duration minimumIdleTime, int limit) {
        Objects.requireNonNull(minimumIdleTime, "minimumIdleTime must not be null");
        if (minimumIdleTime.isNegative()) {
            throw new IllegalArgumentException("minimumIdleTime must not be negative");
        }
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be at least 1");
        }

        XAutoClaimArgs<String> claimArgs = XAutoClaimArgs.Builder
                .xautoclaim(
                        Consumer.from(config.consumerGroup(), config.consumerName()),
                        minimumIdleTime,
                        "0-0")
                .count(limit);
        ClaimedMessages<String, String> claimed = redis.xautoclaim(config.streamKey(), claimArgs);

        List<QueuedJob> jobs = new ArrayList<>();
        if (claimed == null || claimed.getMessages() == null) {
            return jobs;
        }
        for (StreamMessage<String, String> message : claimed.getMessages()) {
            jobs.add(toQueuedJob(message));
        }
        return jobs;
    }

    @Override
    public void close() {
        if (connection != null) {
            connection.close();
        }
        if (client != null) {
            client.shutdown();
        }
    }

    private void ensureConsumerGroup() {
        try {
            redis.xgroupCreate(
                    XReadArgs.StreamOffset.from(config.streamKey(), "0"),
                    config.consumerGroup(),
                    XGroupCreateArgs.Builder.mkstream());
            logger.info(
                    "Created Redis consumer group '{}' on stream '{}'",
                    config.consumerGroup(),
                    config.streamKey());
        } catch (RedisBusyException alreadyExists) {
            logger.debug(
                    "Redis consumer group '{}' already exists on stream '{}'",
                    config.consumerGroup(),
                    config.streamKey());
        }
    }

    private QueuedJob toQueuedJob(StreamMessage<String, String> message) {
        String entryId = message.getId();
        try {
            return new QueuedJob(entryId, JobMessageCodec.decode(message.getBody()));
        } catch (RuntimeException ex) {
            throw new InvalidQueueMessageException(
                    entryId,
                    "Invalid job message at stream entry " + entryId + ": " + ex.getMessage(),
                    ex);
        }
    }
}
