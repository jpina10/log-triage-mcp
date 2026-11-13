package dev.logtriage.mcp.logs;

import dev.logtriage.mcp.kube.CommandRunner;
import dev.logtriage.mcp.kube.Kubectl;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
class KubectlLogSource implements LogSource {

    private final Kubectl kubectl;

    KubectlLogSource(Kubectl kubectl) {
        this.kubectl = kubectl;
    }

    @Override
    public Batch fetch(String service, String since, boolean previous) {
        CommandRunner.Result result = kubectl.logs(service, since, previous);
        String notice = null;
        if (!result.ok()) {
            // kubectl fails as a whole when, e.g., a pod has no previous
            // container or is still starting. Pass that on rather than throw:
            // it's evidence too.
            notice = "kubectl: " + result.stderr().strip();
        } else if (!result.stderr().isBlank()) {
            notice = "kubectl: " + result.stderr().strip();
        }
        String[] lines = result.stdout().split("\n");
        List<LogEntry> entries = new ArrayList<>(lines.length);
        int read = 0;
        for (String line : lines) {
            LogEntry entry = LogLineParser.parse(service, line);
            if (entry != null) {
                entries.add(entry);
                read++;
            }
        }
        // --prefix interleaves pods; put everything in time order.
        entries.sort(Comparator.comparing(LogEntry::timestamp, Comparator.nullsFirst(Comparator.<Instant>naturalOrder())));
        return new Batch(entries, read, notice);
    }
}
