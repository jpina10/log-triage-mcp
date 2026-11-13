package dev.logtriage.mcp.kube;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Answers kubectl calls from canned output, keyed by a substring of the command. */
public class FakeRunner implements CommandRunner {

    public final List<List<String>> calls = new ArrayList<>();
    private final Map<String, Result> answers = new LinkedHashMap<>();

    public FakeRunner on(String commandContains, Result result) {
        answers.put(commandContains, result);
        return this;
    }

    public FakeRunner on(String commandContains, String stdout) {
        return on(commandContains, new Result(0, stdout, ""));
    }

    @Override
    public Result run(List<String> command, Duration timeout) {
        calls.add(command);
        String joined = String.join(" ", command);
        return answers.entrySet().stream()
                .filter(a -> joined.contains(a.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(new Result(1, "", "no fake answer for: " + joined));
    }
}
