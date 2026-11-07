package edu.uconn.cse.team13.common.repository;

import com.zaxxer.hikari.HikariDataSource;
import edu.uconn.cse.team13.common.capacity.RepositoryJobCapacity;
import edu.uconn.cse.team13.common.db.DataSourceFactory;
import edu.uconn.cse.team13.common.model.JobState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MysqlJobRepositoryTest {

    private HikariDataSource dataSource;
    private MysqlJobRepository repository;

    @BeforeEach
    void setUp() throws SQLException {
        dataSource = (HikariDataSource) DataSourceFactory.create(
                "jdbc:h2:mem:job_repo_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1",
                "sa",
                "");
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(
                    """
                    CREATE TABLE job (
                        job_id VARCHAR(36) PRIMARY KEY,
                        file_name VARCHAR(512) NOT NULL,
                        file_path VARCHAR(1024) NOT NULL,
                        state VARCHAR(32) NOT NULL,
                        attempt_count INT NOT NULL,
                        k8s_job_name VARCHAR(253),
                        last_error CLOB,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
        }
        repository = new MysqlJobRepository(dataSource);
    }

    @AfterEach
    void tearDown() {
        dataSource.close();
    }

    @Test
    void createJobInsertsQueuedRow() throws SQLException {
        UUID jobId = UUID.randomUUID();
        repository.createJob(jobId, "data.csv", "/data/data.csv");

        assertEquals(Optional.of(JobState.QUEUED), repository.findState(jobId));
        assertEquals("data.csv", readString(jobId, "file_name"));
        assertEquals("/data/data.csv", readString(jobId, "file_path"));
        assertEquals(0, readAttempt(jobId));
    }

    @Test
    void createJobIsIdempotent() {
        UUID jobId = UUID.randomUUID();
        repository.createJob(jobId, "data.csv", "/data/data.csv");
        repository.createJob(jobId, "data.csv", "/data/data.csv");

        assertEquals(Optional.of(JobState.QUEUED), repository.findState(jobId));
    }

    @Test
    void findStateReturnsEmptyWhenNotFound() {
        assertTrue(repository.findState(UUID.randomUUID()).isEmpty());
    }

    @Test
    void markDispatchedMovesQueuedToDispatched() throws SQLException {
        UUID jobId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        insertJob(jobId, JobState.QUEUED, 1, null);

        repository.markDispatched(jobId, 1, "k8s-job-1");

        assertEquals(JobState.DISPATCHED.name(), readState(jobId));
        assertEquals("k8s-job-1", readK8sName(jobId));
        assertEquals(1, readAttempt(jobId));
    }

    @Test
    void markDispatchedIsIdempotentForSameAttemptAndName() throws SQLException {
        UUID jobId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        insertJob(jobId, JobState.DISPATCHED, 1, "k8s-job-1");

        repository.markDispatched(jobId, 1, "k8s-job-1");

        assertEquals(JobState.DISPATCHED.name(), readState(jobId));
    }

    @Test
    void countActiveJobsAndCapacityGate() throws SQLException {
        insertJob(UUID.randomUUID(), JobState.DISPATCHED, 1, "a");
        insertJob(UUID.randomUUID(), JobState.RUNNING, 1, "b");
        insertJob(UUID.randomUUID(), JobState.QUEUED, 1, null);

        assertEquals(2, repository.countActiveJobs());

        RepositoryJobCapacity capacity = new RepositoryJobCapacity(repository, 2);
        assertFalse(capacity.hasAvailableSlot());

        RepositoryJobCapacity room = new RepositoryJobCapacity(repository, 3);
        assertTrue(room.hasAvailableSlot());
    }

    private void insertJob(UUID jobId, JobState state, int attempt, String k8sName)
            throws SQLException {
        try (Connection connection = dataSource.getConnection();
             var statement = connection.prepareStatement(
                     """
                     INSERT INTO job (job_id, file_name, file_path, state, attempt_count, k8s_job_name)
                     VALUES (?, 'f.csv', 'path/f.csv', ?, ?, ?)
                     """)) {
            statement.setString(1, jobId.toString());
            statement.setString(2, state.name());
            statement.setInt(3, attempt);
            statement.setString(4, k8sName);
            statement.executeUpdate();
        }
    }

    private String readState(UUID jobId) throws SQLException {
        return readString(jobId, "state");
    }

    private String readK8sName(UUID jobId) throws SQLException {
        return readString(jobId, "k8s_job_name");
    }

    private int readAttempt(UUID jobId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             var statement = connection.prepareStatement(
                     "SELECT attempt_count FROM job WHERE job_id = ?")) {
            statement.setString(1, jobId.toString());
            try (var rs = statement.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private String readString(UUID jobId, String column) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             var statement = connection.prepareStatement(
                     "SELECT " + column + " FROM job WHERE job_id = ?")) {
            statement.setString(1, jobId.toString());
            try (var rs = statement.executeQuery()) {
                rs.next();
                return rs.getString(1);
            }
        }
    }
}
