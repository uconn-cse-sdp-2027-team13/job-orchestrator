package edu.uconn.cse.team13.statustracker;

import java.time.Duration;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Runtime configuration read from environment variables (design doc 4.2: local and cloud differ
 * only by configuration).
 *
 * @param namespace namespace to watch; {@code null} means the pod's own namespace
 */
public record StatusTrackerConfig(
        String jdbcUrl,
        String dbUser,
        String dbPassword,
        String namespace,
        String workloadContainer,
        Set<Integer> nonRetriableExitCodes,
        RetryPolicy retryPolicy,
        Duration minBackoff,
        Duration maxBackoff) {

    /**
     * Variables: {@code JDBC_URL} (or {@code MYSQL_URL}), {@code DB_USER} (or {@code MYSQL_USER}),
     * {@code DB_PASSWORD} (or {@code MYSQL_PASSWORD}) (required);
     * {@code K8S_NAMESPACE}, {@code WORKLOAD_CONTAINER} (default {@code job-workload}),
     * {@code NON_RETRIABLE_EXIT_CODES} (comma list, default {@code 2}),
     * {@code MAX_ATTEMPTS} (default 3), {@code RETRY_INITIAL_DELAY_SECONDS} (default 60),
     * {@code RECONNECT_MIN_SECONDS} (default 2), {@code RECONNECT_MAX_SECONDS} (default 60).
     */
    public static StatusTrackerConfig fromEnv(Map<String, String> env) {
        RetryPolicy defaults = RetryPolicy.defaults();
        RetryPolicy retry = new RetryPolicy(
                intValue(env, "MAX_ATTEMPTS", defaults.defaultMaxAttempts()),
                Duration.ofSeconds(intValue(env, "RETRY_INITIAL_DELAY_SECONDS",
                        (int) defaults.initialDelay().toSeconds())),
                defaults.multiplier(),
                defaults.maxDelay());
        return new StatusTrackerConfig(
                required(env, "JDBC_URL", "MYSQL_URL"),
                required(env, "DB_USER", "MYSQL_USER"),
                required(env, "DB_PASSWORD", "MYSQL_PASSWORD"),
                blankToNull(env.get("K8S_NAMESPACE")),
                env.getOrDefault("WORKLOAD_CONTAINER", "job-workload"),
                exitCodes(env.get("NON_RETRIABLE_EXIT_CODES")),
                retry,
                Duration.ofSeconds(intValue(env, "RECONNECT_MIN_SECONDS", 2)),
                Duration.ofSeconds(intValue(env, "RECONNECT_MAX_SECONDS", 60)));
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

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
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

    private static Set<Integer> exitCodes(String value) {
        if (value == null || value.isBlank()) {
            return ExitCodePolicy.DEFAULT_NON_RETRIABLE;
        }
        Set<Integer> codes = new HashSet<>();
        for (String part : value.split(",")) {
            if (!part.isBlank()) {
                codes.add(Integer.parseInt(part.trim()));
            }
        }
        return codes;
    }
}
