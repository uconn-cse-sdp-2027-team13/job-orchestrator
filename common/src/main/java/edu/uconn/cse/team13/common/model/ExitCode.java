package edu.uconn.cse.team13.common.model;

/**
 * Exit codes a workload pod returns when it finishes,
 * and whether the orchestrator should retry the job.
 */
public enum ExitCode {
    SUCCESS(0),
    RETRYABLE_FAILURE(10),
    PERMANENT_FAILURE(20);

    private final int code;

    ExitCode(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    /**
     * Converts the raw number Kubernetes reports into an ExitCode.
     * Unknown codes (like 137, pod killed for memory) count as retryable
     * because the job itself may be fine.
     */
    public static ExitCode fromCode(int code) {
        for (ExitCode exitCode : values()) {
            if (exitCode.code == code) {
                return exitCode;
            }
        }
        return RETRYABLE_FAILURE;
    }

    public boolean shouldRetry() {
        return this == RETRYABLE_FAILURE;
    }
}
