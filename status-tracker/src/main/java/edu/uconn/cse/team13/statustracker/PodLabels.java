package edu.uconn.cse.team13.statustracker;

/**
 * Pod contract labels (design doc section 5.3, "Pod contract"). The scheduler stamps every
 * pod with these two labels and the tracker selects on them, so both sides must agree.
 * The doc fixes the meaning (job id, attempt) but not the key names; these are proposals.
 */
public final class PodLabels {

    public static final String JOB_ID = "job-id";
    public static final String ATTEMPT = "attempt";

    /** Existence selector: any pod carrying both labels belongs to the orchestrator. */
    public static final String SELECTOR = JOB_ID + "," + ATTEMPT;

    private PodLabels() {
    }
}
