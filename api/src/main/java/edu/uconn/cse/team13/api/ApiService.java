package edu.uconn.cse.team13.api;

import edu.uconn.cse.team13.common.model.JobState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ApiService {
    private static final Logger logger = LoggerFactory.getLogger(ApiService.class);

    public boolean isTerminal(JobState state) {
        logger.info("Checking terminal status for state: {}", state);
        return state == JobState.SUCCEEDED 
            || state == JobState.DEAD_LETTERED 
            || state == JobState.CANCELLED;
    }
}

