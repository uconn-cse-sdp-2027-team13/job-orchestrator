package edu.uconn.cse.team13.statustracker;

import edu.uconn.cse.team13.common.model.JobState;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class StatusTrackerServiceTest {

    @Test
    void testRunningState() {
        StatusTrackerService service = new StatusTrackerService();
        assertEquals(JobState.RUNNING, service.getRunningState());
    }
}

