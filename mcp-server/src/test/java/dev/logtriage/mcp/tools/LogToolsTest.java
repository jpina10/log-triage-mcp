package dev.logtriage.mcp.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.logtriage.mcp.LogTriageProperties;
import dev.logtriage.mcp.kube.ClusterReader;
import dev.logtriage.mcp.kube.CommandRunner;
import dev.logtriage.mcp.kube.FakeRunner;
import dev.logtriage.mcp.kube.Kubectl;
import dev.logtriage.mcp.logs.LogSource;
import java.lang.reflect.Constructor;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LogToolsTest {

    private static final String DEPLOYMENTS = """
            {"items":[
              {"metadata":{"name":"orders-api"},"spec":{"replicas":1},"status":{"readyReplicas":1}},
              {"metadata":{"name":"payment-service"},"spec":{"replicas":1},"status":{}}
            ]}""";

    private static final String ORDERS_LOGS = String.join("\n",
            line("orders-api-1", "15:00:01", "INFO", "inbound",
                    "HTTP POST /orders -> 201 in 60ms body={\\\"customer\\\":{\\\"name\\\":\\\"Ana\\\"}}", "tx-a"),
            line("orders-api-1", "15:00:02", "WARN", "outbound",
                    "Outbound POST http://payment-service:8080/payments failed after 3001ms", "tx-b"),
            line("orders-api-1", "15:00:03", "ERROR", "dev.x.ApiErrorHandler",
                    "Request POST /orders failed with 504", "tx-b"),
            "[pod/orders-api-1/app] 2026-11-13T14:59:00Z OpenJDK 64-Bit Server VM warning: SVE disabled");

    private static final String PODS = """
            {"items":[{"metadata":{"name":"payment-service-1"},
              "spec":{"containers":[{"image":"log-triage/payment-service:dev","resources":{"limits":{"memory":"384Mi"}}}]},
              "status":{"phase":"Running","containerStatuses":[{"ready":false,"restartCount":3,
                "state":{"waiting":{"reason":"CrashLoopBackOff","message":"back-off 40s"}},
                "lastState":{"terminated":{"reason":"Error","exitCode":1,"finishedAt":"2026-11-13T15:01:00Z"}}}]}}]}""";

    private static final String EVENTS = """
            {"items":[{"type":"Warning","reason":"BackOff","count":4,"message":"Back-off restarting failed container",
              "lastTimestamp":"2026-11-13T15:01:10Z"}]}""";

    private FakeRunner runner;
    private LogTools tools;

    @BeforeEach
    void setUp() throws Exception {
        runner = new FakeRunner()
                .on("get deployments", DEPLOYMENTS)
                .on("logs -l app=orders-api", ORDERS_LOGS)
                .on("logs -l app=payment-service", new CommandRunner.Result(1, "",
                        "Error from server (BadRequest): previous terminated container \"app\" not found"))
                .on("get pods -l app=payment-service", PODS)
                .on("get events", EVENTS);
        Kubectl kubectl = new Kubectl(
                new LogTriageProperties("kind-log-triage", "demo", "app", "kubectl", Duration.ofSeconds(5), 5000),
                runner);
        tools = new LogTools(new ClusterReader(kubectl), logSource(kubectl));
    }

    @Test
    void defaultQueryShowsWarnAndErrorAndSaysWhatWasSkipped() {
        String out = tools.queryLogs("orders-api", null, null, null, null, null, null);

        assertThat(out).contains("orders-api · last 15m · level WARN+ · 2 matching of 4 lines read")
                .contains("WARN [orders-api-1] outbound tx=tx-b")
                .contains("ERROR [orders-api-1] ApiErrorHandler tx=tx-b | Request POST /orders failed with 504")
                .contains("1 plain-text line(s) without a level were skipped")
                .doesNotContain("HTTP POST /orders -> 201");
    }

    @Test
    void levelAllIncludesPlainTextAndRequestBodies() {
        String out = tools.queryLogs("orders-api", "10m", "ALL", null, null, null, null);

        assertThat(out).contains("4 matching of 4 lines read")
                .contains("SVE disabled  (plain text)")
                .contains("body={\"customer\":{\"name\":\"Ana\"}}");
    }

    @Test
    void filtersByTransactionIdAndText() {
        assertThat(tools.queryLogs("orders-api", null, "ALL", null, "tx-a", null, null))
                .contains("1 matching").contains("HTTP POST /orders -> 201");
        assertThat(tools.queryLogs("orders-api", null, "ALL", "PAYMENT-SERVICE", null, null, null))
                .contains("1 matching").contains("failed after 3001ms");
    }

    @Test
    void limitKeepsNewestEntries() {
        String out = tools.queryLogs("orders-api", null, "WARN", null, null, null, 1);

        assertThat(out).contains("showing newest 1").contains("failed with 504").doesNotContain("3001ms");
    }

    @Test
    void sourceErrorIsReportedNotThrown() {
        String out = tools.queryLogs("payment-service", null, null, null, null, true, null);

        assertThat(out).contains("(previous container)")
                .contains("previous terminated container \"app\" not found")
                .contains("No matching entries.");
    }

    @Test
    void rejectsUnknownServiceAndBadArguments() {
        assertThatThrownBy(() -> tools.queryLogs("billing", null, null, null, null, null, null))
                .hasMessageContaining("Unknown service 'billing'").hasMessageContaining("orders-api");
        assertThatThrownBy(() -> tools.queryLogs("orders-api; rm -rf /", null, null, null, null, null, null))
                .hasMessageContaining("Invalid service name");
        assertThatThrownBy(() -> tools.queryLogs("orders-api", "10 minutes", null, null, null, null, null))
                .hasMessageContaining("Invalid 'since'");
        assertThatThrownBy(() -> tools.queryLogs("orders-api", "999h", null, null, null, null, null))
                .hasMessageContaining("capped at 7 days");
        assertThatThrownBy(() -> tools.queryLogs("orders-api", null, "LOUD", null, null, null, null))
                .hasMessageContaining("Invalid level");
        assertThatThrownBy(() -> tools.queryLogs("orders-api", null, null, null, null, null, 500))
                .hasMessageContaining("limit");
    }

    @Test
    void findByTransactionIdMergesServices() {
        String out = tools.findByTransactionId("tx-b", null);

        assertThat(out).contains("tx=tx-b · last 1h · 2 entries across 1 service(s)")
                .contains("payment-service: kubectl:");
    }

    @Test
    void listServicesFlagsNotReady() {
        assertThat(tools.listServices())
                .contains("- orders-api  ready 1/1")
                .contains("- payment-service  ready 0/1  (NOT READY: check pod_status)");
    }

    @Test
    void podStatusShowsCrashLoopTerminationAndEvents() {
        String out = tools.podStatus("payment-service");

        assertThat(out).contains("payment-service-1")
                .contains("restarts: 3")
                .contains("Waiting: CrashLoopBackOff - back-off 40s")
                .contains("last termination: Error (exit 1)")
                .contains("memory limit: 384Mi")
                .contains("Warning BackOff (x4): Back-off restarting failed container");
    }

    private static String line(String pod, String time, String level, String logger, String message, String tx) {
        return "[pod/%s/app] 2026-11-13T%sZ {\"@timestamp\":\"2026-11-13T%s.000Z\",\"level\":\"%s\",\"logger_name\":\"%s\",\"message\":\"%s\",\"transactionId\":\"%s\"}"
                .formatted(pod, time, time, level, logger, message, tx);
    }

    /** KubectlLogSource is package-private on purpose; tests reach it reflectively. */
    private static LogSource logSource(Kubectl kubectl) throws Exception {
        Class<?> type = Class.forName("dev.logtriage.mcp.logs.KubectlLogSource");
        Constructor<?> ctor = type.getDeclaredConstructor(Kubectl.class);
        ctor.setAccessible(true);
        return (LogSource) ctor.newInstance(kubectl);
    }
}
