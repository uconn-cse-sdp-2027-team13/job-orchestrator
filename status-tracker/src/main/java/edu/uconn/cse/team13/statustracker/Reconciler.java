package edu.uconn.cse.team13.statustracker;

import edu.uconn.cse.team13.statustracker.PodSource.PodListing;
import edu.uconn.cse.team13.statustracker.StatusRepository.ActiveAttempt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Startup (and reconnect) reconciliation: list the labelled pods, replay each through the same
 * {@link PodEventProcessor} the watch uses, and report jobs MySQL thinks are active but that have
 * no pod. Design doc 7.6: "lists existing pods first and then watches from that point".
 *
 * <p>Jobs with no pod are only reported, not failed: the pod may simply have been cleaned up by
 * its TTL after finishing while the tracker was down, so the outcome is unknown. Resolving those
 * is the janitor's job (doc 8.4).
 */
public final class Reconciler {

    private static final Logger logger = LoggerFactory.getLogger(Reconciler.class);

    private final PodSource podSource;
    private final PodEventProcessor processor;
    private final StatusRepository repository;

    public Reconciler(PodSource podSource, PodEventProcessor processor, StatusRepository repository) {
        this.podSource = Objects.requireNonNull(podSource, "podSource must not be null");
        this.processor = Objects.requireNonNull(processor, "processor must not be null");
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    public Result reconcile() {
        PodListing listing = podSource.listPods();

        Set<String> podsPresent = new HashSet<>();
        int transitions = 0;
        for (PodSnapshot pod : listing.pods()) {
            if (pod.hasJobIdentity()) {
                podsPresent.add(key(pod.jobId(), pod.attempt()));
            }
            if (processor.handle(pod, PodEventType.RECONCILED).isPresent()) {
                transitions++;
            }
        }

        // Read after replaying pods so jobs we just closed are not reported.
        List<ActiveAttempt> withoutPod = new ArrayList<>();
        for (ActiveAttempt active : repository.findActiveAttempts()) {
            if (!podsPresent.contains(key(active.jobId(), active.attempt()))) {
                withoutPod.add(active);
                logger.warn("job {} attempt {} is {} in MySQL but has no pod in the cluster",
                        active.jobId(), active.attempt(), active.state());
            }
        }

        logger.info("Reconciled {} pods: {} job transitions, {} active jobs without a pod",
                listing.pods().size(), transitions, withoutPod.size());
        return new Result(listing.resourceVersion(), listing.pods().size(), transitions, withoutPod);
    }

    private static String key(UUID jobId, int attempt) {
        return jobId + "#" + attempt;
    }

    /**
     * @param resourceVersion where the watch should start
     * @param activeWithoutPod active jobs in MySQL for which no pod exists
     */
    public record Result(
            String resourceVersion,
            int podsSeen,
            int transitions,
            List<ActiveAttempt> activeWithoutPod) {
    }
}
