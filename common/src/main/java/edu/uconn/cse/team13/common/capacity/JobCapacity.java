package edu.uconn.cse.team13.common.capacity;

/**
 * Cluster-wide admission check for concurrent running jobs.
 * Implementations must use a shared source of truth across consumer replicas;
 * a process-local counter is not sufficient.
 */
public interface JobCapacity {

    /** {@code true} when another job may be dispatched without exceeding the configured limit. */
    boolean hasAvailableSlot();
}
