package edu.uconn.cse.team13.statustracker;

import java.util.Locale;

/** Kubernetes pod phases as the tracker sees them. */
public enum PodPhase {
    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED,
    UNKNOWN;

    /** Parses the {@code status.phase} string; anything unrecognized becomes {@link #UNKNOWN}. */
    public static PodPhase parse(String value) {
        if (value == null) {
            return UNKNOWN;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return UNKNOWN;
        }
    }

    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED;
    }
}
