package dev.logtriage.demo.orders;

import dev.logtriage.demo.orders.Model.CreateOrderRequest;
import dev.logtriage.demo.orders.Model.Order;
import dev.logtriage.demo.orders.Model.PaymentRequest;
import dev.logtriage.demo.orders.Model.PaymentResponse;
import dev.logtriage.demo.orders.Model.ReservationRequest;
import dev.logtriage.demo.orders.Model.ReservationResponse;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

@Service
class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    private static final int INVENTORY_ATTEMPTS = 2;

    private final RestClient inventory;
    private final RestClient payments;
    private final Map<String, Order> orders = new ConcurrentHashMap<>();

    OrderService(RestClient.Builder builder,
                 @Value("${clients.inventory.url}") String inventoryUrl,
                 @Value("${clients.payment.url}") String paymentUrl) {
        this.inventory = builder.clone().baseUrl(inventoryUrl).build();
        this.payments = builder.clone().baseUrl(paymentUrl).build();
    }

    Order create(CreateOrderRequest request) {
        String orderId = "ord-" + UUID.randomUUID().toString().substring(0, 8);
        BigDecimal total = request.items().stream()
                .map(i -> i.unitPrice().multiply(BigDecimal.valueOf(i.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        log.info("Creating order {} with {} item(s), total {} EUR", orderId, request.items().size(), total);

        ReservationResponse reservation = reserve(orderId, request);
        log.info("Order {} reserved stock as {}", orderId, reservation.reservationId());

        PaymentResponse payment = payments.post().uri("/payments")
                .body(new PaymentRequest(orderId, total, request.customer(), request.cardLast4()))
                .retrieve()
                .body(PaymentResponse.class);
        log.info("Order {} paid with {} ({})", orderId, payment.paymentId(), payment.status());

        Order order = new Order(orderId, request.customer(), request.items(), total, "CONFIRMED",
                reservation.reservationId(), payment.paymentId());
        orders.put(orderId, order);
        log.info("Order {} confirmed", orderId);
        return order;
    }

    Optional<Order> find(String id) {
        return Optional.ofNullable(orders.get(id));
    }

    /** One retry on 503: inventory is known to have short blips. */
    private ReservationResponse reserve(String orderId, CreateOrderRequest request) {
        for (int attempt = 1; ; attempt++) {
            try {
                return inventory.post().uri("/reservations")
                        .body(new ReservationRequest(orderId, request.items()))
                        .retrieve()
                        .body(ReservationResponse.class);
            } catch (HttpServerErrorException e) {
                boolean retryable = e.getStatusCode().value() == HttpStatus.SERVICE_UNAVAILABLE.value();
                if (!retryable || attempt >= INVENTORY_ATTEMPTS) {
                    throw e;
                }
                log.warn("Inventory reservation for order {} returned {}, retrying (attempt {}/{})",
                        orderId, e.getStatusCode().value(), attempt + 1, INVENTORY_ATTEMPTS);
                sleep(150);
            }
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
