package edu.uconn.cse.team13.producer;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProducerConfigTest {

    @Test
    void fromEnvWithDefaults() {
        Map<String, String> env = Map.of(
                "JDBC_URL", "jdbc:mysql://localhost:3306/app",
                "DB_USER", "app",
                "DB_PASSWORD", "secret");

        ProducerConfig config = ProducerConfig.fromEnv(withStorage(env));

        assertEquals("jdbc:mysql://localhost:3306/app", config.jdbcUrl());
        assertEquals("app", config.dbUser());
        assertEquals("secret", config.dbPassword());
        assertEquals("redis://localhost:6379", config.redisUri());
        assertEquals(ProducerConfig.DEFAULT_STREAM_KEY, config.streamKey());
        assertEquals(ProducerConfig.DEFAULT_CONSUMER_GROUP, config.consumerGroup());
        assertEquals(ProducerConfig.DEFAULT_PRODUCER_NAME, config.producerName());
        assertEquals("http://localhost:9000", config.storage().endpoint());
        assertEquals("job-files", config.storage().bucket());
        assertEquals("local-rustfs", config.storage().accessKey());
        assertEquals("local-rustfs-password", config.storage().secretKey());
        assertEquals("us-east-1", config.storage().region());
        assertEquals(2500, config.recordCount());
        assertFalse(config.oneShot());
        assertTrue(config.produceOnStart());
        assertEquals(300, config.intervalSeconds());
    }

    @Test
    void fromEnvWithOverrides() {
        Map<String, String> env = Map.ofEntries(
                Map.entry("JDBC_URL", "jdbc:mysql://db.internal:3306/prod"),
                Map.entry("DB_USER", "prod_user"),
                Map.entry("DB_PASSWORD", "prod_pass"),
                Map.entry("REDIS_HOST", "redis.internal"),
                Map.entry("REDIS_PORT", "6381"),
                Map.entry("REDIS_STREAM", "payments:stream"),
                Map.entry("REDIS_GROUP", "producer-group"),
                Map.entry("REDIS_PRODUCER_NAME", "producer-worker-42"),
                Map.entry("S3_ENDPOINT", "https://s3.internal"),
                Map.entry("S3_BUCKET", "prod-job-files"),
                Map.entry("RECORD_COUNT", "10000"),
                Map.entry("ONE_SHOT", "true"),
                Map.entry("PRODUCE_ON_START", "false"),
                Map.entry("INTERVAL_SECONDS", "60"));

        ProducerConfig config = ProducerConfig.fromEnv(withStorage(env));

        assertEquals("jdbc:mysql://db.internal:3306/prod", config.jdbcUrl());
        assertEquals("redis://redis.internal:6381", config.redisUri());
        assertEquals("payments:stream", config.streamKey());
        assertEquals("producer-group", config.consumerGroup());
        assertEquals("producer-worker-42", config.producerName());
        assertEquals("https://s3.internal", config.storage().endpoint());
        assertEquals("prod-job-files", config.storage().bucket());
        assertEquals(10000, config.recordCount());
        assertTrue(config.oneShot());
        assertFalse(config.produceOnStart());
        assertEquals(60, config.intervalSeconds());
    }

    @Test
    void fromEnvWithExplicitRedisUri() {
        Map<String, String> env = Map.of(
                "JDBC_URL", "jdbc:mysql://localhost:3306/app",
                "DB_USER", "app",
                "DB_PASSWORD", "secret",
                "REDIS_URI", "redis://auth-user:auth-pass@redis.prod:6379/0");

        ProducerConfig config = ProducerConfig.fromEnv(withStorage(env));
        assertEquals("redis://auth-user:auth-pass@redis.prod:6379/0", config.redisUri());
    }

    @Test
    void fromEnvWithMysqlUrlFallback() {
        Map<String, String> env = Map.of(
                "MYSQL_URL", "jdbc:mysql://db.local:3306/app",
                "MYSQL_USER", "mysql_user",
                "MYSQL_PASSWORD", "mysql_pass");

        ProducerConfig config = ProducerConfig.fromEnv(withStorage(env));
        assertEquals("jdbc:mysql://db.local:3306/app", config.jdbcUrl());
        assertEquals("mysql_user", config.dbUser());
        assertEquals("mysql_pass", config.dbPassword());
    }

    @Test
    void fromEnvMissingRequiredThrows() {
        Map<String, String> env = Map.of(
                "DB_USER", "app",
                "DB_PASSWORD", "secret");

        assertThrows(IllegalArgumentException.class, () -> ProducerConfig.fromEnv(withStorage(env)));
    }

    @Test
    void fromEnvMissingStorageSettingThrows() {
        Map<String, String> env = withStorage(Map.of(
                "JDBC_URL", "jdbc:mysql://localhost:3306/app",
                "DB_USER", "app",
                "DB_PASSWORD", "secret"));
        env.remove("S3_BUCKET");

        assertThrows(IllegalArgumentException.class, () -> ProducerConfig.fromEnv(env));
    }

    @Test
    void fromEnvInvalidIntegerThrows() {
        Map<String, String> env = Map.of(
                "JDBC_URL", "jdbc:mysql://localhost:3306/app",
                "DB_USER", "app",
                "DB_PASSWORD", "secret",
                "RECORD_COUNT", "abc");

        assertThrows(IllegalArgumentException.class, () -> ProducerConfig.fromEnv(withStorage(env)));
    }

    @Test
    void fromEnvInvalidRecordCountThrows() {
        Map<String, String> env = Map.of(
                "JDBC_URL", "jdbc:mysql://localhost:3306/app",
                "DB_USER", "app",
                "DB_PASSWORD", "secret",
                "RECORD_COUNT", "0");

        assertThrows(IllegalArgumentException.class, () -> ProducerConfig.fromEnv(withStorage(env)));
    }

    @Test
    void fromEnvNegativeIntervalThrows() {
        Map<String, String> env = Map.of(
                "JDBC_URL", "jdbc:mysql://localhost:3306/app",
                "DB_USER", "app",
                "DB_PASSWORD", "secret",
                "INTERVAL_SECONDS", "-10");

        assertThrows(IllegalArgumentException.class, () -> ProducerConfig.fromEnv(withStorage(env)));
    }

    /** Adds the required object storage settings unless the test already set its own. */
    private static Map<String, String> withStorage(Map<String, String> env) {
        Map<String, String> all = new HashMap<>();
        all.put("S3_ENDPOINT", "http://localhost:9000");
        all.put("S3_BUCKET", "job-files");
        all.put("S3_ACCESS_KEY", "local-rustfs");
        all.put("S3_SECRET_KEY", "local-rustfs-password");
        all.put("S3_REGION", "us-east-1");
        all.putAll(env);
        return all;
    }
}
