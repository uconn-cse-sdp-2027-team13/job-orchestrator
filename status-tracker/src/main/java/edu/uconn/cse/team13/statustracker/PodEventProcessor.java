package edu.uconn.cse.team13.statustracker;

import edu.uconn.cse.team13.common.model.JobState;
import edu.uconn.cse.team13.statustracker.StatusRepository.AttemptOutcome;
import edu.uconn.cse.team13.statustracker.StatusRepository.JobRecord;
import edu.uconn.cse.team13.statustracker.StatusRepository.PodEventRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Turns one pod snapshot into MySQL writes. Both the live watch and startup reconciliation call
 * {@link #handle}, so there is exactly one place that decides what a pod means for its job.
 *
 * <p>Rules (design doc 5.2 steps 4 and 6, 6.3, 7.6, 8.1):
 * <ul>
 *   <li>Every phase change is appended to pod_event, including events for stale attempts.
 *   <li>Only the pod of the job's <em>current</em> attempt may move the job, and only while the job
 *       is DISPATCHED or RUNNING. Anything else is a replay or a late event and changes nothing.
 *   <li>Running moves DISPATCHED to RUNNING. Succeeded moves to SUCCEEDED. Failed (or a pod deleted
 *       before finishing) moves to RETRY_WAIT while attempts remain and the failure is retriable,
 *       otherwise to DEAD_LETTERED, so no job is left silently stuck.
 *   <li>Closing an attempt writes one job_attempt row, once (append-only, doc 6.5).
 * </ul>
 *
 * <p>Methods are synchronized: the watch thread and the reconcile thread share one processor.
 */
public final class PodEventProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PodEventProcessor.class);

    private final StatusRepository repository;
    private final RetryPolicy retryPolicy;
    private final ExitCodePolicy exitCodePolicy;
    private final Clock clock;

    public PodEventProcessor(
            StatusRepository repository,
            RetryPolicy retryPolicy,
            ExitCodePolicy exitCodePolicy,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.retryPolicy = Objects.requireNonNull(retryPolicy, "retryPolicy must not be null");
        this.exitCodePolicy = Objects.requireNonNull(exitCodePolicy, "exitCodePolicy must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * @return the new job state if this pod moved the job, otherwise empty
     */
    public synchronized Optional<JobState> handle(PodSnapshot pod, PodEventType type) {
        if (!pod.hasJobIdentity()) {
            logger.warn("Ignoring pod {} without valid job-id/attempt labels", pod.podName());
            return Optional.empty();
        }
        Optional<JobRecord> found = repository.findJob(pod.jobId());
        if (found.isEmpty()) {
            logger.warn("Pod {} refers to unknown job {}; ignoring", pod.podName(), pod.jobId());
            return Optional.empty();
        }
        JobRecord job = found.get();

        boolean recorded = repository.recordPodEventIfChanged(new PodEventRecord(
                pod.jobId(), pod.podName(), type, pod.phase(), describe(pod), clock.instant()));
        if (recorded) {
            logger.info("pod_event jobId={} attempt={} pod={} phase={} type={}",
                    pod.jobId(), pod.attempt(), pod.podName(), pod.phase(), type);
        }

        if (pod.attempt() != job.attemptCount()) {
            logger.debug("Pod {} is attempt {} but job {} is on attempt {}; not changing job state",
                    pod.podName(), pod.attempt(), job.jobId(), job.attemptCount());
            return Optional.empty();
        }
        if (job.state() != JobState.DISPATCHED && job.state() != JobState.RUNNING) {
            return Optional.empty();
        }

        if (type == PodEventType.DELETED && !pod.phase().isTerminal()) {
            return fail(pod, job, "PodDeleted", "Pod was deleted before it finished", null, true);
        }
        return switch (pod.phase()) {
            case RUNNING -> running(pod, job);
            case SUCCEEDED -> succeeded(pod, job);
            case FAILED -> failed(pod, job);
            case PENDING, UNKNOWN -> Optional.empty();
        };
    }

    private Optional<JobState> running(PodSnapshot pod, JobRecord job) {
        if (job.state() != JobState.DISPATCHED) {
            return Optional.empty();
        }
        Instant startedAt = pod.startedAt() != null ? pod.startedAt() : clock.instant();
        if (repository.markRunning(job.jobId(), job.attemptCount(), startedAt)) {
            logger.info("job {} attempt {} -> RUNNING", job.jobId(), job.attemptCount());
            return Optional.of(JobState.RUNNING);
        }
        return Optional.empty();
    }

    private Optional<JobState> succeeded(PodSnapshot pod, JobRecord job) {
        AttemptOutcome outcome = new AttemptOutcome(
                job.jobId(), job.attemptCount(), JobState.SUCCEEDED,
                pod.podName(), pod.nodeName(), pod.startedAt(), endedAt(pod),
                pod.exitCode() != null ? pod.exitCode() : ExitCodePolicy.SUCCESS, null, null);
        return apply(outcome);
    }

    private Optional<JobState> failed(PodSnapshot pod, JobRecord job) {
        String reason = pod.reason() != null ? pod.reason() : "Failed";
        boolean retriable = exitCodePolicy.isRetriable(pod.exitCode());
        return fail(pod, job, reason, pod.message(), pod.exitCode(), retriable);
    }

    private Optional<JobState> fail(
            PodSnapshot pod,
            JobRecord job,
            String reason,
            String detail,
            Integer exitCode,
            boolean retriable) {
        RetryPolicy.Decision decision =
                retryPolicy.decide(job.attemptCount(), job.maxAttempts(), retriable);
        JobState next = decision == RetryPolicy.Decision.RETRY
                ? JobState.RETRY_WAIT
                : JobState.DEAD_LETTERED;
        String lastError = detail == null || detail.isBlank() ? reason : reason + ": " + detail;

        AttemptOutcome outcome = new AttemptOutcome(
                job.jobId(), job.attemptCount(), next,
                pod.podName(), pod.nodeName(), pod.startedAt(), endedAt(pod),
                exitCode, reason, lastError);
        Optional<JobState> applied = apply(outcome);
        if (applied.isPresent() && next == JobState.RETRY_WAIT) {
            logger.info("job {} attempt {} failed ({}); next attempt after about {}",
                    job.jobId(), job.attemptCount(), reason, retryPolicy.backoff(job.attemptCount()));
        }
        return applied;
    }

    private Optional<JobState> apply(AttemptOutcome outcome) {
        if (repository.completeAttempt(outcome)) {
            logger.info("job {} attempt {} -> {}", outcome.jobId(), outcome.attempt(), outcome.newState());
            return Optional.of(outcome.newState());
        }
        return Optional.empty();
    }

    private Instant endedAt(PodSnapshot pod) {
        return pod.finishedAt() != null ? pod.finishedAt() : clock.instant();
    }

    private static String describe(PodSnapshot pod) {
        StringBuilder text = new StringBuilder();
        if (pod.reason() != null) {
            text.append(pod.reason());
        }
        if (pod.exitCode() != null) {
            if (text.length() > 0) {
                text.append("; ");
            }
            text.append("exitCode=").append(pod.exitCode());
        }
        if (pod.message() != null && !pod.message().isBlank()) {
            if (text.length() > 0) {
                text.append("; ");
            }
            text.append(pod.message());
        }
        return text.length() == 0 ? null : text.toString();
    }
}
