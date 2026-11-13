package dev.logtriage.demo.fraud;

import java.math.BigDecimal;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
class FraudScoringService {

    private static final Logger log = LoggerFactory.getLogger(FraudScoringService.class);
    private static final double DECLINE_THRESHOLD = 0.7;

    private static final Map<String, Double> RISK_BY_COUNTRY = Map.of(
            "PT", 0.10, "ES", 0.15, "FR", 0.12, "DE", 0.08, "IT", 0.18, "NL", 0.09);

    record Result(double score, String decision) {
    }

    Result score(String orderId, String country, BigDecimal amount) {
        // KNOWN DEFECT (scenario "fraud-npe"): a country missing from the map
        // unboxes null and throws NullPointerException. Left in on purpose.
        double base = RISK_BY_COUNTRY.get(country);
        double amountFactor = Math.min(amount.doubleValue() / 5_000, 0.6);
        double score = Math.round((base + amountFactor) * 100) / 100.0;
        String decision = score >= DECLINE_THRESHOLD ? "DECLINE" : "APPROVE";
        log.info("Fraud score for order {} ({}, {} EUR): {} -> {}", orderId, country, amount, score, decision);
        return new Result(score, decision);
    }
}
