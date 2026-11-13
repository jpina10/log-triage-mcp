package dev.logtriage.demo.orders;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.List;

final class Model {

    private Model() {
    }

    record Customer(@NotBlank String name, @Email @NotBlank String email, String phone, @NotBlank String country) {
    }

    record OrderItem(@NotBlank String sku, @Positive int quantity, @NotNull @Positive BigDecimal unitPrice) {
    }

    record CreateOrderRequest(@Valid @NotNull Customer customer, @Valid @NotEmpty List<OrderItem> items,
                              @NotBlank String cardLast4) {
    }

    record Order(String id, Customer customer, List<OrderItem> items, BigDecimal total, String status,
                 String reservationId, String paymentId) {
    }

    record ReservationRequest(String orderId, List<OrderItem> items) {
    }

    record ReservationResponse(String reservationId) {
    }

    record PaymentRequest(String orderId, BigDecimal amount, Customer customer, String cardLast4) {
    }

    record PaymentResponse(String paymentId, String status) {
    }
}
