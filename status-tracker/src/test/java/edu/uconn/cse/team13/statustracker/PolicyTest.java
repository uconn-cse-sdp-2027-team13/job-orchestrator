package edu.uconn.cse.team13.statustracker;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PolicyTest {

    @Test
    void retriesWhileAttemptsRemainThenDeadLetters() {
        RetryPolicy policy = RetryPolicy.defaults();

        assertEquals(RetryPolicy.Decision.RETRY, policy.decide(1, 3, true));
        assertEquals(RetryPolicy.Decision.RETRY, policy.decide(2, 3, true));
        assertEquals(RetryPolicy.Decision.DEAD_LETTER, policy.decide(3, 3, true));
    }

    @Test
    void nonRetriableFailureDeadLettersImmediately() {
        assertEquals(RetryPolicy.Decision.DEAD_LETTER, RetryPolicy.defaults().decide(1, 3, false));
    }

    @Test
    void usesDefaultMaxWhenJobHasNone() {
        RetryPolicy policy = new RetryPolicy(2, Duration.ofSeconds(1), 2.0, Duration.ofMinutes(1));

        assertEquals(RetryPolicy.Decision.RETRY, policy.decide(1, null, true));
        assertEquals(RetryPolicy.Decision.DEAD_LETTER, policy.decide(2, null, true));
    }

    @Test
    void backoffGrowsFromOneMinuteAndIsCapped() {
        RetryPolicy policy = RetryPolicy.defaults();

        assertEquals(Duration.ofSeconds(60), policy.backoff(1));
        assertEquals(Duration.ofSeconds(120), policy.backoff(2));
        assertEquals(Duration.ofSeconds(240), policy.backoff(3));
        assertEquals(Duration.ofMinutes(15), policy.backoff(10));
    }

    @Test
    void exitCodePolicyTreatsOnlyConfiguredCodesAsNonRetriable() {
        ExitCodePolicy policy = ExitCodePolicy.defaults();

        assertFalse(policy.isRetriable(2));
        assertTrue(policy.isRetriable(1));
        assertTrue(policy.isRetriable(137));
        assertTrue(policy.isRetriable(143));
        assertTrue(policy.isRetriable(null));
        assertFalse(new ExitCodePolicy(Set.of(65)).isRetriable(65));
    }

    @Test
    void phaseParsingIsTolerant() {
        assertEquals(PodPhase.RUNNING, PodPhase.parse("Running"));
        assertEquals(PodPhase.UNKNOWN, PodPhase.parse("Weird"));
        assertEquals(PodPhase.UNKNOWN, PodPhase.parse(null));
        assertTrue(PodPhase.FAILED.isTerminal());
        assertFalse(PodPhase.RUNNING.isTerminal());
    }

    @Test
    void configReadsRequiredAndOptionalVariables() {
        StatusTrackerConfig config = StatusTrackerConfig.fromEnv(Map.of(
                "JDBC_URL", "jdbc:mysql://db/jobs",
                "DB_USER", "tracker",
                "DB_PASSWORD", "secret",
                "NON_RETRIABLE_EXIT_CODES", "2, 65",
                "MAX_ATTEMPTS", "5"));

        assertEquals("job-workload", config.workloadContainer());
        assertEquals(Set.of(2, 65), config.nonRetriableExitCodes());
        assertEquals(5, config.retryPolicy().defaultMaxAttempts());
        assertEquals(null, config.namespace());
    }

    @Test
    void configRejectsMissingRequiredVariable() {
        assertThrows(IllegalArgumentException.class,
                () -> StatusTrackerConfig.fromEnv(Map.of("JDBC_URL", "x", "DB_USER", "y")));
    }
}
