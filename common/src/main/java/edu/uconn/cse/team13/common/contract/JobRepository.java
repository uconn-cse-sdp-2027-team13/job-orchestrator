package edu.uconn.cse.team13.common.contract;

import edu.uconn.cse.team13.common.model.JobMessage;
import edu.uconn.cse.team13.common.model.JobState;
import java.util.Optional;

/**
 * Saves and reads job records in the database (MySQL in our design).
 */
public interface JobRepository {

    void create(JobMessage message);

    void updateState(String jobId, JobState newState);

    Optional<JobState> findState(String jobId);
}