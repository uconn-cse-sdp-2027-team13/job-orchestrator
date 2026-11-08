package edu.uconn.cse.team13.statustracker;

import edu.uconn.cse.team13.statustracker.PodSource.ChangeType;
import edu.uconn.cse.team13.statustracker.PodSource.PodChange;
import edu.uconn.cse.team13.statustracker.PodSource.PodWatch;
import edu.uconn.cse.team13.statustracker.PodSource.PodWatchListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Runs the tracker: reconcile, watch, and on any watch failure start over with a fresh list.
 *
 * <p>Re-listing after every disconnect (instead of trying to resume) covers every way a watch can
 * lose events, including an expired resource version (HTTP 410), and reuses the one reconcile
 * code path. If handling an event fails (for example MySQL is briefly down) the watch is dropped
 * for the same reason: the next reconcile replays whatever was missed.
 */
public final class StatusTrackerService implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(StatusTrackerService.class);

    private final PodSource podSource;
    private final PodEventProcessor processor;
    private final Reconciler reconciler;
    private final Duration minBackoff;
    private final Duration maxBackoff;

    private final BlockingQueue<Object> restartSignal = new LinkedBlockingQueue<>(1);
    private volatile boolean stopped;
    private Thread worker;

    public StatusTrackerService(
            PodSource podSource,
            PodEventProcessor processor,
            Reconciler reconciler,
            Duration minBackoff,
            Duration maxBackoff) {
        this.podSource = Objects.requireNonNull(podSource, "podSource must not be null");
        this.processor = Objects.requireNonNull(processor, "processor must not be null");
        this.reconciler = Objects.requireNonNull(reconciler, "reconciler must not be null");
        this.minBackoff = Objects.requireNonNull(minBackoff, "minBackoff must not be null");
        this.maxBackoff = Objects.requireNonNull(maxBackoff, "maxBackoff must not be null");
    }

    /** Starts the tracker on a background thread. */
    public synchronized void start() {
        if (worker != null) {
            throw new IllegalStateException("already started");
        }
        worker = new Thread(this::runLoop, "status-tracker");
        worker.start();
    }

    /** Blocks until {@link #close()} is called from another thread. */
    public void awaitTermination() throws InterruptedException {
        Thread t;
        synchronized (this) {
            t = worker;
        }
        if (t != null) {
            t.join();
        }
    }

    @Override
    public void close() {
        stopped = true;
        restartSignal.offer(this);
        Thread t;
        synchronized (this) {
            t = worker;
        }
        if (t != null) {
            t.interrupt();
            try {
                t.join(TimeUnit.SECONDS.toMillis(10));
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void runLoop() {
        Duration backoff = minBackoff;
        while (!stopped) {
            PodWatch watch = null;
            try {
                Reconciler.Result result = reconciler.reconcile();
                backoff = minBackoff;
                restartSignal.clear();
                watch = podSource.watch(result.resourceVersion(), new Listener());
                logger.info("Watching pods from resourceVersion {}", result.resourceVersion());
                restartSignal.take();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            } catch (RuntimeException ex) {
                logger.error("Tracker cycle failed; retrying in {}", backoff, ex);
            } finally {
                if (watch != null) {
                    closeQuietly(watch);
                }
            }
            if (stopped) {
                break;
            }
            try {
                Thread.sleep(backoff.toMillis());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            }
            backoff = backoff.multipliedBy(2).compareTo(maxBackoff) > 0 ? maxBackoff : backoff.multipliedBy(2);
        }
        logger.info("Status tracker stopped");
    }

    private static void closeQuietly(PodWatch watch) {
        try {
            watch.close();
        } catch (RuntimeException ex) {
            logger.debug("Ignoring error while closing watch", ex);
        }
    }

    private final class Listener implements PodWatchListener {

        @Override
        public void onChange(PodChange change) {
            try {
                PodEventType type = change.type() == ChangeType.DELETED
                        ? PodEventType.DELETED
                        : PodEventType.OBSERVED;
                processor.handle(change.pod(), type);
            } catch (RuntimeException ex) {
                logger.error("Failed to handle change for pod {}; re-listing", change.pod().podName(), ex);
                restartSignal.offer(this);
            }
        }

        @Override
        public void onClosed(Throwable cause) {
            if (!stopped) {
                if (cause != null) {
                    logger.warn("Pod watch closed: {}; re-listing", cause.toString());
                } else {
                    logger.info("Pod watch closed; re-listing");
                }
            }
            restartSignal.offer(this);
        }
    }
}
