package edu.uconn.cse.team13.scheduler;

import edu.uconn.cse.team13.common.model.JobMessage;
import edu.uconn.cse.team13.common.scheduler.JobScheduler;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;

import java.util.Objects;

/** Submits Jobs using an injected client; the caller owns the client's lifecycle. */
public final class Fabric8JobScheduler implements JobScheduler {

    private final KubernetesClient client;
    private final KubernetesJobBuilder builder;

    public Fabric8JobScheduler(KubernetesClient client, KubernetesJobConfig config) {
        this.client = Objects.requireNonNull(client, "client must not be null");
        this.builder = new KubernetesJobBuilder(config);
    }

    @Override
    public String submit(JobMessage message) {
        Job job = builder.build(message);
        String name = job.getMetadata().getName();
        var jobs = client.batch().v1().jobs().inNamespace(job.getMetadata().getNamespace());
        try {
            jobs.resource(job).create();
        } catch (KubernetesClientException exception) {
            if (exception.getCode() != 409) {
                throw exception;
            }
            // A redelivered queue message may already have created this attempt.
            Job existing = jobs.withName(name).get();
            if (existing == null || existing.getMetadata().getLabels() == null
                    || !existing.getMetadata().getLabels().entrySet()
                            .containsAll(job.getMetadata().getLabels().entrySet())) {
                throw exception;
            }
        }
        return name;
    }
}
