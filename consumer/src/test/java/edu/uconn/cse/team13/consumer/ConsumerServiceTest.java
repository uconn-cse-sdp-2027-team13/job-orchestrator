package edu.uconn.cse.team13.consumer;

import edu.uconn.cse.team13.common.model.JobState;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ConsumerServiceTest {

    @Test
    void testDispatchedState() {
        ConsumerService service = new ConsumerService();
        assertEquals(JobState.DISPATCHED, service.getDispatchedState());
    }
}

