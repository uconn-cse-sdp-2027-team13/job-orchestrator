package edu.uconn.cse.team13.consumer.queue;

import edu.uconn.cse.team13.common.model.JobMessage;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JobMessageCodecTest {

    @Test
    void roundTripPreservesFields() {
        JobMessage original = new JobMessage(
                1,
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "payments.csv",
                "incoming/payments.csv",
                Instant.parse("2026-11-04T15:00:00Z"),
                1);

        JobMessage decoded = JobMessageCodec.decode(JobMessageCodec.encode(original));

        assertEquals(original, decoded);
    }

    @Test
    void decodeRejectsMissingField() {
        Map<String, String> fields = new HashMap<>(JobMessageCodec.encode(sample()));
        fields.remove(JobMessageCodec.JOB_ID);

        assertThrows(IllegalArgumentException.class, () -> JobMessageCodec.decode(fields));
    }

    @Test
    void decodeRejectsBadUuid() {
        Map<String, String> fields = new HashMap<>(JobMessageCodec.encode(sample()));
        fields.put(JobMessageCodec.JOB_ID, "not-a-uuid");

        assertThrows(IllegalArgumentException.class, () -> JobMessageCodec.decode(fields));
    }

    private static JobMessage sample() {
        return new JobMessage(
                1,
                UUID.randomUUID(),
                "payments.csv",
                "incoming/payments.csv",
                Instant.parse("2026-11-04T15:00:00Z"),
                1);
    }
}
