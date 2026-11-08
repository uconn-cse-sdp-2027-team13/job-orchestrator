package edu.uconn.cse.team13.statustracker;

import java.time.Duration;
import java.util.Objects;

/**
 * Attempt-level retry policy (design doc section 8.2). Proposed defaults, pending sponsor
 * confirmation: three attempts per job, delays growing from about one minute.
 *
 * @param defaultMaxAttempts used when the job row has no {@code max_attempts}
 * @param initialDelay delay before the second attempt
 * @param multiplier growth factor per failed attempt
 * @param maxDelay upper bound on any delay
 */
public record RetryPolicy(
        int defaultMaxAttempts,
        Duration initialDelay,
        double multiplier,
        Duration maxDelay) {

    public enum Decision { RETRY, DEAD_LETTER }

    public RetryPolicy {
        if (defaultMaxAttempts < 1) {
            throw new IllegalArgumentException("defaultMaxAttempts must be at least 1");
        }
        Objects.requireNonNull(initialDelay, "initialDelay must not be null");
        Objects.requireNonNull(maxDelay, "maxDelay must not be null");
        if (multiplier < 1.0) {
            throw new IllegalArgumentException("multiplier must be at least 1.0");
        }
    }

    public static RetryPolicy defaults() {
        return new RetryPolicy(3, Duration.ofSeconds(60), 2.0, Duration.ofMinutes(15));
    }

    /**
     * Non-retriable failures skip straight to dead-letter; otherwise retry while attempts remain.
     *
     * @param attempt the attempt that just failed (1-based)
     * @param jobMaxAttempts {@code job.max_attempts}, or {@code null} to use the default
     */
    public Decision decide(int attempt, Integer jobMaxAttempts, boolean retriable) {
        if (!retriable) {
            return Decision.DEAD_LETTER;
        }
        int max = jobMaxAttempts != null && jobMaxAttempts >= 1 ? jobMaxAttempts : defaultMaxAttempts;
        return attempt < max ? Decision.RETRY : Decision.DEAD_LETTER;
    }

    /** Delay before the next attempt after {@code failedAttempt} failed. */
    public Duration backoff(int failedAttempt) {
        double factor = Math.pow(multiplier, Math.max(0, failedAttempt - 1));
        double seconds = initialDelay.toMillis() / 1000.0 * factor;
        long millis = (long) Math.min(seconds * 1000.0, (double) maxDelay.toMillis());
        return Duration.ofMillis(millis);
    }
}
