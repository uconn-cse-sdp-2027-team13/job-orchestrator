package edu.uconn.cse.team13.statustracker;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatusTrackerConfigTest {

    @Test
    void fromEnvWithDefaults() {
        Map<String, String> env = Map.of(
                "JDBC_URL", "jdbc:mysql://localhost:3306/app",
                "DB_USER", "app",
                "DB_PASSWORD", "secret");

        StatusTrackerConfig config = StatusTrackerConfig.fromEnv(env);

        assertEquals("jdbc:mysql://localhost:3306/app", config.jdbcUrl());
        assertEquals("app", config.dbUser());
        assertEquals("secret", config.dbPassword());
        assertNull(config.namespace());
        assertEquals("job-workload", config.workloadContainer());
        assertEquals(Set.of(2), config.nonRetriableExitCodes());
        assertEquals(3, config.retryPolicy().defaultMaxAttempts());
        assertEquals(Duration.ofSeconds(60), config.retryPolicy().initialDelay());
        assertEquals(Duration.ofSeconds(2), config.minBackoff());
        assertEquals(Duration.ofSeconds(60), config.maxBackoff());
    }

    @Test
    void fromEnvWithOverrides() {
        Map<String, String> env = Map.of(
                "JDBC_URL", "jdbc:mysql://localhost:3306/app",
                "DB_USER", "app",
                "DB_PASSWORD", "secret",
                "K8S_NAMESPACE", "custom-ns",
                "WORKLOAD_CONTAINER", "custom-container",
                "NON_RETRIABLE_EXIT_CODES", "2,20,42",
                "MAX_ATTEMPTS", "5",
                "RETRY_INITIAL_DELAY_SECONDS", "30",
                "RECONNECT_MIN_SECONDS", "5",
                "RECONNECT_MAX_SECONDS", "120");

        StatusTrackerConfig config = StatusTrackerConfig.fromEnv(env);

        assertEquals("custom-ns", config.namespace());
        assertEquals("custom-container", config.workloadContainer());
        assertEquals(Set.of(2, 20, 42), config.nonRetriableExitCodes());
        assertEquals(5, config.retryPolicy().defaultMaxAttempts());
        assertEquals(Duration.ofSeconds(30), config.retryPolicy().initialDelay());
        assertEquals(Duration.ofSeconds(5), config.minBackoff());
        assertEquals(Duration.ofSeconds(120), config.maxBackoff());
    }

    @Test
    void fromEnvWithMysqlUrlFallback() {
        Map<String, String> env = Map.of(
                "MYSQL_URL", "jdbc:mysql://cluster-db:3306/app",
                "MYSQL_USER", "cluster_user",
                "MYSQL_PASSWORD", "cluster_pass");

        StatusTrackerConfig config = StatusTrackerConfig.fromEnv(env);

        assertEquals("jdbc:mysql://cluster-db:3306/app", config.jdbcUrl());
        assertEquals("cluster_user", config.dbUser());
        assertEquals("cluster_pass", config.dbPassword());
    }

    @Test
    void fromEnvMissingRequiredThrows() {
        Map<String, String> env = Map.of(
                "DB_USER", "app",
                "DB_PASSWORD", "secret");

        assertThrows(IllegalArgumentException.class, () -> StatusTrackerConfig.fromEnv(env));
    }

    @Test
    void fromEnvInvalidIntegerThrows() {
        Map<String, String> env = Map.of(
                "JDBC_URL", "jdbc:mysql://localhost:3306/app",
                "DB_USER", "app",
                "DB_PASSWORD", "secret",
                "MAX_ATTEMPTS", "three");

        assertThrows(IllegalArgumentException.class, () -> StatusTrackerConfig.fromEnv(env));
    }
}

