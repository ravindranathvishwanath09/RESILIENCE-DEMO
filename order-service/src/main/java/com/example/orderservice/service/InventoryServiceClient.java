package com.example.orderservice.service;

import com.example.orderservice.kafka.InventoryCheckProducer;
import com.example.orderservice.model.InventoryResponse;
import com.example.orderservice.model.OrderRequest;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryServiceClient {

    private final WebClient inventoryWebClient;
    private final InventoryCheckProducer inventoryCheckProducer;

    /**
     * Checks inventory availability for a given order.
     *
     * Resilience layers applied (inner → outer):
     *   TimeLimiter  → enforces 250ms hard timeout
     *   Retry        → retries on ConnectException only (not on timeout)
     *   CircuitBreaker → opens after 50% failure rate in last 10 calls
     *   Bulkhead     → limits concurrent calls to 10 (isolated thread pool)
     *
     * Annotation order matters: Bulkhead > CircuitBreaker > Retry > TimeLimiter
     * Spring AOP applies them outer-first based on @Order in Resilience4j auto-config.
     */
    @Bulkhead(name = "inventoryService", type = Bulkhead.Type.SEMAPHORE, fallbackMethod = "inventoryFallback")
    @CircuitBreaker(name = "inventoryService", fallbackMethod = "inventoryFallback")
    @Retry(name = "inventoryService", fallbackMethod = "inventoryFallback")
    @TimeLimiter(name = "inventoryService", fallbackMethod = "inventoryFallback")
    public CompletableFuture<InventoryResponse> checkInventory(OrderRequest orderRequest) {
        log.info("Calling Inventory Service for productId={} qty={}",
                orderRequest.getProductId(), orderRequest.getQuantity());

        return inventoryWebClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/inventory/{productId}")
                        .queryParam("quantity", orderRequest.getQuantity())
                        .build(orderRequest.getProductId()))
                .retrieve()
                .bodyToMono(InventoryResponse.class)
                .toFuture();
    }

    /**
     * Fallback — called when any resilience layer triggers.
     * Queues the order for async inventory check via Kafka.
     * Returns null so the OrderService knows to return a provisional response.
     *
     * The fallback method signature MUST match the main method + Throwable parameter.
     */
    public CompletableFuture<InventoryResponse> inventoryFallback(OrderRequest orderRequest, Throwable t) {
        log.warn("Inventory Service fallback triggered for orderId={}. Reason: {}",
                orderRequest.getOrderId(), t.getClass().getSimpleName() + ": " + t.getMessage());

        // Queue to Kafka for async retry by a downstream consumer
        inventoryCheckProducer.queueForInventoryCheck(orderRequest);

        // Return null to signal provisional response to caller
        return CompletableFuture.completedFuture(null);
    }
}
