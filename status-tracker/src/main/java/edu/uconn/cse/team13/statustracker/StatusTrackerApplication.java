package edu.uconn.cse.team13.statustracker;

import edu.uconn.cse.team13.common.db.DataSourceFactory;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;

import javax.sql.DataSource;
import java.time.Clock;

/** Entry point: wires the tracker from environment variables and runs until the JVM stops. */
public final class StatusTrackerApplication {

    private StatusTrackerApplication() {
    }

    public static void main(String[] args) throws InterruptedException {
        StatusTrackerConfig config = StatusTrackerConfig.fromEnv(System.getenv());

        DataSource dataSource = DataSourceFactory.create(config.jdbcUrl(), config.dbUser(), config.dbPassword());
        StatusRepository repository = new JdbcStatusRepository(dataSource);

        KubernetesClient client = new KubernetesClientBuilder().build();
        PodSource podSource = new Fabric8PodSource(client, config.namespace(), config.workloadContainer());

        PodEventProcessor processor = new PodEventProcessor(
                repository,
                config.retryPolicy(),
                new ExitCodePolicy(config.nonRetriableExitCodes()),
                Clock.systemUTC());
        Reconciler reconciler = new Reconciler(podSource, processor, repository);

        StatusTrackerService service = new StatusTrackerService(
                podSource, processor, reconciler, config.minBackoff(), config.maxBackoff());

        // Design doc 8.5: close the watch cleanly; startup reconciliation covers anything missed.
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            service.close();
            client.close();
        }, "status-tracker-shutdown"));

        service.start();
        service.awaitTermination();
    }
}
