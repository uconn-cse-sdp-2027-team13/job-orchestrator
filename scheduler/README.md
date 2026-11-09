# Scheduler (SCHED-1)

Converts a shared `JobMessage` into a `batch/v1` Kubernetes Job and submits it
through Fabric8. Implements the existing `common.scheduler.JobScheduler`
interface used by `ConsumerService`. The design document's proposed
`ClusterClient` name is not introduced as a second shared interface.

Each job/attempt gets a deterministic name, `job-<UUID>-a<attempt>`.
A repeated submission reuses the existing Job only when its `job_id` and
`attempt` labels match. Other Kubernetes API failures propagate to the consumer,
which already records `DISPATCHED` and acknowledges Redis only after submission.

Jobs and their pod templates carry `job_id` and `attempt` labels for TRACKER-1.
Pods receive `JOB_ID`, `FILE_PATH`, and any configured workload environment
variables. Kubernetes retries are disabled (`backoffLimit: 0`,
`restartPolicy: Never`), leaving retry policy with the orchestration layer.

## Usage

Add a dependency on the `scheduler` artifact when wiring the consumer entrypoint
in CORE-3. The caller creates and closes the Kubernetes client:

```java
import edu.uconn.cse.team13.common.scheduler.JobScheduler;
import edu.uconn.cse.team13.scheduler.Fabric8JobScheduler;
import edu.uconn.cse.team13.scheduler.KubernetesJobConfig;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import java.util.Map;

// message is the JobMessage received from the queue.
try (KubernetesClient client = new KubernetesClientBuilder().build()) {
    var config = new KubernetesJobConfig(
            "default", "batch-workload:local", "/srv/job-data",
            Map.of("MYSQL_URL", "jdbc:mysql://mysql:3306/app"));
    JobScheduler scheduler = new Fabric8JobScheduler(client, config);
    String kubernetesJobName = scheduler.submit(message);
}
```

Fabric8 reads the local kubeconfig or in-cluster service-account configuration.
The namespace must already exist and the client must be allowed to create and get
Jobs there. Use cluster-reachable MySQL settings in workload environment variables.

The four-argument configuration defaults to CPU request/limit `100m/1`, memory
request/limit `128Mi/512Mi`, a 3600-second deadline, and a 3600-second cleanup TTL.
The full constructor accepts resource maps, deadline, and TTL overrides.

For the first local demo, the configured host directory must already exist on the
**Kubernetes node** and contain the producer's files. It is mounted read-only at
`/data`; `FILE_PATH` is passed through unchanged. With Kind, expose the host
directory using `extraMounts`; with Docker Desktop, make it available to its node.
The workload image must be available to the cluster (`kind load docker-image`
when using Kind); pods use `IfNotPresent`. This module uses a local shared volume;
object-storage integration is separate work.

## Verification

Requires a full JDK 17 or newer, including its compiler.

```sh
./mvnw -B -ntp -pl scheduler -am verify
./mvnw -B -ntp verify
```

Tests check the generated pod contract and submit requests to Fabric8's in-memory
mock Kubernetes API server. They cover successful submission, duplicate delivery,
separate attempts, name collisions, and API failures without requiring a cluster.
