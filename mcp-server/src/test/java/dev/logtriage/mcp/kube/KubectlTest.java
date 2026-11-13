package dev.logtriage.mcp.kube;

import static org.assertj.core.api.Assertions.assertThat;

import dev.logtriage.mcp.LogTriageProperties;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class KubectlTest {

    private static final Set<String> READ_VERBS = Set.of("get", "logs");

    private final FakeRunner runner = new FakeRunner();
    private final Kubectl kubectl = new Kubectl(
            new LogTriageProperties("kind-log-triage", "demo", "app", "kubectl", Duration.ofSeconds(5), 5000),
            runner);

    @Test
    void everyCallIsPinnedToContextAndNamespaceAndIsARead() {
        kubectl.deployments();
        kubectl.pods("orders-api");
        kubectl.events("orders-api-1");
        kubectl.logs("orders-api", "10m", true);

        assertThat(runner.calls).hasSize(4).allSatisfy(cmd -> {
            assertThat(cmd.subList(0, 5))
                    .containsExactly("kubectl", "--context", "kind-log-triage", "--namespace", "demo");
            assertThat(READ_VERBS).contains(cmd.get(5));
        });
    }

    @Test
    void logsUseSelectorPrefixTimestampsAndTailCap() {
        kubectl.logs("payment-service", "2h", false);

        List<String> cmd = runner.calls.getFirst();
        assertThat(cmd).contains("logs", "-l", "app=payment-service", "--prefix", "--timestamps",
                "--since=2h", "--tail=5000").doesNotContain("--previous");
    }

    @Test
    void previousFlagIsPassedOn() {
        kubectl.logs("payment-service", "10m", true);

        assertThat(runner.calls.getFirst()).contains("--previous");
    }
}
