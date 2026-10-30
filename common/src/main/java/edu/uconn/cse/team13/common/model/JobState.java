package edu.uconn.cse.team13.common.model;

/**
 * Represents the lifecycle states of a batch job according to the system design specification.
 */
public enum JobState {
    QUEUED,
    DISPATCHED,
    RUNNING,
    SUCCEEDED,
    RETRY_WAIT,
    DEAD_LETTERED,
    CANCELLED
}

