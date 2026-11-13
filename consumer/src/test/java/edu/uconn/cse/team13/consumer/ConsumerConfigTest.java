package edu.uconn.cse.team13.consumer;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsumerConfigTest {

    @Test
    void fromEnvWithDefaults() {
        Map<String, String> env = Map.of(
                "JDBC_URL", "jdbc:mysql://localhost:3306/app",
                "DB_USER", "app",
                "DB_PASSWORD", "secret");

        ConsumerConfig config = ConsumerConfig.fromEnv(env);

        assertEquals("jdbc:mysql://localhost:3306/app", config.jdbcUrl());
        assertEquals("app", config.dbUser());
        assertEquals("secret", config.dbPassword());
        assertEquals("redis://localhost:6379", config.redisUri());
        assertEquals(ConsumerConfig.DEFAULT_STREAM_KEY, config.streamKey());
        assertEquals(ConsumerConfig.DEFAULT_CONSUMER_GROUP, config.consumerGroup());
        assertEquals(ConsumerConfig.DEFAULT_CONSUMER_NAME, config.consumerName());
        assertEquals(2, config.maxConcurrentJobs());
        assertEquals(Duration.ofSeconds(2), config.readBlockTimeout());
        assertEquals(Duration.ofSeconds(1), config.capacityPollInterval());
        assertEquals("default", config.k8sNamespace());
        assertEquals("batch-workload:local", config.workloadImage());
        assertEquals(Path.of("/data").toAbsolutePath().toString(), config.dataHostPath());
        assertEquals("jdbc:mysql://localhost:3306/app", config.workloadEnvironment().get("JDBC_URL"));
        assertEquals("app", config.workloadEnvironment().get("DB_USER"));
        assertEquals("secret", config.workloadEnvironment().get("DB_PASSWORD"));
    }

    @Test
    void fromEnvWithOverrides() {
        Map<String, String> env = Map.ofEntries(
                Map.entry("JDBC_URL", "jdbc:mysql://primary:3306/prod"),
                Map.entry("DB_USER", "admin"),
                Map.entry("DB_PASSWORD", "prod-pass"),
                Map.entry("REDIS_HOST", "redis.internal"),
                Map.entry("REDIS_PORT", "6380"),
                Map.entry("REDIS_STREAM", "custom:stream"),
                Map.entry("REDIS_GROUP", "custom-group"),
                Map.entry("REDIS_CONSUMER_NAME", "worker-99"),
                Map.entry("MAX_CONCURRENT_JOBS", "5"),
                Map.entry("READ_BLOCK_TIMEOUT_MS", "3500"),
                Map.entry("CAPACITY_POLL_INTERVAL_MS", "500"),
                Map.entry("K8S_NAMESPACE", "workloads"),
                Map.entry("WORKLOAD_IMAGE", "custom-worker:v2"),
                Map.entry("DATA_HOST_PATH", "/tmp/cluster-data"),
                Map.entry("WORKLOAD_JDBC_URL", "jdbc:mysql://mysql.workloads:3306/prod"),
                Map.entry("BATCH_SIZE", "3000"));

        ConsumerConfig config = ConsumerConfig.fromEnv(env);

        assertEquals("jdbc:mysql://primary:3306/prod", config.jdbcUrl());
        assertEquals("redis://redis.internal:6380", config.redisUri());
        assertEquals("custom:stream", config.streamKey());
        assertEquals("custom-group", config.consumerGroup());
        assertEquals("worker-99", config.consumerName());
        assertEquals(5, config.maxConcurrentJobs());
        assertEquals(Duration.ofMillis(3500), config.readBlockTimeout());
        assertEquals(Duration.ofMillis(500), config.capacityPollInterval());
        assertEquals("workloads", config.k8sNamespace());
        assertEquals("custom-worker:v2", config.workloadImage());
        assertEquals(Path.of("/tmp/cluster-data").toAbsolutePath().toString(), config.dataHostPath());
        assertEquals("jdbc:mysql://mysql.workloads:3306/prod", config.workloadEnvironment().get("JDBC_URL"));
        assertEquals("admin", config.workloadEnvironment().get("DB_USER"));
        assertEquals("prod-pass", config.workloadEnvironment().get("DB_PASSWORD"));
        assertEquals("3000", config.workloadEnvironment().get("BATCH_SIZE"));
    }

    @Test
    void fromEnvWithExplicitRedisUri() {
        Map<String, String> env = Map.of(
                "JDBC_URL", "jdbc:mysql://localhost:3306/app",
                "DB_USER", "app",
                "DB_PASSWORD", "secret",
                "REDIS_URI", "redis://auth-user:auth-pass@redis.prod:6379/0");

        ConsumerConfig config = ConsumerConfig.fromEnv(env);
        assertEquals("redis://auth-user:auth-pass@redis.prod:6379/0", config.redisUri());
    }

    @Test
    void fromEnvWithMysqlUrlFallback() {
        Map<String, String> env = Map.of(
                "MYSQL_URL", "jdbc:mysql://db.local:3306/app",
                "MYSQL_USER", "mysql_user",
                "MYSQL_PASSWORD", "mysql_pass");

        ConsumerConfig config = ConsumerConfig.fromEnv(env);
        assertEquals("jdbc:mysql://db.local:3306/app", config.jdbcUrl());
        assertEquals("mysql_user", config.dbUser());
        assertEquals("mysql_pass", config.dbPassword());
    }

    @Test
    void fromEnvMissingRequiredThrows() {
        Map<String, String> env = Map.of(
                "DB_USER", "app",
                "DB_PASSWORD", "secret");

        assertThrows(IllegalArgumentException.class, () -> ConsumerConfig.fromEnv(env));
    }

    @Test
    void fromEnvInvalidIntegerThrows() {
        Map<String, String> env = Map.of(
                "JDBC_URL", "jdbc:mysql://localhost:3306/app",
                "DB_USER", "app",
                "DB_PASSWORD", "secret",
                "MAX_CONCURRENT_JOBS", "not-a-number");

        assertThrows(IllegalArgumentException.class, () -> ConsumerConfig.fromEnv(env));
    }

    @Test
    void fromEnvInvalidConcurrencyThrows() {
        Map<String, String> env = Map.of(
                "JDBC_URL", "jdbc:mysql://localhost:3306/app",
                "DB_USER", "app",
                "DB_PASSWORD", "secret",
                "MAX_CONCURRENT_JOBS", "0");

        assertThrows(IllegalArgumentException.class, () -> ConsumerConfig.fromEnv(env));
    }
}

