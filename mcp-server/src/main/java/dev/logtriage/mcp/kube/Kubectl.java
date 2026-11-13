package dev.logtriage.mcp.kube;

import dev.logtriage.mcp.LogTriageProperties;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * The only kubectl calls the server can make. All are reads ({@code get},
 * {@code logs}); every call carries the configured context and namespace;
 * arguments are a list, never a shell string. Callers validate names before
 * they get here.
 */
@Component
public class Kubectl {

    private final LogTriageProperties props;
    private final CommandRunner runner;

    public Kubectl(LogTriageProperties props, CommandRunner runner) {
        this.props = props;
        this.runner = runner;
    }

    public CommandRunner.Result deployments() {
        return run("get", "deployments", "-o", "json");
    }

    public CommandRunner.Result pods(String service) {
        return run("get", "pods", "-l", selector(service), "-o", "json");
    }

    public CommandRunner.Result events(String podName) {
        return run("get", "events", "--field-selector", "involvedObject.name=" + podName, "-o", "json");
    }

    /**
     * Logs of every pod of the service, each line prefixed with
     * {@code [pod/<name>/<container>]} and an RFC 3339 timestamp.
     */
    public CommandRunner.Result logs(String service, String since, boolean previous) {
        List<String> args = new ArrayList<>(List.of(
                "logs", "-l", selector(service), "--prefix", "--timestamps",
                "--since=" + since, "--tail=" + props.maxLogLines(), "--max-log-requests=10"));
        if (previous) {
            args.add("--previous");
        }
        return run(args.toArray(String[]::new));
    }

    public String context() {
        return props.context();
    }

    public String namespace() {
        return props.namespace();
    }

    private String selector(String service) {
        return props.serviceLabel() + "=" + service;
    }

    private CommandRunner.Result run(String... args) {
        List<String> command = new ArrayList<>(List.of(
                props.kubectl(), "--context", props.context(), "--namespace", props.namespace()));
        command.addAll(List.of(args));
        return runner.run(command, props.commandTimeout());
    }
}
