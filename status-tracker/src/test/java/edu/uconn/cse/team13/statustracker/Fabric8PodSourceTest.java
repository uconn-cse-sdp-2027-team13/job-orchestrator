package edu.uconn.cse.team13.statustracker;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pod-to-snapshot mapping, tested on plain model objects so no cluster is needed. */
class Fabric8PodSourceTest {

    private static final UUID JOB = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    void mapsLabelsPhaseAndNode() {
        Pod pod = new PodBuilder()
                .withNewMetadata().withName("p1")
                .addToLabels(PodLabels.JOB_ID, JOB.toString())
                .addToLabels(PodLabels.ATTEMPT, "2")
                .endMetadata()
                .withNewSpec().withNodeName("node-7").endSpec()
                .withNewStatus().withPhase("Running").withStartTime("2026-11-08T12:00:00Z").endStatus()
                .build();

        PodSnapshot snapshot = Fabric8PodSource.toSnapshot(pod, "job-workload");

        assertEquals(JOB, snapshot.jobId());
        assertEquals(Integer.valueOf(2), snapshot.attempt());
        assertEquals(PodPhase.RUNNING, snapshot.phase());
        assertEquals("node-7", snapshot.nodeName());
        assertEquals(Instant.parse("2026-11-08T12:00:00Z"), snapshot.startedAt());
        assertTrue(snapshot.hasJobIdentity());
    }

    @Test
    void takesExitCodeAndTimesFromTheWorkloadContainer() {
        Pod pod = new PodBuilder()
                .withNewMetadata().withName("p2")
                .addToLabels(PodLabels.JOB_ID, JOB.toString())
                .addToLabels(PodLabels.ATTEMPT, "1")
                .endMetadata()
                .withNewStatus().withPhase("Failed")
                .addNewContainerStatus().withName("sidecar")
                    .withNewState().withNewTerminated().withExitCode(0).endTerminated().endState()
                .endContainerStatus()
                .addNewContainerStatus().withName("job-workload")
                    .withNewState().withNewTerminated()
                        .withExitCode(137).withReason("OOMKilled")
                        .withStartedAt("2026-11-08T12:00:00Z").withFinishedAt("2026-11-08T12:05:00Z")
                    .endTerminated().endState()
                .endContainerStatus()
                .endStatus()
                .build();

        PodSnapshot snapshot = Fabric8PodSource.toSnapshot(pod, "job-workload");

        assertEquals(PodPhase.FAILED, snapshot.phase());
        assertEquals(Integer.valueOf(137), snapshot.exitCode());
        assertEquals("OOMKilled", snapshot.reason());
        assertEquals(Instant.parse("2026-11-08T12:05:00Z"), snapshot.finishedAt());
    }

    @Test
    void podLevelReasonWinsOverContainerReason() {
        Pod pod = new PodBuilder()
                .withNewMetadata().withName("p3")
                .addToLabels(PodLabels.JOB_ID, JOB.toString())
                .addToLabels(PodLabels.ATTEMPT, "1")
                .endMetadata()
                .withNewStatus().withPhase("Failed").withReason("DeadlineExceeded")
                .addNewContainerStatus().withName("job-workload")
                    .withNewState().withNewTerminated().withExitCode(143).withReason("Error").endTerminated().endState()
                .endContainerStatus()
                .endStatus()
                .build();

        assertEquals("DeadlineExceeded", Fabric8PodSource.toSnapshot(pod, "job-workload").reason());
    }

    @Test
    void badOrMissingLabelsYieldNoJobIdentity() {
        Pod pod = new PodBuilder()
                .withNewMetadata().withName("p4")
                .addToLabels(PodLabels.JOB_ID, "not-a-uuid")
                .addToLabels(PodLabels.ATTEMPT, "zero")
                .endMetadata()
                .withNewStatus().withPhase("Pending").endStatus()
                .build();

        PodSnapshot snapshot = Fabric8PodSource.toSnapshot(pod, "job-workload");

        assertNull(snapshot.jobId());
        assertNull(snapshot.attempt());
        assertFalse(snapshot.hasJobIdentity());
        assertEquals(PodPhase.PENDING, snapshot.phase());
    }
}
