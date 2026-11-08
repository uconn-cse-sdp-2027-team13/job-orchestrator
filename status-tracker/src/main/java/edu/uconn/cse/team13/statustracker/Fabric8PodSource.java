package edu.uconn.cse.team13.statustracker;

import io.fabric8.kubernetes.api.model.ContainerState;
import io.fabric8.kubernetes.api.model.ContainerStateTerminated;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * {@link PodSource} backed by the Fabric8 Kubernetes client. All Fabric8 types stop here: pods are
 * converted to {@link PodSnapshot} immediately.
 */
public final class Fabric8PodSource implements PodSource {

    private static final Logger logger = LoggerFactory.getLogger(Fabric8PodSource.class);

    private final KubernetesClient client;
    private final String namespace;
    private final String workloadContainer;

    /**
     * @param namespace namespace to watch; {@code null} uses the client's current namespace
     * @param workloadContainer name of the container whose exit code decides the outcome
     */
    public Fabric8PodSource(KubernetesClient client, String namespace, String workloadContainer) {
        this.client = Objects.requireNonNull(client, "client must not be null");
        this.namespace = namespace != null && !namespace.isBlank() ? namespace : client.getNamespace();
        this.workloadContainer = Objects.requireNonNull(workloadContainer, "workloadContainer must not be null");
    }

    @Override
    public PodListing listPods() {
        PodList list = client.pods()
                .inNamespace(namespace)
                .withLabelSelector(PodLabels.SELECTOR)
                .list();
        List<PodSnapshot> pods = new ArrayList<>();
        for (Pod pod : list.getItems()) {
            pods.add(toSnapshot(pod, workloadContainer));
        }
        String resourceVersion = list.getMetadata() != null ? list.getMetadata().getResourceVersion() : null;
        return new PodListing(pods, resourceVersion);
    }

    @Override
    public PodWatch watch(String resourceVersion, PodWatchListener listener) {
        Watcher<Pod> watcher = new Watcher<>() {
            @Override
            public void eventReceived(Action action, Pod pod) {
                ChangeType type = switch (action) {
                    case ADDED -> ChangeType.ADDED;
                    case MODIFIED -> ChangeType.MODIFIED;
                    case DELETED -> ChangeType.DELETED;
                    default -> null;
                };
                if (type != null) {
                    listener.onChange(new PodChange(type, toSnapshot(pod, workloadContainer)));
                }
            }

            @Override
            public void onClose(WatcherException cause) {
                listener.onClosed(cause);
            }

            @Override
            public void onClose() {
                listener.onClosed(null);
            }
        };

        Watch watch = client.pods()
                .inNamespace(namespace)
                .withLabelSelector(PodLabels.SELECTOR)
                .withResourceVersion(resourceVersion)
                .watch(watcher);
        return watch::close;
    }

    /** Converts a pod to the neutral snapshot. Package-private so tests can call it directly. */
    static PodSnapshot toSnapshot(Pod pod, String workloadContainer) {
        String name = pod.getMetadata().getName();
        Map<String, String> labels = pod.getMetadata().getLabels() != null
                ? pod.getMetadata().getLabels()
                : Map.of();

        PodPhase phase = PodPhase.parse(pod.getStatus() != null ? pod.getStatus().getPhase() : null);
        String podReason = pod.getStatus() != null ? pod.getStatus().getReason() : null;
        String podMessage = pod.getStatus() != null ? pod.getStatus().getMessage() : null;
        String node = pod.getSpec() != null ? pod.getSpec().getNodeName() : null;

        ContainerStateTerminated terminated = findTerminated(pod, workloadContainer);
        Integer exitCode = null;
        String reason = podReason;
        String message = podMessage;
        Instant startedAt = null;
        Instant finishedAt = null;
        if (terminated != null) {
            exitCode = terminated.getExitCode();
            if (reason == null) {
                reason = terminated.getReason();
            }
            if (message == null) {
                message = terminated.getMessage();
            }
            startedAt = parseInstant(terminated.getStartedAt());
            finishedAt = parseInstant(terminated.getFinishedAt());
        }
        if (startedAt == null && pod.getStatus() != null) {
            startedAt = parseInstant(pod.getStatus().getStartTime());
        }

        return new PodSnapshot(
                name,
                parseUuid(labels.get(PodLabels.JOB_ID)),
                parseAttempt(labels.get(PodLabels.ATTEMPT)),
                phase,
                node,
                exitCode,
                reason,
                message,
                startedAt,
                finishedAt);
    }

    /** The named workload container's terminated state, else any terminated container. */
    private static ContainerStateTerminated findTerminated(Pod pod, String workloadContainer) {
        if (pod.getStatus() == null || pod.getStatus().getContainerStatuses() == null) {
            return null;
        }
        ContainerStateTerminated fallback = null;
        for (ContainerStatus status : pod.getStatus().getContainerStatuses()) {
            ContainerState state = status.getState();
            if (state == null || state.getTerminated() == null) {
                continue;
            }
            if (workloadContainer.equals(status.getName())) {
                return state.getTerminated();
            }
            if (fallback == null) {
                fallback = state.getTerminated();
            }
        }
        return fallback;
    }

    private static UUID parseUuid(String value) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            logger.warn("Label {} is not a UUID: {}", PodLabels.JOB_ID, value);
            return null;
        }
    }

    private static Integer parseAttempt(String value) {
        if (value == null) {
            return null;
        }
        try {
            int attempt = Integer.parseInt(value);
            return attempt >= 1 ? attempt : null;
        } catch (NumberFormatException ex) {
            logger.warn("Label {} is not an integer: {}", PodLabels.ATTEMPT, value);
            return null;
        }
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }
}
