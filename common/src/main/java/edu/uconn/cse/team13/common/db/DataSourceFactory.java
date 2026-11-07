package edu.uconn.cse.team13.common.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.util.Objects;

/** Builds a HikariCP pool for MySQL (or a compatible JDBC URL in tests). */
public final class DataSourceFactory {

    private DataSourceFactory() {
    }

    public static DataSource create(String jdbcUrl, String username, String password) {
        Objects.requireNonNull(jdbcUrl, "jdbcUrl must not be null");
        Objects.requireNonNull(username, "username must not be null");
        Objects.requireNonNull(password, "password must not be null");

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(5);
        config.setPoolName("job-orchestrator");
        return new HikariDataSource(config);
    }
}
