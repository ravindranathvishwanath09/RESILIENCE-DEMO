package com.example.inventoryservice.controller;

import com.example.inventoryservice.model.InventoryResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    @Value("${inventory.simulate-slow-response:false}")
    private boolean simulateSlowResponse;

    @Value("${inventory.simulate-failure:false}")
    private boolean simulateFailure;

    @Value("${inventory.slow-response-delay-ms:500}")
    private int slowResponseDelayMs;

    // In-memory stock — pre-seeded with test products
    private final Map<String, Integer> stock = new ConcurrentHashMap<>(Map.of(
            "PROD-001", 50,
            "PROD-002", 0,   // out of stock — test insufficient inventory path
            "PROD-003", 100
    ));

    /**
     * GET /api/inventory/{productId}?quantity=N
     *
     * Use application.yml flags to simulate:
     *   inventory.simulate-slow-response=true  → triggers TimeLimiter (500ms > 250ms timeout)
     *   inventory.simulate-failure=true        → triggers CircuitBreaker (500 errors)
     *
     * Or use the toggle endpoints below at runtime.
     */
    @GetMapping("/{productId}")
    public ResponseEntity<InventoryResponse> checkInventory(
            @PathVariable String productId,
            @RequestParam(defaultValue = "1") int quantity) throws InterruptedException {

        log.info("Inventory check: productId={} requestedQty={} slowMode={} failMode={}",
                productId, quantity, simulateSlowResponse, simulateFailure);

        // Simulate failure — triggers CircuitBreaker after threshold
        if (simulateFailure) {
            log.warn("Simulating failure for productId={}", productId);
            return ResponseEntity.internalServerError().build();
        }

        // Simulate slow response — triggers TimeLimiter (250ms timeout)
        if (simulateSlowResponse) {
            log.warn("Simulating slow response: sleeping {}ms", slowResponseDelayMs);
            Thread.sleep(slowResponseDelayMs);
        }

        int availableQty = stock.getOrDefault(productId, 0);
        boolean available = availableQty >= quantity;

        log.info("Inventory result: productId={} available={} qty={}", productId, available, availableQty);
        return ResponseEntity.ok(new InventoryResponse(productId, available, availableQty));
    }

    // ---- Runtime toggle endpoints for testing without restart ----

    /** POST /api/inventory/simulate/slow?enabled=true */
    @PostMapping("/simulate/slow")
    public ResponseEntity<String> toggleSlowMode(@RequestParam boolean enabled) {
        this.simulateSlowResponse = enabled;
        return ResponseEntity.ok("Slow mode: " + enabled);
    }

    /** POST /api/inventory/simulate/failure?enabled=true */
    @PostMapping("/simulate/failure")
    public ResponseEntity<String> toggleFailureMode(@RequestParam boolean enabled) {
        this.simulateFailure = enabled;
        return ResponseEntity.ok("Failure mode: " + enabled);
    }

    /** GET /api/inventory/stock — view current stock */
    @GetMapping("/stock")
    public ResponseEntity<Map<String, Integer>> getStock() {
        return ResponseEntity.ok(stock);
    }
}
