package edu.uconn.cse.team13.producer;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

/**
 * Runtime configuration for the producer service read from environment variables.
 */
public record ProducerConfig(
        String jdbcUrl,
        String dbUser,
        String dbPassword,
        String redisUri,
        String streamKey,
        String consumerGroup,
        String producerName,
        Path dataDir,
        int recordCount,
        boolean oneShot,
        boolean produceOnStart,
        int intervalSeconds) {

    public static final String DEFAULT_REDIS_HOST = "localhost";
    public static final int DEFAULT_REDIS_PORT = 6379;
    public static final String DEFAULT_STREAM_KEY = "jobs:stream";
    public static final String DEFAULT_CONSUMER_GROUP = "orchestrator-producers";
    public static final String DEFAULT_PRODUCER_NAME = "producer-1";
    public static final String DEFAULT_DATA_DIR = "/data";
    public static final int DEFAULT_RECORD_COUNT = 2500;
    public static final int DEFAULT_INTERVAL_SECONDS = 300; // 5 minutes (design doc Section 5.2 / 7.1)
    public static final boolean DEFAULT_PRODUCE_ON_START = true;
    public static final boolean DEFAULT_ONE_SHOT = false;

    public ProducerConfig {
        Objects.requireNonNull(jdbcUrl, "jdbcUrl must not be null");
        Objects.requireNonNull(dbUser, "dbUser must not be null");
        Objects.requireNonNull(dbPassword, "dbPassword must not be null");
        Objects.requireNonNull(redisUri, "redisUri must not be null");
        Objects.requireNonNull(streamKey, "streamKey must not be null");
        Objects.requireNonNull(consumerGroup, "consumerGroup must not be null");
        Objects.requireNonNull(producerName, "producerName must not be null");
        Objects.requireNonNull(dataDir, "dataDir must not be null");
        if (recordCount < 1) {
            throw new IllegalArgumentException("recordCount must be at least 1");
        }
        if (intervalSeconds < 0) {
            throw new IllegalArgumentException("intervalSeconds must be non-negative");
        }
    }

    /**
     * Parses configuration from environment variables.
     */
    public static ProducerConfig fromEnv(Map<String, String> env) {
        String jdbcUrl = required(env, "JDBC_URL", "MYSQL_URL");
        String dbUser = required(env, "DB_USER", "MYSQL_USER");
        String dbPassword = required(env, "DB_PASSWORD", "MYSQL_PASSWORD");

        String redisUri = env.get("REDIS_URI");
        if (redisUri == null || redisUri.isBlank()) {
            String redisHost = env.getOrDefault("REDIS_HOST", DEFAULT_REDIS_HOST);
            int redisPort = intValue(env, "REDIS_PORT", DEFAULT_REDIS_PORT);
            redisUri = "redis://" + redisHost.trim() + ":" + redisPort;
        }

        String streamKey = env.getOrDefault("REDIS_STREAM",
                env.getOrDefault("REDIS_STREAM_KEY", DEFAULT_STREAM_KEY));
        String consumerGroup = env.getOrDefault("REDIS_GROUP",
                env.getOrDefault("REDIS_CONSUMER_GROUP", DEFAULT_CONSUMER_GROUP));
        String producerName = env.getOrDefault("REDIS_PRODUCER_NAME",
                env.getOrDefault("HOSTNAME", DEFAULT_PRODUCER_NAME));

        String rawDataDir = env.getOrDefault("DATA_DIR",
                env.getOrDefault("DATA_HOST_PATH", DEFAULT_DATA_DIR));
        Path dataDir = Path.of(rawDataDir).toAbsolutePath();

        int recordCount = intValue(env, "RECORD_COUNT", DEFAULT_RECORD_COUNT);
        boolean oneShot = booleanValue(env, "ONE_SHOT", DEFAULT_ONE_SHOT);
        boolean produceOnStart = booleanValue(env, "PRODUCE_ON_START", DEFAULT_PRODUCE_ON_START);
        int intervalSeconds = intValue(env, "INTERVAL_SECONDS",
                intValue(env, "CRON_INTERVAL_SECONDS", DEFAULT_INTERVAL_SECONDS));

        return new ProducerConfig(
                jdbcUrl,
                dbUser,
                dbPassword,
                redisUri,
                streamKey,
                consumerGroup,
                producerName,
                dataDir,
                recordCount,
                oneShot,
                produceOnStart,
                intervalSeconds);
    }

    private static String required(Map<String, String> env, String primaryKey, String fallbackKey) {
        String value = env.get(primaryKey);
        if (value == null || value.isBlank()) {
            value = env.get(fallbackKey);
        }
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "Missing required environment variable " + primaryKey + " (or " + fallbackKey + ")");
        }
        return value;
    }

    private static int intValue(Map<String, String> env, String key, int fallback) {
        String value = env.get(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(key + " must be an integer: " + value, ex);
        }
    }

    private static boolean booleanValue(Map<String, String> env, String key, boolean fallback) {
        String value = env.get(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return Boolean.parseBoolean(value.trim());
    }
}

