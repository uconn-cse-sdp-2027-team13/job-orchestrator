package edu.uconn.cse.team13.producer;

import edu.uconn.cse.team13.common.model.JobState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProducerService {
    private static final Logger logger = LoggerFactory.getLogger(ProducerService.class);

    public JobState getInitialState() {
        logger.info("Initializing producer job state");
        return JobState.QUEUED;
    }
}

