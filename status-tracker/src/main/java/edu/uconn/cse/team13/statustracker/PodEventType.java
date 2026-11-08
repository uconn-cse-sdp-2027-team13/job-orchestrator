package edu.uconn.cse.team13.statustracker;

/**
 * Where a pod event came from. The schema's {@code pod_event.event_type} only stores severity
 * (NORMAL/WARNING), so {@link JdbcStatusRepository} maps these and marks deletions in the message.
 */
public enum PodEventType {
    /** A phase change seen on the live watch. */
    OBSERVED,
    /** A phase found during startup/reconnect reconciliation (the tracker missed the live event). */
    RECONCILED,
    /** The pod object was deleted. */
    DELETED
}
