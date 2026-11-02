package edu.uconn.cse.team13.common.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExitCodeTest {

    @Test
    void knownCodesMapToTheRightConstant() {
        assertEquals(ExitCode.SUCCESS, ExitCode.fromCode(0));
        assertEquals(ExitCode.RETRYABLE_FAILURE, ExitCode.fromCode(10));
        assertEquals(ExitCode.PERMANENT_FAILURE, ExitCode.fromCode(20));
    }

    @Test
    void unknownCodeIsTreatedAsRetryable() {
        assertEquals(ExitCode.RETRYABLE_FAILURE, ExitCode.fromCode(137));
    }

    @Test
    void onlyRetryableFailureShouldRetry() {
        assertTrue(ExitCode.RETRYABLE_FAILURE.shouldRetry());
        assertFalse(ExitCode.SUCCESS.shouldRetry());
        assertFalse(ExitCode.PERMANENT_FAILURE.shouldRetry());
    }
}