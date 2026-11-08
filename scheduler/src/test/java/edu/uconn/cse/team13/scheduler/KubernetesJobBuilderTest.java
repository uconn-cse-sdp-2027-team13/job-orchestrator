package edu.uconn.cse.team13.scheduler;

import edu.uconn.cse.team13.common.model.JobMessage;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KubernetesJobBuilderTest {

    @Test
    void buildsWorkloadWithMessageLabelsEnvironmentAndSharedData() {
        JobMessage message = message(1);
        Job job = new KubernetesJobBuilder(config()).build(message);
        var spec = job.getSpec();
        var pod = spec.getTemplate().getSpec();
        var container = pod.getContainers().get(0);
        Map<String, String> labels = Map.of("job_id", message.jobId().toString(), "attempt", "1");

        assertEquals("batch/v1", job.getApiVersion());
        assertEquals("Job", job.getKind());
        assertEquals("job-" + message.jobId() + "-a1", job.getMetadata().getName());
        assertEquals("scheduler-test", job.getMetadata().getNamespace());
        assertEquals(labels, job.getMetadata().getLabels());
        assertEquals(labels, spec.getTemplate().getMetadata().getLabels());
        assertEquals(0, spec.getBackoffLimit());
        assertEquals(1, spec.getCompletions());
        assertEquals(1, spec.getParallelism());
        assertEquals("Never", pod.getRestartPolicy());
        assertEquals(1, pod.getContainers().size());
        assertEquals("batch-workload:local", container.getImage());
        assertEquals("IfNotPresent", container.getImagePullPolicy());
        assertEquals(Map.of(
                "JOB_ID", message.jobId().toString(),
                "FILE_PATH", message.filePath(),
                "MYSQL_URL", "jdbc:mysql://mysql:3306/app"),
                container.getEnv().stream().collect(Collectors.toMap(EnvVar::getName, EnvVar::getValue)));
        assertEquals("/data", container.getVolumeMounts().get(0).getMountPath());
        assertTrue(container.getVolumeMounts().get(0).getReadOnly());
        assertEquals(pod.getVolumes().get(0).getName(), container.getVolumeMounts().get(0).getName());
        assertEquals("/srv/job-data", pod.getVolumes().get(0).getHostPath().getPath());
        assertEquals("Directory", pod.getVolumes().get(0).getHostPath().getType());
        assertEquals(new Quantity("100m"), container.getResources().getRequests().get("cpu"));
        assertEquals(new Quantity("128Mi"), container.getResources().getRequests().get("memory"));
        assertEquals(new Quantity("1"), container.getResources().getLimits().get("cpu"));
        assertEquals(new Quantity("512Mi"), container.getResources().getLimits().get("memory"));
        assertEquals(3600L, spec.getActiveDeadlineSeconds());
        assertEquals(3600, spec.getTtlSecondsAfterFinished());
    }

    @Test
    void usesConfiguredResourcesDeadlineAndTtl() {
        Map<String, Quantity> requests = Map.of("cpu", new Quantity("250m"), "memory", new Quantity("256Mi"));
        Map<String, Quantity> limits = Map.of("cpu", new Quantity("2"), "memory", new Quantity("1Gi"));
        var config = new KubernetesJobConfig("scheduler-test", "worker:v1", "/srv/job-data",
                Map.of(), requests, limits, 120, 600);
        Job job = new KubernetesJobBuilder(config).build(message(1));

        assertEquals(requests, job.getSpec().getTemplate().getSpec().getContainers().get(0).getResources().getRequests());
        assertEquals(limits, job.getSpec().getTemplate().getSpec().getContainers().get(0).getResources().getLimits());
        assertEquals(120L, job.getSpec().getActiveDeadlineSeconds());
        assertEquals(600, job.getSpec().getTtlSecondsAfterFinished());
    }

    @Test
    void givesEachAttemptAStableDistinctName() {
        var builder = new KubernetesJobBuilder(config());

        assertEquals(builder.build(message(1)), builder.build(message(1)));
        assertNotEquals(builder.build(message(1)).getMetadata().getName(),
                builder.build(message(2)).getMetadata().getName());
        assertEquals("2", builder.build(message(2)).getSpec().getTemplate().getMetadata().getLabels().get("attempt"));
    }

    @Test
    void rejectsUnknownSchemaBeforeDispatch() {
        JobMessage message = message(1);
        var unsupported = new JobMessage(2, message.jobId(), message.fileName(),
                message.filePath(), message.submittedAt(), message.attempt());

        assertThrows(IllegalArgumentException.class, () -> new KubernetesJobBuilder(config()).build(unsupported));
    }

    @Test
    void rejectsInvalidNamespaceRelativeMountAndReservedEnvironment() {
        assertThrows(IllegalArgumentException.class,
                () -> new KubernetesJobConfig("Bad Namespace", "worker:v1", "/data", Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new KubernetesJobConfig("default", "worker:v1", "relative", Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new KubernetesJobConfig("default", "worker:v1", "/data", Map.of("JOB_ID", "override")));
        assertThrows(IllegalArgumentException.class,
                () -> new KubernetesJobConfig("default", "worker:v1", "/data", Map.of("FILE_PATH", "override")));
    }

    static KubernetesJobConfig config() {
        return new KubernetesJobConfig("scheduler-test", "batch-workload:local", "/srv/job-data",
                Map.of("MYSQL_URL", "jdbc:mysql://mysql:3306/app"));
    }

    static JobMessage message(int attempt) {
        return new JobMessage(1, UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "payments.csv", "incoming/payments.csv", Instant.parse("2026-11-07T12:00:00Z"), attempt);
    }
}
