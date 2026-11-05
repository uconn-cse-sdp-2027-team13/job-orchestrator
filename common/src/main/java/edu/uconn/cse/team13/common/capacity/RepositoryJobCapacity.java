package edu.uconn.cse.team13.common.capacity;

import edu.uconn.cse.team13.common.repository.JobRepository;

import java.util.Objects;

/**
 * Shared concurrency gate: allows dispatch while active jobs ({@code DISPATCHED}+{@code RUNNING})
 * are below a configured limit. Backed by MySQL via {@link JobRepository}, not a local counter.
 */
public final class RepositoryJobCapacity implements JobCapacity {

    private final JobRepository jobRepository;
    private final int maxActiveJobs;

    public RepositoryJobCapacity(JobRepository jobRepository, int maxActiveJobs) {
        this.jobRepository = Objects.requireNonNull(jobRepository, "jobRepository must not be null");
        if (maxActiveJobs < 1) {
            throw new IllegalArgumentException("maxActiveJobs must be at least 1");
        }
        this.maxActiveJobs = maxActiveJobs;
    }

    public int maxActiveJobs() {
        return maxActiveJobs;
    }

    @Override
    public boolean hasAvailableSlot() {
        return jobRepository.countActiveJobs() < maxActiveJobs;
    }
}
