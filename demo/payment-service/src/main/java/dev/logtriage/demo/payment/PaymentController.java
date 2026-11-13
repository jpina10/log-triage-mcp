package dev.logtriage.demo.payment;

import dev.logtriage.demo.common.FaultSwitches;
import java.math.BigDecimal;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

@RestController
class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    record Customer(String name, String email, String phone, String country) {
    }

    record PaymentRequest(String orderId, BigDecimal amount, Customer customer, String cardLast4) {
    }

    record PaymentResponse(String paymentId, String status) {
    }

    record FraudCheckRequest(String orderId, String email, String country, BigDecimal amount) {
    }

    record FraudCheckResponse(double score, String decision) {
    }

    @ResponseStatus(HttpStatus.UNPROCESSABLE_CONTENT)
    static class PaymentDeclinedException extends RuntimeException {
        PaymentDeclinedException(String orderId, double score) {
            super("Payment for order " + orderId + " declined by fraud check (score " + score + ")");
        }
    }

    private final RestClient fraud;
    private final ProviderProperties provider;
    private final FaultSwitches faults;

    PaymentController(RestClient.Builder builder, @Value("${clients.fraud.url}") String fraudUrl,
                      ProviderProperties provider, FaultSwitches faults) {
        this.fraud = builder.clone().baseUrl(fraudUrl).build();
        this.provider = provider;
        this.faults = faults;
    }

    @PostMapping("/payments")
    PaymentResponse pay(@RequestBody PaymentRequest request) {
        FraudCheckResponse check = fraud.post().uri("/checks")
                .body(new FraudCheckRequest(request.orderId(), request.customer().email(),
                        request.customer().country(), request.amount()))
                .retrieve()
                .body(FraudCheckResponse.class);
        if ("DECLINE".equals(check.decision())) {
            throw new PaymentDeclinedException(request.orderId(), check.score());
        }
        String paymentId = charge(request);
        log.info("Payment {} captured for order {}: {} EUR", paymentId, request.orderId(), request.amount());
        return new PaymentResponse(paymentId, "CAPTURED");
    }

    /** Stands in for the call to the external payment provider. */
    private String charge(PaymentRequest request) {
        // Leaks the API key on purpose: a redaction target for the log-triage tool.
        log.info("Calling provider {}/charges for order {} with headers {Authorization=Bearer {}}",
                provider.url(), request.orderId(), provider.apiKey());
        long latencyMs = faults.isOn("slow-provider") ? 8_000 : 40 + (long) (Math.random() * 60);
        sleep(latencyMs);
        log.info("Provider answered for order {} in {}ms", request.orderId(), latencyMs);
        return "pay-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
