package dev.logtriage.demo.inventory;

import dev.logtriage.demo.common.FaultSwitches;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ReservationController {

    private static final Logger log = LoggerFactory.getLogger(ReservationController.class);
    private static final String LIMITED_SKU = "SKU-LIMITED";
    private static final int LIMITED_RESTOCK = 5;

    record Item(String sku, int quantity) {
    }

    record ReservationRequest(String orderId, List<Item> items) {
    }

    record ReservationResponse(String reservationId) {
    }

    @ResponseStatus(HttpStatus.CONFLICT)
    static class OutOfStockException extends RuntimeException {
        OutOfStockException(String sku, int requested, int available) {
            super("Insufficient stock for " + sku + ": requested " + requested + ", available " + available);
        }
    }

    /** What a pooled datastore throws when it runs dry. */
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    static class StoreUnavailableException extends RuntimeException {
        StoreUnavailableException(String message) {
            super(message);
        }
    }

    private final FaultSwitches faults;
    private final AtomicLong calls = new AtomicLong();
    private final Map<String, AtomicInteger> limitedStock = new ConcurrentHashMap<>(
            Map.of(LIMITED_SKU, new AtomicInteger(LIMITED_RESTOCK)));

    ReservationController(FaultSwitches faults) {
        this.faults = faults;
    }

    @PostMapping("/reservations")
    ReservationResponse reserve(@RequestBody ReservationRequest request) {
        long call = calls.incrementAndGet();
        if (faults.isOn("flaky-store") && call % 2 == 1) {
            throw new StoreUnavailableException(
                    "Could not acquire connection from pool 'inventory-db' within 50ms (active=10, idle=0, waiting=4)");
        }
        for (Item item : request.items()) {
            AtomicInteger stock = limitedStock.get(item.sku());
            if (stock != null) {
                int left = stock.addAndGet(-item.quantity());
                if (left < 0) {
                    stock.addAndGet(item.quantity());
                    throw new OutOfStockException(item.sku(), item.quantity(), Math.max(0, left + item.quantity()));
                }
            }
        }
        String reservationId = "res-" + UUID.randomUUID().toString().substring(0, 8);
        log.info("Reserved {} line(s) for order {} as {}", request.items().size(), request.orderId(), reservationId);
        return new ReservationResponse(reservationId);
    }

    @Scheduled(fixedDelay = 30_000)
    void restock() {
        limitedStock.forEach((sku, stock) -> {
            if (stock.get() < LIMITED_RESTOCK) {
                stock.set(LIMITED_RESTOCK);
                log.info("Restocked {} to {} units", sku, LIMITED_RESTOCK);
            }
        });
    }
}
