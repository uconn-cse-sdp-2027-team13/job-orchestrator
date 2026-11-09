package edu.uconn.cse.team13.statustracker;

import com.zaxxer.hikari.HikariDataSource;
import edu.uconn.cse.team13.common.db.DataSourceFactory;
import edu.uconn.cse.team13.common.model.JobState;
import edu.uconn.cse.team13.statustracker.StatusRepository.AttemptOutcome;
import edu.uconn.cse.team13.statustracker.StatusRepository.JobRecord;
import edu.uconn.cse.team13.statustracker.StatusRepository.PodEventRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the repository's SQL against H2 in MySQL mode. The DDL below is copied from the
 * platform-infra schema (job, job_attempt, pod_event); keep it in sync with the migrations.
 */
class JdbcStatusRepositoryTest {

    private static final Instant T0 = Instant.parse("2026-11-08T12:00:00Z");
    private static final Instant T1 = Instant.parse("2026-11-08T12:05:00Z");

    private HikariDataSource dataSource;
    private JdbcStatusRepository repository;
    private UUID jobId;

    @BeforeEach
    void setUp() throws SQLException {
        dataSource = (HikariDataSource) DataSourceFactory.create(
                "jdbc:h2:mem:tracker_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.execute("""
                    CREATE TABLE job (
                        job_id VARCHAR(36) PRIMARY KEY,
                        file_name VARCHAR(255) NOT NULL,
                        file_path VARCHAR(1024) NOT NULL,
                        `state` ENUM ('QUEUED', 'DISPATCHED', 'RUNNING', 'SUCCEEDED', 'RETRY_WAIT',
                                      'DEAD_LETTERED', 'CANCELLED') NOT NULL DEFAULT 'QUEUED',
                        attempt_count INT UNSIGNED,
                        max_attempts INT UNSIGNED,
                        k8s_job_name VARCHAR(255),
                        created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                        started_at DATETIME(3),
                        finished_at DATETIME(3),
                        updated_at DATETIME(3),
                        last_error TEXT
                    )""");
            s.execute("""
                    CREATE TABLE job_attempt (
                        attempt_id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
                        job_id VARCHAR(36) NOT NULL,
                        attempt_number INT UNSIGNED NOT NULL,
                        pod_name VARCHAR(255),
                        node_name VARCHAR(255),
                        started_at DATETIME(3) NOT NULL,
                        ended_at DATETIME(3),
                        exit_code INT,
                        failure_reason TEXT,
                        CONSTRAINT fk_attempt_job FOREIGN KEY (job_id) REFERENCES job(job_id)
                    )""");
            s.execute("""
                    CREATE TABLE pod_event (
                        event_id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
                        job_id VARCHAR(36) NOT NULL,
                        pod_name VARCHAR(255) NOT NULL,
                        event_type ENUM ('NORMAL', 'WARNING') NOT NULL,
                        pod_phase ENUM ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED', 'UNKNOWN') NOT NULL,
                        `message` TEXT,
                        observed_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                        CONSTRAINT fk_event_job FOREIGN KEY (job_id) REFERENCES job(job_id)
                    )""");
        }
        repository = new JdbcStatusRepository(dataSource);
        jobId = UUID.randomUUID();
    }

    @AfterEach
    void tearDown() {
        dataSource.close();
    }

    @Test
    void findJobReadsStateAttemptAndNullableMaxAttempts() throws SQLException {
        insertJob(jobId, JobState.DISPATCHED, 2, null);

        JobRecord job = repository.findJob(jobId).orElseThrow();

        assertEquals(JobState.DISPATCHED, job.state());
        assertEquals(2, job.attemptCount());
        assertNull(job.maxAttempts());
        assertTrue(repository.findJob(UUID.randomUUID()).isEmpty());
    }

    @Test
    void podEventsAreDeduplicatedAgainstTheLastRowForThatPod() throws SQLException {
        insertJob(jobId, JobState.DISPATCHED, 1, 3);

        assertTrue(repository.recordPodEventIfChanged(event(PodEventType.OBSERVED, PodPhase.PENDING)));
        assertFalse(repository.recordPodEventIfChanged(event(PodEventType.OBSERVED, PodPhase.PENDING)));
        assertFalse(repository.recordPodEventIfChanged(event(PodEventType.RECONCILED, PodPhase.PENDING)));
        assertTrue(repository.recordPodEventIfChanged(event(PodEventType.OBSERVED, PodPhase.RUNNING)));
        assertTrue(repository.recordPodEventIfChanged(event(PodEventType.DELETED, PodPhase.RUNNING)));
        assertFalse(repository.recordPodEventIfChanged(event(PodEventType.DELETED, PodPhase.RUNNING)));

        assertEquals(3, count("pod_event"));
    }

    @Test
    void podEventsStoreSeverityAndMarkDeletionsInTheMessage() throws SQLException {
        insertJob(jobId, JobState.RUNNING, 1, 3);

        repository.recordPodEventIfChanged(event(PodEventType.OBSERVED, PodPhase.RUNNING));
        repository.recordPodEventIfChanged(new PodEventRecord(
                jobId, "pod-a", PodEventType.DELETED, PodPhase.RUNNING, "Evicted", T1));

        assertEquals("NORMAL,RUNNING,null|WARNING,RUNNING,Pod deleted before finishing; Evicted", podEvents());
    }

    @Test
    void failedPodIsAWarningAndItsLaterCleanupIsNotRecorded() throws SQLException {
        insertJob(jobId, JobState.RUNNING, 1, 3);

        assertTrue(repository.recordPodEventIfChanged(event(PodEventType.OBSERVED, PodPhase.FAILED)));
        assertFalse(repository.recordPodEventIfChanged(event(PodEventType.DELETED, PodPhase.FAILED)));

        assertEquals("WARNING,FAILED,null", podEvents());
    }

    @Test
    void markRunningOnlyAppliesToTheCurrentAttemptInDispatched() throws SQLException {
        insertJob(jobId, JobState.DISPATCHED, 2, 3);

        assertFalse(repository.markRunning(jobId, 1, T0));
        assertTrue(repository.markRunning(jobId, 2, T0));
        assertFalse(repository.markRunning(jobId, 2, T0));

        assertEquals("RUNNING", readString("state"));
        assertNotNull(readString("started_at"));
    }

    @Test
    void completeAttemptWritesJobAndAttemptRowOnceAndIsIdempotent() throws SQLException {
        insertJob(jobId, JobState.RUNNING, 1, 3);
        AttemptOutcome outcome = new AttemptOutcome(
                jobId, 1, JobState.SUCCEEDED, "pod-a", "node-1", T0, T1, 0, null, null);

        assertTrue(repository.completeAttempt(outcome));
        assertFalse(repository.completeAttempt(outcome));

        assertEquals("SUCCEEDED", readString("state"));
        assertNotNull(readString("finished_at"));
        assertEquals(1, count("job_attempt"));
    }

    @Test
    void retryWaitKeepsFinishedAtEmptyAndStoresLastError() throws SQLException {
        insertJob(jobId, JobState.RUNNING, 1, 3);

        assertTrue(repository.completeAttempt(new AttemptOutcome(
                jobId, 1, JobState.RETRY_WAIT, "pod-a", "node-1", T0, T1, 137,
                "OOMKilled", "OOMKilled: container exceeded memory limit")));

        assertEquals("RETRY_WAIT", readString("state"));
        assertNull(readString("finished_at"));
        assertEquals("OOMKilled: container exceeded memory limit", readString("last_error"));
        assertEquals(1, count("job_attempt"));
    }

    @Test
    void attemptThatNeverStartedUsesItsEndTimeForStartedAt() throws SQLException {
        insertJob(jobId, JobState.DISPATCHED, 1, 3);

        assertTrue(repository.completeAttempt(new AttemptOutcome(
                jobId, 1, JobState.RETRY_WAIT, "pod-a", null, null, T1, null,
                "PodDeleted", "PodDeleted: Pod was deleted before it finished")));

        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT started_at, ended_at FROM job_attempt WHERE job_id = ?")) {
            ps.setString(1, jobId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(rs.getTimestamp("ended_at"), rs.getTimestamp("started_at"));
            }
        }
    }

    @Test
    void jobWithNoAttemptYetIsReadAsAttemptZero() throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO job (job_id, file_name, file_path) VALUES (?, 'f.csv', 'p/f.csv')")) {
            ps.setString(1, jobId.toString());
            ps.executeUpdate();
        }

        JobRecord job = repository.findJob(jobId).orElseThrow();

        assertEquals(JobState.QUEUED, job.state());
        assertEquals(0, job.attemptCount());
    }

    @Test
    void completeAttemptForAStaleAttemptChangesNothing() throws SQLException {
        insertJob(jobId, JobState.RUNNING, 2, 3);

        assertFalse(repository.completeAttempt(new AttemptOutcome(
                jobId, 1, JobState.RETRY_WAIT, "old-pod", null, T0, T1, 1, "Error", "Error")));

        assertEquals("RUNNING", readString("state"));
        assertEquals(0, count("job_attempt"));
    }

    @Test
    void findActiveAttemptsListsDispatchedAndRunningOnly() throws SQLException {
        UUID running = UUID.randomUUID();
        insertJob(jobId, JobState.DISPATCHED, 1, 3);
        insertJob(running, JobState.RUNNING, 2, 3);
        insertJob(UUID.randomUUID(), JobState.SUCCEEDED, 1, 3);
        insertJob(UUID.randomUUID(), JobState.RETRY_WAIT, 1, 3);

        assertEquals(2, repository.findActiveAttempts().size());
    }

    private PodEventRecord event(PodEventType type, PodPhase phase) {
        return new PodEventRecord(jobId, "pod-a", type, phase, null, T0);
    }

    private void insertJob(UUID id, JobState state, int attempt, Integer max) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     """
                     INSERT INTO job (job_id, file_name, file_path, state, attempt_count, max_attempts)
                     VALUES (?, 'f.csv', 'p/f.csv', ?, ?, ?)
                     """)) {
            ps.setString(1, id.toString());
            ps.setString(2, state.name());
            ps.setInt(3, attempt);
            if (max == null) {
                ps.setNull(4, java.sql.Types.INTEGER);
            } else {
                ps.setInt(4, max);
            }
            ps.executeUpdate();
        }
    }

    /** pod_event rows for the job as "type,phase,message" joined by "|", oldest first. */
    private String podEvents() throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT event_type, pod_phase, message FROM pod_event WHERE job_id = ? ORDER BY event_id")) {
            ps.setString(1, jobId.toString());
            StringBuilder rows = new StringBuilder();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    if (rows.length() > 0) {
                        rows.append('|');
                    }
                    rows.append(rs.getString(1)).append(',').append(rs.getString(2)).append(',')
                            .append(rs.getString(3));
                }
            }
            return rows.toString();
        }
    }

    private String readString(String column) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT " + column + " FROM job WHERE job_id = ?")) {
            ps.setString(1, jobId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getString(1);
            }
        }
    }

    private int count(String table) throws SQLException {
        try (Connection c = dataSource.getConnection();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
