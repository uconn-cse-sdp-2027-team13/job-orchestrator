package edu.uconn.cse.team13.statustracker;

import edu.uconn.cse.team13.common.model.JobState;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.Map;

/** In-memory repository that applies the same guards as {@link JdbcStatusRepository}. */
final class FakeStatusRepository implements StatusRepository {

    static final class Job {
        JobState state;
        int attemptCount;
        Integer maxAttempts;
        Instant startedAt;
        Instant finishedAt;
        String lastError;
    }

    final Map<UUID, Job> jobs = new ConcurrentHashMap<>();
    final List<PodEventRecord> events = new CopyOnWriteArrayList<>();
    final List<AttemptOutcome> attempts = new CopyOnWriteArrayList<>();

    Job addJob(UUID id, JobState state, int attempt, Integer maxAttempts) {
        Job job = new Job();
        job.state = state;
        job.attemptCount = attempt;
        job.maxAttempts = maxAttempts;
        jobs.put(id, job);
        return job;
    }

    /** When set, {@link #findJob} throws for this job, simulating a database failure. */
    volatile UUID failingJob;

    @Override
    public Optional<JobRecord> findJob(UUID jobId) {
        if (jobId.equals(failingJob)) {
            throw new IllegalStateException("simulated database failure");
        }
        Job job = jobs.get(jobId);
        return job == null
                ? Optional.empty()
                : Optional.of(new JobRecord(jobId, job.state, job.attemptCount, job.maxAttempts));
    }

    @Override
    public synchronized boolean recordPodEventIfChanged(PodEventRecord event) {
        PodEventRecord last = null;
        for (PodEventRecord e : events) {
            if (e.jobId().equals(event.jobId()) && e.podName().equals(event.podName())) {
                last = e;
            }
        }
        if (last != null) {
            boolean lastDeleted = last.type() == PodEventType.DELETED;
            boolean repeat = event.type() == PodEventType.DELETED
                    ? lastDeleted
                    : !lastDeleted && last.phase() == event.phase();
            if (repeat) {
                return false;
            }
        }
        events.add(event);
        return true;
    }

    @Override
    public synchronized boolean markRunning(UUID jobId, int attempt, Instant startedAt) {
        Job job = jobs.get(jobId);
        if (job == null || job.attemptCount != attempt || job.state != JobState.DISPATCHED) {
            return false;
        }
        job.state = JobState.RUNNING;
        if (job.startedAt == null) {
            job.startedAt = startedAt;
        }
        return true;
    }

    @Override
    public synchronized boolean completeAttempt(AttemptOutcome o) {
        Job job = jobs.get(o.jobId());
        if (job == null || job.attemptCount != o.attempt()
                || (job.state != JobState.DISPATCHED && job.state != JobState.RUNNING)) {
            return false;
        }
        job.state = o.newState();
        if (job.startedAt == null) {
            job.startedAt = o.startedAt();
        }
        if (o.newState() == JobState.SUCCEEDED || o.newState() == JobState.DEAD_LETTERED) {
            job.finishedAt = o.endedAt();
        }
        if (o.lastError() != null) {
            job.lastError = o.lastError();
        }
        attempts.add(o);
        return true;
    }

    @Override
    public List<ActiveAttempt> findActiveAttempts() {
        List<ActiveAttempt> result = new ArrayList<>();
        jobs.forEach((id, job) -> {
            if (job.state == JobState.DISPATCHED || job.state == JobState.RUNNING) {
                result.add(new ActiveAttempt(id, job.attemptCount, job.state));
            }
        });
        return result;
    }
}
