package dev.logtriage.mcp.logs;

import java.util.List;

/**
 * Where logs come from. v1 has one implementation, {@link KubectlLogSource}
 * (dev clusters). A centralised backend (Elastic, OpenSearch, CloudWatch,
 * Loki) for prod becomes a second implementation of this same interface.
 */
public interface LogSource {

    /**
     * @param since    how far back, as a kubectl-style duration (10m, 2h)
     * @param previous read the previous (crashed) container instead of the current one
     */
    Batch fetch(String service, String since, boolean previous);

    /**
     * @param entries   parsed entries, oldest first
     * @param linesRead raw lines read from the source
     * @param notice    a source-side message worth passing on (e.g. no
     *                  previous container), or null
     */
    record Batch(List<LogEntry> entries, int linesRead, String notice) {
    }
}
