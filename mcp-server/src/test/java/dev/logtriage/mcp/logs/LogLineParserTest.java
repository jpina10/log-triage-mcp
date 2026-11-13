package dev.logtriage.mcp.logs;

import static org.assertj.core.api.Assertions.assertThat;

import dev.logtriage.mcp.logs.LogEntry.Level;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class LogLineParserTest {

    @Test
    void parsesPrefixedJsonLine() {
        String line = "[pod/orders-api-7f9c/app] 2026-11-13T15:12:11.600000000Z "
                + "{\"@timestamp\":\"2026-11-13T15:12:11.549483Z\",\"level\":\"ERROR\","
                + "\"logger_name\":\"dev.logtriage.demo.common.ApiErrorHandler\","
                + "\"message\":\"Request POST /orders failed with 502\",\"transactionId\":\"tx-1\","
                + "\"stack_trace\":\"java.lang.IllegalStateException: boom\\n\\tat a.B.c(B.java:1)\"}";

        LogEntry e = LogLineParser.parse("orders-api", line);

        assertThat(e.structured()).isTrue();
        assertThat(e.pod()).isEqualTo("orders-api-7f9c");
        assertThat(e.service()).isEqualTo("orders-api");
        assertThat(e.level()).isEqualTo(Level.ERROR);
        assertThat(e.timestamp()).isEqualTo(Instant.parse("2026-11-13T15:12:11.549483Z"));
        assertThat(e.logger()).endsWith("ApiErrorHandler");
        assertThat(e.message()).isEqualTo("Request POST /orders failed with 502");
        assertThat(e.transactionId()).isEqualTo("tx-1");
        assertThat(e.stackTrace()).startsWith("java.lang.IllegalStateException: boom");
    }

    @Test
    void keepsPlainTextLineWithKubectlTimestamp() {
        String line = "[pod/inventory-service-1/app] 2026-11-13T15:22:07.100000000Z "
                + "OpenJDK 64-Bit Server VM warning: Unable to get SVE vector length on this system.";

        LogEntry e = LogLineParser.parse("inventory-service", line);

        assertThat(e.structured()).isFalse();
        assertThat(e.level()).isEqualTo(Level.UNKNOWN);
        assertThat(e.timestamp()).isEqualTo(Instant.parse("2026-11-13T15:22:07.100Z"));
        assertThat(e.message()).startsWith("OpenJDK 64-Bit Server VM warning");
    }

    @Test
    void plainTextLineWithExplicitLevelKeepsIt() {
        LogEntry e = LogLineParser.parse("x", "[pod/x-1/app] 2026-11-13T15:00:00Z 15:00:00 ERROR something broke");

        assertThat(e.level()).isEqualTo(Level.ERROR);
        assertThat(e.structured()).isFalse();
    }

    @Test
    void brokenJsonFallsBackToPlainText() {
        LogEntry e = LogLineParser.parse("x", "[pod/x-1/app] 2026-11-13T15:00:00Z {\"level\":\"ERROR\", truncated");

        assertThat(e.structured()).isFalse();
        assertThat(e.message()).startsWith("{\"level\"");
    }

    @Test
    void unprefixedLineIsStillParsed() {
        LogEntry e = LogLineParser.parse("x", "{\"level\":\"INFO\",\"message\":\"hello\"}");

        assertThat(e.structured()).isTrue();
        assertThat(e.pod()).isNull();
        assertThat(e.message()).isEqualTo("hello");
    }

    @Test
    void blankLineIsSkipped() {
        assertThat(LogLineParser.parse("x", "  ")).isNull();
    }

    @Test
    void unknownLevelNeverPassesALevelFilter() {
        assertThat(Level.UNKNOWN.atLeast(Level.TRACE)).isFalse();
        assertThat(Level.ERROR.atLeast(Level.WARN)).isTrue();
        assertThat(Level.INFO.atLeast(Level.WARN)).isFalse();
    }
}
