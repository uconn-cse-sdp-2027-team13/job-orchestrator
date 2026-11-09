package edu.uconn.cse.team13.common.queue;

import edu.uconn.cse.team13.common.model.JobMessage;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Maps {@link JobMessage} fields to Redis Streams hash entries and back.
 */
public final class JobMessageCodec {

    public static final String SCHEMA_VERSION = "schemaVersion";
    public static final String JOB_ID = "jobId";
    public static final String FILE_NAME = "fileName";
    public static final String FILE_PATH = "filePath";
    public static final String SUBMITTED_AT = "submittedAt";
    public static final String ATTEMPT = "attempt";

    private JobMessageCodec() {
    }

    public static Map<String, String> encode(JobMessage message) {
        Map<String, String> fields = new HashMap<>();
        fields.put(SCHEMA_VERSION, Integer.toString(message.schemaVersion()));
        fields.put(JOB_ID, message.jobId().toString());
        fields.put(FILE_NAME, message.fileName());
        fields.put(FILE_PATH, message.filePath());
        fields.put(SUBMITTED_AT, message.submittedAt().toString());
        fields.put(ATTEMPT, Integer.toString(message.attempt()));
        return fields;
    }

    public static JobMessage decode(Map<String, String> fields) {
        return new JobMessage(
                parseInt(required(fields, SCHEMA_VERSION), SCHEMA_VERSION),
                parseUuid(required(fields, JOB_ID)),
                required(fields, FILE_NAME),
                required(fields, FILE_PATH),
                parseInstant(required(fields, SUBMITTED_AT)),
                parseInt(required(fields, ATTEMPT), ATTEMPT));
    }

    private static String required(Map<String, String> fields, String name) {
        String value = fields.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("missing or blank field: " + name);
        }
        return value;
    }

    private static int parseInt(String value, String fieldName) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(fieldName + " is not an integer: " + value, ex);
        }
    }

    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("jobId is not a UUID: " + value, ex);
        }
    }

    private static Instant parseInstant(String value) {
        try {
            return Instant.parse(value);
        } catch (Exception ex) {
            throw new IllegalArgumentException("submittedAt is not an ISO-8601 instant: " + value, ex);
        }
    }
}
