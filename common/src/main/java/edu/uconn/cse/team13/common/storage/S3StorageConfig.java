package edu.uconn.cse.team13.common.storage;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Connection settings for {@link S3StorageClient}, read from environment variables.
 *
 * @param endpoint URL of the S3-compatible server, for example {@code http://localhost:9000} for local RustFS
 * @param bucket bucket that holds the job input files
 * @param accessKey access key (user name) for the server
 * @param secretKey secret key (password) for the server
 * @param region region name; S3-compatible servers accept any value, for example {@code us-east-1}
 */
public record S3StorageConfig(
        String endpoint,
        String bucket,
        String accessKey,
        String secretKey,
        String region) {

    public static final String ENDPOINT = "S3_ENDPOINT";
    public static final String BUCKET = "S3_BUCKET";
    public static final String ACCESS_KEY = "S3_ACCESS_KEY";
    public static final String SECRET_KEY = "S3_SECRET_KEY";
    public static final String REGION = "S3_REGION";

    /** The five variable names, shared so every service reads and passes the same ones. */
    public static final List<String> VARIABLES = List.of(ENDPOINT, BUCKET, ACCESS_KEY, SECRET_KEY, REGION);

    public S3StorageConfig {
        Objects.requireNonNull(endpoint, "endpoint must not be null");
        Objects.requireNonNull(bucket, "bucket must not be null");
        Objects.requireNonNull(accessKey, "accessKey must not be null");
        Objects.requireNonNull(secretKey, "secretKey must not be null");
        Objects.requireNonNull(region, "region must not be null");
    }

    /**
     * Reads all five settings from {@code env}. Every one is required.
     *
     * @throws IllegalArgumentException naming the first variable that is missing or blank
     */
    public static S3StorageConfig fromEnv(Map<String, String> env) {
        return new S3StorageConfig(
                required(env, ENDPOINT),
                required(env, BUCKET),
                required(env, ACCESS_KEY),
                required(env, SECRET_KEY),
                required(env, REGION));
    }

    private static String required(Map<String, String> env, String name) {
        String value = env.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required environment variable " + name);
        }
        return value.trim();
    }
}
