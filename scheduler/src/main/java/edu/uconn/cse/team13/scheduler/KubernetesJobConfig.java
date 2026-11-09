package edu.uconn.cse.team13.scheduler;

import io.fabric8.kubernetes.api.model.Quantity;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

/** Settings for workload pods. The host data directory must exist on the Kubernetes node. */
public record KubernetesJobConfig(
        String namespace,
        String image,
        String dataHostPath,
        Map<String, String> environment,
        Map<String, Quantity> requests,
        Map<String, Quantity> limits,
        long activeDeadlineSeconds,
        int ttlSecondsAfterFinished) {

    /** Small local-demo defaults; database settings can be supplied in environment. */
    public KubernetesJobConfig(
            String namespace, String image, String dataHostPath, Map<String, String> environment) {
        this(namespace, image, dataHostPath, environment,
                Map.of("cpu", new Quantity("100m"), "memory", new Quantity("128Mi")),
                Map.of("cpu", new Quantity("1"), "memory", new Quantity("512Mi")),
                3600, 3600);
    }

    public KubernetesJobConfig {
        Objects.requireNonNull(namespace, "namespace must not be null");
        if (namespace.length() > 63 || !namespace.matches("[a-z0-9]([-a-z0-9]*[a-z0-9])?")) {
            throw new IllegalArgumentException("namespace must be a Kubernetes DNS label");
        }
        Objects.requireNonNull(image, "image must not be null");
        if (image.isBlank()) {
            throw new IllegalArgumentException("image must not be blank");
        }
        Objects.requireNonNull(dataHostPath, "dataHostPath must not be null");
        if (!Path.of(dataHostPath).isAbsolute()) {
            throw new IllegalArgumentException("dataHostPath must be absolute");
        }
        environment = Map.copyOf(environment);
        if (environment.containsKey("JOB_ID") || environment.containsKey("FILE_PATH")) {
            throw new IllegalArgumentException("JOB_ID and FILE_PATH come from JobMessage");
        }
        requests = Map.copyOf(requests);
        limits = Map.copyOf(limits);
        for (String resource : new String[]{"cpu", "memory"}) {
            Quantity request = Objects.requireNonNull(requests.get(resource), resource + " request is required");
            Quantity limit = Objects.requireNonNull(limits.get(resource), resource + " limit is required");
            if (request.getNumericalAmount().signum() <= 0 || limit.compareTo(request) < 0) {
                throw new IllegalArgumentException(resource + " request must be positive and no greater than its limit");
            }
        }
        if (activeDeadlineSeconds <= 0 || ttlSecondsAfterFinished < 0) {
            throw new IllegalArgumentException("deadline must be positive and TTL must be non-negative");
        }
    }
}
