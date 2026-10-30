package edu.uconn.cse.team13.common.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class JobStateTest {

    @Test
    void testEnumValuesExist() {
        assertNotNull(JobState.valueOf("QUEUED"));
        assertNotNull(JobState.valueOf("RUNNING"));
        assertNotNull(JobState.valueOf("SUCCEEDED"));
    }
}

