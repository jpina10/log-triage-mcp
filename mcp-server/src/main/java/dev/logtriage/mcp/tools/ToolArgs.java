package dev.logtriage.mcp.tools;

import dev.logtriage.mcp.logs.LogEntry.Level;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Validates everything a model passes in. Names and durations reach kubectl,
 * so they must match a strict shape; free text (search terms) is only ever
 * used for in-memory filtering.
 */
final class ToolArgs {

    static final int DEFAULT_LIMIT = 50;
    static final int MAX_LIMIT = 200;

    private static final Pattern SERVICE = Pattern.compile("^[a-z0-9]([-a-z0-9]{0,61}[a-z0-9])?$");
    private static final Pattern SINCE = Pattern.compile("^([1-9][0-9]{0,3})([smh])$");
    private static final Pattern TX_ID = Pattern.compile("^[A-Za-z0-9._:-]{1,128}$");
    private static final long MAX_SINCE_SECONDS = 7 * 24 * 3600;

    private ToolArgs() {
    }

    static String service(String raw, List<String> known) {
        if (raw == null || !SERVICE.matcher(raw.trim()).matches()) {
            throw new IllegalArgumentException("Invalid service name '" + raw + "'. Known services: " + known);
        }
        String service = raw.trim();
        if (!known.contains(service)) {
            throw new IllegalArgumentException("Unknown service '" + service + "'. Known services: " + known);
        }
        return service;
    }

    static String since(String raw, String fallback) {
        String value = raw == null || raw.isBlank() ? fallback : raw.trim();
        var m = SINCE.matcher(value);
        if (!m.matches()) {
            throw new IllegalArgumentException("Invalid 'since' '" + raw + "': use a number and s, m or h, e.g. 10m or 2h");
        }
        long seconds = Long.parseLong(m.group(1)) * switch (m.group(2)) {
            case "s" -> 1;
            case "m" -> 60;
            default -> 3600;
        };
        if (seconds > MAX_SINCE_SECONDS) {
            throw new IllegalArgumentException("'since' is capped at 7 days (168h)");
        }
        return value;
    }

    /** Null means "everything, including plain-text lines without a level". */
    static Level minLevel(String raw, Level fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String value = raw.trim().toUpperCase();
        if (value.equals("ALL")) {
            return null;
        }
        return switch (value) {
            case "ERROR" -> Level.ERROR;
            case "WARN", "WARNING" -> Level.WARN;
            case "INFO" -> Level.INFO;
            case "DEBUG" -> Level.DEBUG;
            case "TRACE" -> Level.TRACE;
            default -> throw new IllegalArgumentException("Invalid level '" + raw + "': use ERROR, WARN, INFO, DEBUG or ALL");
        };
    }

    static String transactionId(String raw, boolean required) {
        if (raw == null || raw.isBlank()) {
            if (required) {
                throw new IllegalArgumentException("transactionId is required");
            }
            return null;
        }
        String value = raw.trim();
        if (!TX_ID.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid transactionId '" + raw + "'");
        }
        return value;
    }

    static int limit(Integer raw) {
        if (raw == null) {
            return DEFAULT_LIMIT;
        }
        if (raw < 1 || raw > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_LIMIT);
        }
        return raw;
    }
}
