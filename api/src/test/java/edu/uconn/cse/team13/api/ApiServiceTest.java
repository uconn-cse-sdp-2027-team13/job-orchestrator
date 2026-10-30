package edu.uconn.cse.team13.api;

import edu.uconn.cse.team13.common.model.JobState;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiServiceTest {

    @Test
    void testIsTerminal() {
        ApiService service = new ApiService();
        assertTrue(service.isTerminal(JobState.SUCCEEDED));
        assertTrue(service.isTerminal(JobState.DEAD_LETTERED));
        assertTrue(service.isTerminal(JobState.CANCELLED));
        assertFalse(service.isTerminal(JobState.QUEUED));
        assertFalse(service.isTerminal(JobState.RUNNING));
    }
}

