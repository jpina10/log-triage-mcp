package dev.logtriage.mcp.kube;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Services (deployments) and pod status, parsed from kubectl JSON. */
@Component
public class ClusterReader {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final int EVENTS_PER_POD = 6;

    private final Kubectl kubectl;

    public ClusterReader(Kubectl kubectl) {
        this.kubectl = kubectl;
    }

    public record ServiceInfo(String name, int ready, int desired) {
    }

    public record Termination(String reason, int exitCode, String finishedAt, String message) {
    }

    public record PodInfo(String name, String phase, boolean ready, int restarts, String state,
                          Termination lastTermination, String memoryLimit, String image, List<String> events) {
    }

    public List<ServiceInfo> services() {
        JsonNode root = json(kubectl.deployments(), "list deployments");
        List<ServiceInfo> out = new ArrayList<>();
        for (JsonNode d : root.path("items")) {
            out.add(new ServiceInfo(
                    d.path("metadata").path("name").asString(),
                    d.path("status").path("readyReplicas").asInt(0),
                    d.path("spec").path("replicas").asInt(0)));
        }
        out.sort(Comparator.comparing(ServiceInfo::name));
        return out;
    }

    public List<String> serviceNames() {
        return services().stream().map(ServiceInfo::name).toList();
    }

    public List<PodInfo> pods(String service) {
        JsonNode root = json(kubectl.pods(service), "list pods of " + service);
        List<PodInfo> out = new ArrayList<>();
        for (JsonNode pod : root.path("items")) {
            String name = pod.path("metadata").path("name").asString();
            JsonNode cs = pod.path("status").path("containerStatuses").path(0);
            JsonNode container = pod.path("spec").path("containers").path(0);
            out.add(new PodInfo(
                    name,
                    pod.path("status").path("phase").asString(),
                    cs.path("ready").asBoolean(false),
                    cs.path("restartCount").asInt(0),
                    state(cs.path("state")),
                    termination(cs.path("lastState").path("terminated")),
                    text(container.path("resources").path("limits").path("memory")),
                    text(container.path("image")),
                    events(name)));
        }
        return out;
    }

    private List<String> events(String podName) {
        CommandRunner.Result result = kubectl.events(podName);
        if (!result.ok()) {
            return List.of("(events unavailable: " + result.stderr().strip() + ")");
        }
        JsonNode root = JSON.readTree(result.stdout());
        List<JsonNode> items = new ArrayList<>();
        root.path("items").forEach(items::add);
        items.sort(Comparator.comparing(ClusterReader::eventTime));
        int from = Math.max(0, items.size() - EVENTS_PER_POD);
        List<String> out = new ArrayList<>();
        for (JsonNode e : items.subList(from, items.size())) {
            int count = e.path("count").asInt(1);
            out.add("%s %s %s%s: %s".formatted(
                    eventTime(e), e.path("type").asString(), e.path("reason").asString(),
                    count > 1 ? " (x" + count + ")" : "", e.path("message").asString()));
        }
        return out;
    }

    private static Instant eventTime(JsonNode e) {
        for (String field : List.of("lastTimestamp", "eventTime", "firstTimestamp")) {
            String raw = e.path(field).asString("");
            if (!raw.isEmpty()) {
                try {
                    return Instant.parse(raw);
                } catch (DateTimeParseException ignored) {
                    // try the next field
                }
            }
        }
        return Instant.EPOCH;
    }

    private static String state(JsonNode state) {
        if (state.has("running")) {
            return "Running since " + state.path("running").path("startedAt").asString();
        }
        if (state.has("waiting")) {
            JsonNode w = state.path("waiting");
            return "Waiting: " + w.path("reason").asString() + messageSuffix(w);
        }
        if (state.has("terminated")) {
            JsonNode t = state.path("terminated");
            return "Terminated: " + t.path("reason").asString() + " (exit " + t.path("exitCode").asInt() + ")";
        }
        return "unknown";
    }

    private static Termination termination(JsonNode t) {
        if (t.isMissingNode() || t.isEmpty()) {
            return null;
        }
        return new Termination(t.path("reason").asString(), t.path("exitCode").asInt(),
                t.path("finishedAt").asString(), text(t.path("message")));
    }

    private static String messageSuffix(JsonNode node) {
        String msg = node.path("message").asString("");
        return msg.isEmpty() ? "" : " - " + (msg.length() > 200 ? msg.substring(0, 200) + "…" : msg);
    }

    private static String text(JsonNode node) {
        return node.isMissingNode() || node.isNull() ? null : node.asString();
    }

    private JsonNode json(CommandRunner.Result result, String what) {
        if (!result.ok()) {
            throw new IllegalStateException("kubectl could not " + what + " in context '" + kubectl.context()
                    + "', namespace '" + kubectl.namespace() + "': " + result.stderr().strip());
        }
        return JSON.readTree(result.stdout());
    }
}
