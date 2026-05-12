package com.example.orderservice.service;

import com.example.orderservice.model.InventoryResponse;
import com.example.orderservice.model.OrderRequest;
import com.example.orderservice.model.OrderResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final InventoryServiceClient inventoryServiceClient;

    /**
     * Places an order by checking inventory first.
     *
     * Flow:
     *  1. Call Inventory Service (with full resilience stack)
     *  2a. If inventory confirmed → return CONFIRMED response
     *  2b. If fallback triggered (null response) → return PENDING_INVENTORY response
     *      (order already queued to Kafka for async processing)
     *  2c. If inventory unavailable for requested qty → return FAILED response
     */
    public OrderResponse placeOrder(OrderRequest orderRequest) {
        log.info("Placing order: orderId={} productId={} qty={}",
                orderRequest.getOrderId(), orderRequest.getProductId(), orderRequest.getQuantity());

        try {
            CompletableFuture<InventoryResponse> future =
                    inventoryServiceClient.checkInventory(orderRequest);

            InventoryResponse inventoryResponse = future.get(); // blocks until done or fallback

            // Fallback was triggered — inventory queued async
            if (inventoryResponse == null) {
                log.warn("Provisional order created for orderId={}", orderRequest.getOrderId());
                return OrderResponse.provisional(orderRequest.getOrderId());
            }

            // Inventory available
            if (inventoryResponse.isAvailable() &&
                    inventoryResponse.getAvailableQuantity() >= orderRequest.getQuantity()) {
                log.info("Inventory confirmed for orderId={}", orderRequest.getOrderId());
                return OrderResponse.confirmed(orderRequest.getOrderId());
            }

            // Insufficient stock — hard fail, no fallback needed
            log.warn("Insufficient inventory for orderId={}: requested={} available={}",
                    orderRequest.getOrderId(), orderRequest.getQuantity(),
                    inventoryResponse.getAvailableQuantity());
            return OrderResponse.failed(orderRequest.getOrderId(),
                    "Insufficient stock. Available: " + inventoryResponse.getAvailableQuantity());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return OrderResponse.failed(orderRequest.getOrderId(), "Request interrupted");
        } catch (ExecutionException e) {
            log.error("Unexpected error placing order {}: {}", orderRequest.getOrderId(), e.getMessage());
            return OrderResponse.failed(orderRequest.getOrderId(), "Unexpected error: " + e.getCause().getMessage());
        }
    }
}
