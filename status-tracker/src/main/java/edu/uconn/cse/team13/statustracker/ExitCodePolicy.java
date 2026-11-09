package edu.uconn.cse.team13.statustracker;

import java.util.Set;

/**
 * Interprets the workload's exit code (design doc 5.3 "Pod contract", 7.5 "Report").
 *
 * <p>The doc requires distinct codes for success, retriable failure, and non-retriable failure
 * but does not assign numbers. Proposal: {@code 0} succeeds, a small configurable set means
 * "do not retry" (default {@code 2}, e.g. invalid file), and every other code is retriable. That
 * deliberately includes 1 (uncaught JVM error), 137 (OOM kill) and 143 (SIGTERM during the
 * graceful shutdown in 8.5). The workload owner must agree to these numbers.
 */
public final class ExitCodePolicy {

    public static final int SUCCESS = 0;
    public static final Set<Integer> DEFAULT_NON_RETRIABLE = Set.of(2);

    private final Set<Integer> nonRetriable;

    public ExitCodePolicy(Set<Integer> nonRetriable) {
        this.nonRetriable = Set.copyOf(nonRetriable);
    }

    public static ExitCodePolicy defaults() {
        return new ExitCodePolicy(DEFAULT_NON_RETRIABLE);
    }

    /** A missing exit code (pod killed before the container reported one) is retriable. */
    public boolean isRetriable(Integer exitCode) {
        return exitCode == null || !nonRetriable.contains(exitCode);
    }
}
