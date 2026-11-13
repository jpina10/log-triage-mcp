package dev.logtriage.mcp.kube;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
class ProcessCommandRunner implements CommandRunner {

    @Override
    public Result run(List<String> command, Duration timeout) {
        Process process;
        try {
            process = new ProcessBuilder(command).start();
        } catch (IOException e) {
            return new Result(127, "", "could not start " + command.getFirst() + ": " + e.getMessage());
        }
        try {
            process.getOutputStream().close();
        } catch (IOException ignored) {
            // nothing is ever written to kubectl's stdin
        }
        // Drain both streams concurrently, so a full stderr pipe can't block stdout.
        CompletableFuture<String> out = CompletableFuture.supplyAsync(() -> read(process.getInputStream()));
        CompletableFuture<String> err = CompletableFuture.supplyAsync(() -> read(process.getErrorStream()));
        try {
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                return new Result(124, "", "timed out after " + timeout.toSeconds() + "s");
            }
            return new Result(process.exitValue(), out.get(), err.get());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            return new Result(130, "", "interrupted");
        } catch (ExecutionException e) {
            return new Result(1, "", "failed reading output: " + e.getCause());
        }
    }

    private static String read(InputStream in) {
        try (in) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
