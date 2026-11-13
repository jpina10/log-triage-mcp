package dev.logtriage.mcp;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Where the server reads from. The context and namespace are fixed here, never
 * taken from a tool call, so the model cannot point the server at another
 * cluster.
 *
 * @param context        kubectl context to use (always passed explicitly)
 * @param namespace      namespace to read
 * @param serviceLabel   pod label whose value is the service name
 * @param kubectl        kubectl binary
 * @param commandTimeout per kubectl call
 * @param maxLogLines    lines read per pod per call (kubectl --tail)
 */
@Validated
@ConfigurationProperties("logtriage")
public record LogTriageProperties(
        @NotBlank String context,
        @NotBlank String namespace,
        @DefaultValue("app") @NotBlank String serviceLabel,
        @DefaultValue("kubectl") @NotBlank String kubectl,
        @DefaultValue("20s") Duration commandTimeout,
        @DefaultValue("5000") @Min(100) @Max(50_000) int maxLogLines) {
}
