package dev.logtriage.demo.fraud;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "dev.logtriage.demo")
public class FraudCheckApplication {

    public static void main(String[] args) {
        SpringApplication.run(FraudCheckApplication.class, args);
    }
}
