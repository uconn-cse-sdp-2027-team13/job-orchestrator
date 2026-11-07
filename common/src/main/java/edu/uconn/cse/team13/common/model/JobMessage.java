package edu.uconn.cse.team13.common.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Versioned queue payload identifying a file to process. File contents are stored elsewhere
 * and must never be included in this message.
 *
 * @param schemaVersion message format version
 * @param jobId stable identifier for the job
 * @param fileName display/name component of the input file
 * @param filePath path or object-store key from which the workload can fetch the file
 * @param submittedAt UTC submission instant
 * @param attempt attempt number, starting at 1
 */
public record JobMessage(
        int schemaVersion,
        UUID jobId,
        String fileName,
        String filePath,
        Instant submittedAt,
        int attempt) {

    /** The first queue message format defined by the project scope. */
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public JobMessage {
        if (schemaVersion < 1) {
            throw new IllegalArgumentException("schemaVersion must be positive");
        }
        Objects.requireNonNull(jobId, "jobId must not be null");
        fileName = requireText(fileName, "fileName");
        filePath = requireText(filePath, "filePath");
        Objects.requireNonNull(submittedAt, "submittedAt must not be null");
        if (attempt < 1) {
            throw new IllegalArgumentException("attempt must be at least 1");
        }
    }

    private static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
