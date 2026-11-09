package edu.uconn.cse.team13.statustracker;

import edu.uconn.cse.team13.common.model.JobState;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.TimeZone;
import java.util.UUID;

/**
 * JDBC {@link StatusRepository} over the platform-infra schema (design doc section 6.4). DDL is
 * owned by the platform-infra Flyway migrations; this class only reads and writes.
 *
 * <p>Columns used: job(job_id, state, attempt_count, max_attempts, started_at, finished_at,
 * last_error, updated_at), job_attempt(job_id, attempt_number, pod_name, node_name, started_at,
 * ended_at, exit_code, failure_reason), pod_event(event_id, job_id, pod_name, event_type,
 * pod_phase, message, observed_at). {@code event_id} auto-increments, so the latest row for a pod
 * is found by ordering on it.
 *
 * <p>All times are written as UTC into {@code DATETIME(3)} columns. {@code updated_at} uses the
 * database clock, so the MySQL session time zone must be UTC too.
 */
public final class JdbcStatusRepository implements StatusRepository {

    /** pod_event.event_type values in the schema. */
    static final String SEVERITY_NORMAL = "NORMAL";
    static final String SEVERITY_WARNING = "WARNING";

    /** Prefix on pod_event.message for deletions, since event_type only carries severity. */
    static final String DELETED_PREFIX = "Pod deleted";

    /** TEXT holds 65,535 bytes; 16,000 chars fits even at 4 bytes per char in utf8mb4. */
    private static final int MAX_TEXT = 16_000;

    private final DataSource dataSource;

    public JdbcStatusRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
    }

    @Override
    public Optional<JobRecord> findJob(UUID jobId) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT state, attempt_count, max_attempts FROM job WHERE job_id = ?")) {
            ps.setString(1, jobId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                // attempt_count is NULL until the first dispatch; 0 never matches a pod's attempt.
                int attemptCount = rs.getInt("attempt_count");
                int max = rs.getInt("max_attempts");
                Integer maxAttempts = rs.wasNull() ? null : max;
                return Optional.of(new JobRecord(
                        jobId,
                        JobState.valueOf(rs.getString("state")),
                        attemptCount,
                        maxAttempts));
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to read job " + jobId, ex);
        }
    }

    @Override
    public boolean recordPodEventIfChanged(PodEventRecord event) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                if (repeatsLastEvent(c, event)) {
                    c.commit();
                    return false;
                }
                try (PreparedStatement ps = c.prepareStatement(
                        """
                        INSERT INTO pod_event (job_id, pod_name, event_type, pod_phase, message, observed_at)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """)) {
                    ps.setString(1, event.jobId().toString());
                    ps.setString(2, event.podName());
                    ps.setString(3, severity(event));
                    ps.setString(4, event.phase().name());
                    ps.setString(5, truncate(message(event)));
                    setInstant(ps, 6, event.observedAt());
                    ps.executeUpdate();
                }
                c.commit();
                return true;
            } catch (SQLException | RuntimeException ex) {
                c.rollback();
                throw ex;
            } finally {
                c.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to record pod event for " + event.podName(), ex);
        }
    }

    @Override
    public boolean markRunning(UUID jobId, int attempt, Instant startedAt) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     """
                     UPDATE job
                     SET state = ?, started_at = COALESCE(started_at, ?), updated_at = CURRENT_TIMESTAMP(3)
                     WHERE job_id = ? AND attempt_count = ? AND state = ?
                     """)) {
            ps.setString(1, JobState.RUNNING.name());
            setInstant(ps, 2, startedAt);
            ps.setString(3, jobId.toString());
            ps.setInt(4, attempt);
            ps.setString(5, JobState.DISPATCHED.name());
            return ps.executeUpdate() == 1;
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to mark RUNNING: " + jobId, ex);
        }
    }

    @Override
    public boolean completeAttempt(AttemptOutcome o) {
        boolean finishesJob = o.newState() == JobState.SUCCEEDED || o.newState() == JobState.DEAD_LETTERED;
        String sql = finishesJob
                ? """
                  UPDATE job
                  SET state = ?, started_at = COALESCE(started_at, ?), finished_at = ?,
                      last_error = COALESCE(?, last_error), updated_at = CURRENT_TIMESTAMP(3)
                  WHERE job_id = ? AND attempt_count = ? AND state IN (?, ?)
                  """
                : """
                  UPDATE job
                  SET state = ?, started_at = COALESCE(started_at, ?),
                      last_error = COALESCE(?, last_error), updated_at = CURRENT_TIMESTAMP(3)
                  WHERE job_id = ? AND attempt_count = ? AND state IN (?, ?)
                  """;

        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                boolean applied;
                try (PreparedStatement ps = c.prepareStatement(sql)) {
                    int i = 1;
                    ps.setString(i++, o.newState().name());
                    setInstant(ps, i++, o.startedAt());
                    if (finishesJob) {
                        setInstant(ps, i++, o.endedAt());
                    }
                    ps.setString(i++, truncate(o.lastError()));
                    ps.setString(i++, o.jobId().toString());
                    ps.setInt(i++, o.attempt());
                    ps.setString(i++, JobState.DISPATCHED.name());
                    ps.setString(i, JobState.RUNNING.name());
                    applied = ps.executeUpdate() == 1;
                }
                if (applied) {
                    insertAttempt(c, o);
                }
                c.commit();
                return applied;
            } catch (SQLException | RuntimeException ex) {
                c.rollback();
                throw ex;
            } finally {
                c.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to complete attempt for " + o.jobId(), ex);
        }
    }

    @Override
    public List<ActiveAttempt> findActiveAttempts() {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT job_id, attempt_count, state FROM job WHERE state IN (?, ?)")) {
            ps.setString(1, JobState.DISPATCHED.name());
            ps.setString(2, JobState.RUNNING.name());
            List<ActiveAttempt> result = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new ActiveAttempt(
                            UUID.fromString(rs.getString("job_id")),
                            rs.getInt("attempt_count"),
                            JobState.valueOf(rs.getString("state"))));
                }
            }
            return result;
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to list active attempts", ex);
        }
    }

    /**
     * WARNING for anything that went wrong (failed, unknown, or deleted before finishing),
     * NORMAL otherwise.
     */
    static String severity(PodEventRecord event) {
        boolean deletedEarly = event.type() == PodEventType.DELETED && !event.phase().isTerminal();
        boolean bad = event.phase() == PodPhase.FAILED || event.phase() == PodPhase.UNKNOWN;
        return deletedEarly || bad ? SEVERITY_WARNING : SEVERITY_NORMAL;
    }

    /** Keeps the deletion visible in pod_event, since the schema has no DELETED event type. */
    static String message(PodEventRecord event) {
        if (event.type() != PodEventType.DELETED) {
            return event.message();
        }
        String prefix = event.phase().isTerminal() ? DELETED_PREFIX : DELETED_PREFIX + " before finishing";
        return event.message() == null || event.message().isBlank()
                ? prefix
                : prefix + "; " + event.message();
    }

    /**
     * True when the pod's latest row already has the same severity and phase, so replays and
     * reconciliation do not add duplicates. A deletion of a running pod is a new WARNING row; a
     * deletion of a finished pod (normal TTL cleanup) repeats the terminal row and is skipped.
     */
    private static boolean repeatsLastEvent(Connection c, PodEventRecord event) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                """
                SELECT event_type, pod_phase FROM pod_event
                WHERE job_id = ? AND pod_name = ?
                ORDER BY event_id DESC LIMIT 1
                """)) {
            ps.setString(1, event.jobId().toString());
            ps.setString(2, event.podName());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next()
                        && severity(event).equals(rs.getString("event_type"))
                        && event.phase().name().equals(rs.getString("pod_phase"));
            }
        }
    }

    private static void insertAttempt(Connection c, AttemptOutcome o) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                """
                INSERT INTO job_attempt
                    (job_id, attempt_number, pod_name, node_name, started_at, ended_at, exit_code, failure_reason)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            ps.setString(1, o.jobId().toString());
            ps.setInt(2, o.attempt());
            ps.setString(3, o.podName());
            ps.setString(4, o.nodeName());
            // started_at is NOT NULL; a pod that failed before starting uses its end time.
            Instant endedAt = o.endedAt() != null ? o.endedAt() : Instant.now();
            setInstant(ps, 5, o.startedAt() != null ? o.startedAt() : endedAt);
            setInstant(ps, 6, endedAt);
            if (o.exitCode() == null) {
                ps.setNull(7, Types.INTEGER);
            } else {
                ps.setInt(7, o.exitCode());
            }
            ps.setString(8, truncate(o.failureReason()));
            ps.executeUpdate();
        }
    }

    private static void setInstant(PreparedStatement ps, int index, Instant value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.TIMESTAMP);
        } else {
            ps.setTimestamp(index, Timestamp.from(value), Calendar.getInstance(TimeZone.getTimeZone("UTC")));
        }
    }

    private static String truncate(String text) {
        if (text == null || text.length() <= MAX_TEXT) {
            return text;
        }
        return text.substring(0, MAX_TEXT);
    }
}
