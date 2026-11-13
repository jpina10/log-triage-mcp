package dev.logtriage.demo.fraud;

import java.math.BigDecimal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class FraudCheckController {

    record CheckRequest(String orderId, String email, String country, BigDecimal amount) {
    }

    private final FraudScoringService scoring;

    FraudCheckController(FraudScoringService scoring) {
        this.scoring = scoring;
    }

    @PostMapping("/checks")
    FraudScoringService.Result check(@RequestBody CheckRequest request) {
        return scoring.score(request.orderId(), request.country(), request.amount());
    }
}
