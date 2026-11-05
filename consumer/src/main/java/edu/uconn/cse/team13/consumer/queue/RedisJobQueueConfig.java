package edu.uconn.cse.team13.consumer.queue;

import java.util.Objects;

/**
 * Connection and consumer-group settings for {@link RedisJobQueue}.
 */
public record RedisJobQueueConfig(
        String redisUri,
        String streamKey,
        String consumerGroup,
        String consumerName) {

    public RedisJobQueueConfig {
        redisUri = requireText(redisUri, "redisUri");
        streamKey = requireText(streamKey, "streamKey");
        consumerGroup = requireText(consumerGroup, "consumerGroup");
        consumerName = requireText(consumerName, "consumerName");
    }

    private static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
