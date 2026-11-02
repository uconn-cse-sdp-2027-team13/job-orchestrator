package edu.uconn.cse.team13.common.model;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class JobMessageTest {

    private static final Instant NOW = Instant.parse("2026-11-01T12:00:00Z");

    @Test
    void validMessageKeepsItsValues() {
        JobMessage message = new JobMessage("job-1", "input.csv", 1, NOW);
        assertEquals("job-1", message.jobId());
        assertEquals("input.csv", message.filePath());
        assertEquals(1, message.attempt());
    }

    @Test
    void blankJobIdIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new JobMessage(" ", "input.csv", 1, NOW));
    }

    @Test
    void nullFilePathIsRejected() {
        assertThrows(NullPointerException.class,
                () -> new JobMessage("job-1", null, 1, NOW));
    }

    @Test
    void attemptBelowOneIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new JobMessage("job-1", "input.csv", 0, NOW));
    }

    @Test
    void nextAttemptMakesNewMessageAndLeavesOriginalAlone() {
        JobMessage first = new JobMessage("job-1", "input.csv", 1, NOW);
        JobMessage second = first.nextAttempt();
        assertEquals(1, first.attempt());
        assertEquals(2, second.attempt());
    }
}