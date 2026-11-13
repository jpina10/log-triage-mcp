package dev.logtriage.mcp.logs;

import dev.logtriage.mcp.logs.LogEntry.Level;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Parses one line of {@code kubectl logs --prefix --timestamps} output. JSON
 * bodies (Logstash/ECS-like field names) become structured entries; anything
 * else is kept as plain text, because real pods mix both.
 */
public final class LogLineParser {

    /** {@code [pod/<pod>/<container>] <rfc3339> <body>} */
    private static final Pattern PREFIXED = Pattern.compile("^\\[pod/([^/\\]]+)/([^\\]]+)] (\\S+) ?(.*)$");
    private static final Pattern PLAIN_LEVEL = Pattern.compile("\\b(TRACE|DEBUG|INFO|WARN|WARNING|ERROR|FATAL)\\b");
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private LogLineParser() {
    }

    /** Returns null for a blank line. */
    public static LogEntry parse(String service, String line) {
        if (line == null || line.isBlank()) {
            return null;
        }
        String pod = null;
        Instant kubeTime = null;
        String body = line;
        Matcher m = PREFIXED.matcher(line);
        if (m.matches()) {
            pod = m.group(1);
            kubeTime = instant(m.group(3));
            body = m.group(4);
        }
        if (body.startsWith("{")) {
            LogEntry structured = parseJson(service, pod, kubeTime, body);
            if (structured != null) {
                return structured;
            }
        }
        return new LogEntry(kubeTime, service, pod, plainLevel(body), null, body, null, null, false);
    }

    private static LogEntry parseJson(String service, String pod, Instant kubeTime, String body) {
        JsonNode node;
        try {
            node = JSON.readTree(body);
        } catch (JacksonException e) {
            return null;
        }
        if (!node.isObject()) {
            return null;
        }
        Instant ts = instant(text(node, "@timestamp", "timestamp", "time"));
        return new LogEntry(
                ts != null ? ts : kubeTime,
                service,
                pod,
                Level.parse(text(node, "level", "log.level", "severity")),
                text(node, "logger_name", "logger", "log.logger"),
                text(node, "message", "msg"),
                text(node, "transactionId", "transaction_id", "traceId", "trace_id"),
                text(node, "stack_trace", "error.stack_trace", "exception"),
                true);
    }

    private static String text(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value != null && !value.isNull()) {
                return value.asString();
            }
        }
        return null;
    }

    private static Level plainLevel(String body) {
        Matcher m = PLAIN_LEVEL.matcher(body);
        return m.find() ? Level.parse(m.group(1)) : Level.UNKNOWN;
    }

    private static Instant instant(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return Instant.parse(raw);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
