package dev.logtriage.demo.payment;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Required at startup: without the URL or the API key (from the
 * {@code payment-provider} secret) the service refuses to start.
 */
@Validated
@ConfigurationProperties("payment.provider")
record ProviderProperties(@NotBlank String url, @NotBlank String apiKey) {
}
