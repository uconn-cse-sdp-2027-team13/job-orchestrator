package edu.uconn.cse.team13.scheduler;

import edu.uconn.cse.team13.common.model.JobMessage;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Translates the shared message into one Kubernetes Job for one attempt. */
public final class KubernetesJobBuilder {

    private final KubernetesJobConfig config;

    public KubernetesJobBuilder(KubernetesJobConfig config) {
        this.config = Objects.requireNonNull(config, "config must not be null");
    }

    public Job build(JobMessage message) {
        Objects.requireNonNull(message, "message must not be null");
        if (message.schemaVersion() != JobMessage.CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("unsupported schemaVersion: " + message.schemaVersion());
        }

        String name = "job-" + message.jobId() + "-a" + message.attempt();
        Map<String, String> labels = Map.of(
                "job_id", message.jobId().toString(),
                "attempt", Integer.toString(message.attempt()));

        ContainerBuilder workload = new ContainerBuilder()
                .withName("workload")
                .withImage(config.image())
                .withImagePullPolicy("IfNotPresent")
                .withNewResources()
                    .withRequests(config.requests())
                    .withLimits(config.limits())
                .endResources();
        config.environment().forEach((key, value) ->
                workload.addNewEnv().withName(key).withValue(value).endEnv());
        workload.addNewEnv().withName("JOB_ID").withValue(message.jobId().toString()).endEnv()
                .addNewEnv().withName("FILE_PATH").withValue(message.filePath()).endEnv();

        // Input files come from object storage, so a node directory is mounted only when one is configured.
        List<Volume> volumes = new ArrayList<>();
        if (config.dataHostPath() != null) {
            workload.addNewVolumeMount()
                    .withName("data")
                    .withMountPath("/data")
                    .withReadOnly(true)
                .endVolumeMount();
            volumes.add(new VolumeBuilder()
                    .withName("data")
                    .withNewHostPath()
                        .withPath(config.dataHostPath())
                        .withType("Directory")
                    .endHostPath()
                    .build());
        }

        return new JobBuilder()
                .withApiVersion("batch/v1")
                .withKind("Job")
                .withNewMetadata()
                    .withName(name)
                    .withNamespace(config.namespace())
                    .withLabels(labels)
                .endMetadata()
                .withNewSpec()
                    .withBackoffLimit(0)
                    .withCompletions(1)
                    .withParallelism(1)
                    .withActiveDeadlineSeconds(config.activeDeadlineSeconds())
                    .withTtlSecondsAfterFinished(config.ttlSecondsAfterFinished())
                    .withNewTemplate()
                        .withNewMetadata().withLabels(labels).endMetadata()
                        .withNewSpec()
                            .withRestartPolicy("Never")
                            .withContainers(workload.build())
                            .withVolumes(volumes)
                        .endSpec()
                    .endTemplate()
                .endSpec()
                .build();
    }
}
