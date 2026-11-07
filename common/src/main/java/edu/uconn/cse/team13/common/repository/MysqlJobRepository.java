package edu.uconn.cse.team13.common.repository;

import edu.uconn.cse.team13.common.model.JobState;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;

/**
 * JDBC {@link JobRepository} against the {@code job} table provisioned by {@code platform-infra}.
 * Expected columns: {@code job_id}, {@code state}, {@code attempt_count}, {@code k8s_job_name},
 * {@code updated_at} (plus other §6.4 columns unused here).
 */
public final class MysqlJobRepository implements JobRepository {

    private final DataSource dataSource;

    public MysqlJobRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
    }

    @Override
    public void markDispatched(UUID jobId, int attempt, String kubernetesJobName) {
        Objects.requireNonNull(jobId, "jobId must not be null");
        Objects.requireNonNull(kubernetesJobName, "kubernetesJobName must not be null");
        if (kubernetesJobName.isBlank()) {
            throw new IllegalArgumentException("kubernetesJobName must not be blank");
        }
        if (attempt < 1) {
            throw new IllegalArgumentException("attempt must be at least 1");
        }

        String id = jobId.toString();
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                if (alreadyDispatched(connection, id, attempt, kubernetesJobName)) {
                    connection.commit();
                    return;
                }

                try (PreparedStatement update = connection.prepareStatement(
                        """
                        UPDATE job
                        SET state = ?, attempt_count = ?, k8s_job_name = ?, updated_at = CURRENT_TIMESTAMP
                        WHERE job_id = ? AND state = ?
                        """)) {
                    update.setString(1, JobState.DISPATCHED.name());
                    update.setInt(2, attempt);
                    update.setString(3, kubernetesJobName);
                    update.setString(4, id);
                    update.setString(5, JobState.QUEUED.name());
                    if (update.executeUpdate() != 1) {
                        throw new IllegalStateException(
                                "Cannot mark DISPATCHED for jobId=" + id
                                        + " attempt=" + attempt
                                        + "; expected one QUEUED row");
                    }
                }
                connection.commit();
            } catch (RuntimeException | SQLException ex) {
                connection.rollback();
                throw ex instanceof RuntimeException runtime
                        ? runtime
                        : new IllegalStateException("Failed to mark job DISPATCHED: " + id, ex);
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to mark job DISPATCHED: " + id, ex);
        }
    }

    @Override
    public int countActiveJobs() {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     """
                     SELECT COUNT(*)
                     FROM job
                     WHERE state IN (?, ?)
                     """)) {
            statement.setString(1, JobState.DISPATCHED.name());
            statement.setString(2, JobState.RUNNING.name());
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to count active jobs", ex);
        }
    }

    private static boolean alreadyDispatched(
            Connection connection,
            String jobId,
            int attempt,
            String kubernetesJobName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                """
                SELECT state, attempt_count, k8s_job_name
                FROM job
                WHERE job_id = ?
                """)) {
            statement.setString(1, jobId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalStateException("No job row for jobId=" + jobId);
                }
                return JobState.DISPATCHED.name().equals(rs.getString("state"))
                        && rs.getInt("attempt_count") == attempt
                        && kubernetesJobName.equals(rs.getString("k8s_job_name"));
            }
        }
    }
}
