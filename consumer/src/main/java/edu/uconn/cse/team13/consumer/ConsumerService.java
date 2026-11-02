package edu.uconn.cse.team13.consumer;

import edu.uconn.cse.team13.common.model.JobState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConsumerService {
    private static final Logger logger = LoggerFactory.getLogger(ConsumerService.class);

    public JobState getDispatchedState() {
        logger.info("Transitioning job state to DISPATCHED");
        return JobState.DISPATCHED;
    }
}

