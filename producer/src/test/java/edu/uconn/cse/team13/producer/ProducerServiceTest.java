package edu.uconn.cse.team13.producer;

import edu.uconn.cse.team13.common.model.JobState;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ProducerServiceTest {

    @Test
    void testInitialStateIsQueued() {
        ProducerService service = new ProducerService();
        assertEquals(JobState.QUEUED, service.getInitialState());
    }
}

