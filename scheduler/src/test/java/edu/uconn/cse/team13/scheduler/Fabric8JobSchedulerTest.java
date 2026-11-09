package edu.uconn.cse.team13.scheduler;

import io.fabric8.kubernetes.api.model.StatusBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static edu.uconn.cse.team13.scheduler.KubernetesJobBuilderTest.config;
import static edu.uconn.cse.team13.scheduler.KubernetesJobBuilderTest.message;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@EnableKubernetesMockClient(crud = true)
class Fabric8JobSchedulerTest {

    KubernetesClient client;
    KubernetesMockServer server;

    @Test
    void submitsToConfiguredNamespaceAndReturnsJobName() {
        var scheduler = new Fabric8JobScheduler(client, config());
        String name = scheduler.submit(message(1));

        var job = client.batch().v1().jobs().inNamespace("scheduler-test").withName(name).get();
        assertEquals(name, job.getMetadata().getName());
        assertEquals(message(1).jobId().toString(), job.getSpec().getTemplate().getMetadata().getLabels().get("job_id"));
    }

    @Test
    void redeliveryReusesAttemptWhileRetryCreatesAnotherJob() {
        var scheduler = new Fabric8JobScheduler(client, config());
        String first = scheduler.submit(message(1));

        assertEquals(first, scheduler.submit(message(1)));
        assertEquals(1, client.batch().v1().jobs().inNamespace("scheduler-test").list().getItems().size());
        assertNotEquals(first, scheduler.submit(message(2)));
        assertEquals(2, client.batch().v1().jobs().inNamespace("scheduler-test").list().getItems().size());
    }

    @Test
    void doesNotTreatAnUnrelatedJobAsSuccessfulDispatch() {
        var unrelated = new KubernetesJobBuilder(config()).build(message(1));
        unrelated.getMetadata().setLabels(Map.of("job_id", "someone-else", "attempt", "1"));
        client.batch().v1().jobs().inNamespace("scheduler-test").resource(unrelated).create();
        var scheduler = new Fabric8JobScheduler(client, config());

        var exception = assertThrows(KubernetesClientException.class, () -> scheduler.submit(message(1)));

        assertEquals(409, exception.getCode());
    }

    @Test
    void propagatesApiErrorsSoConsumerCanLeaveMessageUnacknowledged() {
        server.expect().post().withPath("/apis/batch/v1/namespaces/scheduler-test/jobs")
                .andReturn(403, new StatusBuilder().withCode(403).withMessage("Forbidden").build()).once();
        var scheduler = new Fabric8JobScheduler(client, config());

        var exception = assertThrows(KubernetesClientException.class, () -> scheduler.submit(message(1)));

        assertEquals(403, exception.getCode());
        assertEquals(0, client.batch().v1().jobs().inNamespace("scheduler-test").list().getItems().size());
    }
}
