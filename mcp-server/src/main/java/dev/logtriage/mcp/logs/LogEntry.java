package dev.logtriage.mcp.logs;

import java.time.Instant;

/**
 * One log line, normalised.
 *
 * @param timestamp     from the log itself, or kubectl's when the line has none
 * @param level         ERROR, WARN, INFO, DEBUG, TRACE, or UNKNOWN for a line
 *                      that states none
 * @param structured    true for a JSON line, false for plain text
 */
public record LogEntry(
        Instant timestamp,
        String service,
        String pod,
        Level level,
        String logger,
        String message,
        String transactionId,
        String stackTrace,
        boolean structured) {

    public enum Level {
        TRACE, DEBUG, INFO, WARN, ERROR, UNKNOWN;

        public boolean atLeast(Level min) {
            return this != UNKNOWN && min != UNKNOWN && ordinal() >= min.ordinal();
        }

        static Level parse(String raw) {
            if (raw == null) {
                return UNKNOWN;
            }
            return switch (raw.trim().toUpperCase()) {
                case "TRACE" -> TRACE;
                case "DEBUG" -> DEBUG;
                case "INFO" -> INFO;
                case "WARN", "WARNING" -> WARN;
                case "ERROR", "FATAL", "SEVERE" -> ERROR;
                default -> UNKNOWN;
            };
        }
    }
}
