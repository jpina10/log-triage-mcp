package dev.logtriage.mcp.tools;

import dev.logtriage.mcp.logs.LogEntry;
import java.util.List;

/**
 * Renders entries as compact text for the model. Caps keep a single tool
 * result small: long messages and stack traces are cut, and the cut is
 * stated, never silent.
 */
final class LogFormat {

    static final int MAX_MESSAGE_CHARS = 600;
    static final int MAX_STACK_LINES = 6;

    private LogFormat() {
    }

    static void appendEntries(StringBuilder out, List<LogEntry> entries, boolean showService) {
        for (LogEntry e : entries) {
            out.append(e.timestamp() == null ? "(no time)" : e.timestamp().toString())
                    .append(' ').append(e.level());
            if (showService) {
                out.append(' ').append(e.service());
            }
            if (e.pod() != null) {
                out.append(" [").append(e.pod()).append(']');
            }
            if (e.logger() != null) {
                out.append(' ').append(shortLogger(e.logger()));
            }
            if (e.transactionId() != null) {
                out.append(" tx=").append(e.transactionId());
            }
            out.append(" | ").append(cut(e.message(), MAX_MESSAGE_CHARS));
            if (!e.structured()) {
                out.append("  (plain text)");
            }
            out.append('\n');
            appendStack(out, e.stackTrace());
        }
    }

    private static void appendStack(StringBuilder out, String stack) {
        if (stack == null || stack.isBlank()) {
            return;
        }
        String[] lines = stack.strip().split("\n");
        int shown = Math.min(lines.length, MAX_STACK_LINES);
        for (int i = 0; i < shown; i++) {
            out.append("    ").append(cut(lines[i].strip(), 300)).append('\n');
        }
        if (lines.length > shown) {
            out.append("    … ").append(lines.length - shown).append(" more stack lines\n");
        }
    }

    static String shortLogger(String logger) {
        int dot = logger.lastIndexOf('.');
        return dot >= 0 ? logger.substring(dot + 1) : logger;
    }

    static String cut(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "… (+" + (text.length() - max) + " chars)";
    }
}
