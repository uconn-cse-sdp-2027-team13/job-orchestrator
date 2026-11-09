package edu.uconn.cse.team13.statustracker;

import java.util.List;

/**
 * The tracker's only view of the cluster: list labelled pods, then watch from that point.
 * Kept local to this module so it does not collide with the scheduler's {@code ClusterClient};
 * the two can be merged later (design doc section 5.3 lists both under one interface).
 */
public interface PodSource {

    /** Lists every pod carrying the job-id and attempt labels. */
    PodListing listPods();

    /**
     * Opens a watch that delivers changes after {@code resourceVersion}. The listener is called on
     * a library thread. Implementations call {@link PodWatchListener#onClosed} when the watch ends
     * for any reason.
     */
    PodWatch watch(String resourceVersion, PodWatchListener listener);

    /** Result of a list call plus the resource version to start the watch from. */
    record PodListing(List<PodSnapshot> pods, String resourceVersion) {
    }

    enum ChangeType { ADDED, MODIFIED, DELETED }

    record PodChange(ChangeType type, PodSnapshot pod) {
    }

    interface PodWatchListener {

        void onChange(PodChange change);

        /** The watch ended; {@code cause} is {@code null} for a clean close. */
        void onClosed(Throwable cause);
    }

    /** Handle for an open watch. */
    interface PodWatch extends AutoCloseable {

        @Override
        void close();
    }
}
