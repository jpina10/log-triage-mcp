package dev.logtriage.mcp.kube;

import java.time.Duration;
import java.util.List;

/** Runs a program with an argument list (never through a shell). */
public interface CommandRunner {

    Result run(List<String> command, Duration timeout);

    record Result(int exitCode, String stdout, String stderr) {

        public boolean ok() {
            return exitCode == 0;
        }
    }
}
