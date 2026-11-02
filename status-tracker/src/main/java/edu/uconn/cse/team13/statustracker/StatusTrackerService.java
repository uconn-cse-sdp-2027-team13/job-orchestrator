package edu.uconn.cse.team13.statustracker;

import edu.uconn.cse.team13.common.model.JobState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StatusTrackerService {
    private static final Logger logger = LoggerFactory.getLogger(StatusTrackerService.class);

    public JobState getRunningState() {
        logger.info("Tracking job state transition to RUNNING");
        return JobState.RUNNING;
    }
}

