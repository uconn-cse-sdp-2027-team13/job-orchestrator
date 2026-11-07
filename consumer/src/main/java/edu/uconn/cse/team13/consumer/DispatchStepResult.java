package edu.uconn.cse.team13.consumer;

/** Outcome of one consumer dispatch attempt. */
public enum DispatchStepResult {
    /** Concurrent running-job limit reached; did not read from the queue. */
    WAITING_FOR_CAPACITY,
    /** Blocking read returned no message. */
    IDLE,
    /** Scheduler create + DISPATCHED persistence + ack all succeeded. */
    DISPATCHED,
    /** Broker entry could not be mapped to a valid message; left unacked. */
    INVALID_MESSAGE,
    /** Schedule or state persistence failed; left unacked. */
    DISPATCH_FAILED
}
