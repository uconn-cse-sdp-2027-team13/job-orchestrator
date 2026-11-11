package edu.uconn.cse.team13.consumer;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Runtime configuration for the consumer service read from environment variables.
 */
public record ConsumerConfig(
        String jdbcUrl,
        String dbUser,
        String dbPassword,
        String redisUri,
        String streamKey,
        String consumerGroup,
        String consumerName,
        int maxConcurrentJobs,
        Duration readBlockTimeout,
        Duration capacityPollInterval,
        String k8sNamespace,
        String workloadImage,
        String dataHostPath,
        Map<String, String> workloadEnvironment) {

    public static final String DEFAULT_REDIS_HOST = "localhost";
    public static final int DEFAULT_REDIS_PORT = 6379;
    public static final String DEFAULT_STREAM_KEY = "jobs:stream";
    public static final String DEFAULT_CONSUMER_GROUP = "orchestrator-consumers";
    public static final String DEFAULT_CONSUMER_NAME = "consumer-1";
    public static final int DEFAULT_MAX_CONCURRENT_JOBS = 2;
    public static final Duration DEFAULT_READ_BLOCK_TIMEOUT = Duration.ofSeconds(2);
    public static final Duration DEFAULT_CAPACITY_POLL_INTERVAL = Duration.ofSeconds(1);
    public static final String DEFAULT_K8S_NAMESPACE = "default";
    public static final String DEFAULT_WORKLOAD_IMAGE = "batch-workload:local";
    public static final String DEFAULT_DATA_PATH = "/data";

    public ConsumerConfig {
        Objects.requireNonNull(jdbcUrl, "jdbcUrl must not be null");
        Objects.requireNonNull(dbUser, "dbUser must not be null");
        Objects.requireNonNull(dbPassword, "dbPassword must not be null");
        Objects.requireNonNull(redisUri, "redisUri must not be null");
        Objects.requireNonNull(streamKey, "streamKey must not be null");
        Objects.requireNonNull(consumerGroup, "consumerGroup must not be null");
        Objects.requireNonNull(consumerName, "consumerName must not be null");
        if (maxConcurrentJobs < 1) {
            throw new IllegalArgumentException("maxConcurrentJobs must be at least 1");
        }
        Objects.requireNonNull(readBlockTimeout, "readBlockTimeout must not be null");
        Objects.requireNonNull(capacityPollInterval, "capacityPollInterval must not be null");
        Objects.requireNonNull(k8sNamespace, "k8sNamespace must not be null");
        Objects.requireNonNull(workloadImage, "workloadImage must not be null");
        Objects.requireNonNull(dataHostPath, "dataHostPath must not be null");
        workloadEnvironment = workloadEnvironment == null ? Map.of() : Map.copyOf(workloadEnvironment);
    }

    /**
     * Parses configuration from environment variables.
     */
    public static ConsumerConfig fromEnv(Map<String, String> env) {
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
        String consumerName = env.getOrDefault("REDIS_CONSUMER_NAME",
                env.getOrDefault("HOSTNAME", DEFAULT_CONSUMER_NAME));

        int maxConcurrentJobs = intValue(env, "MAX_CONCURRENT_JOBS",
                intValue(env, "MAX_ACTIVE_JOBS", DEFAULT_MAX_CONCURRENT_JOBS));

        Duration readBlockTimeout;
        if (env.containsKey("READ_BLOCK_TIMEOUT_MS") && !env.get("READ_BLOCK_TIMEOUT_MS").isBlank()) {
            readBlockTimeout = Duration.ofMillis(intValue(env, "READ_BLOCK_TIMEOUT_MS", 2000));
        } else {
            readBlockTimeout = Duration.ofSeconds(intValue(env, "READ_BLOCK_TIMEOUT_SECONDS", 2));
        }

        Duration capacityPollInterval;
        if (env.containsKey("CAPACITY_POLL_INTERVAL_MS") && !env.get("CAPACITY_POLL_INTERVAL_MS").isBlank()) {
            capacityPollInterval = Duration.ofMillis(intValue(env, "CAPACITY_POLL_INTERVAL_MS", 1000));
        } else {
            capacityPollInterval = Duration.ofSeconds(intValue(env, "CAPACITY_POLL_INTERVAL_SECONDS", 1));
        }

        String k8sNamespace = env.getOrDefault("K8S_NAMESPACE", DEFAULT_K8S_NAMESPACE);
        String workloadImage = env.getOrDefault("WORKLOAD_IMAGE", DEFAULT_WORKLOAD_IMAGE);

        String rawDataPath = env.getOrDefault("DATA_HOST_PATH",
                env.getOrDefault("DATA_DIR", DEFAULT_DATA_PATH));
        String dataHostPath = Path.of(rawDataPath).toAbsolutePath().toString();

        Map<String, String> workloadEnv = new HashMap<>();
        String workloadJdbcUrl = env.getOrDefault("WORKLOAD_JDBC_URL", jdbcUrl);
        String workloadDbUser = env.getOrDefault("WORKLOAD_DB_USER", dbUser);
        String workloadDbPassword = env.getOrDefault("WORKLOAD_DB_PASSWORD", dbPassword);
        workloadEnv.put("JDBC_URL", workloadJdbcUrl);
        workloadEnv.put("DB_USER", workloadDbUser);
        workloadEnv.put("DB_PASSWORD", workloadDbPassword);

        String batchSize = env.get("WORKLOAD_BATCH_SIZE");
        if (batchSize == null || batchSize.isBlank()) {
            batchSize = env.get("BATCH_SIZE");
        }
        if (batchSize != null && !batchSize.isBlank()) {
            workloadEnv.put("BATCH_SIZE", batchSize.trim());
        }

        return new ConsumerConfig(
                jdbcUrl,
                dbUser,
                dbPassword,
                redisUri,
                streamKey,
                consumerGroup,
                consumerName,
                maxConcurrentJobs,
                readBlockTimeout,
                capacityPollInterval,
                k8sNamespace,
                workloadImage,
                dataHostPath,
                Collections.unmodifiableMap(workloadEnv));
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
}

