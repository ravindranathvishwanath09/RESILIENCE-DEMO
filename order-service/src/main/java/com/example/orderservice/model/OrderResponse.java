package com.example.orderservice.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {
    private String orderId;
    private String status;        // CONFIRMED, PENDING_INVENTORY, FAILED
    private String message;
    private boolean inventoryConfirmed;
    private Instant timestamp;

    public static OrderResponse confirmed(String orderId) {
        return OrderResponse.builder()
                .orderId(orderId)
                .status("CONFIRMED")
                .message("Order confirmed. Inventory reserved successfully.")
                .inventoryConfirmed(true)
                .timestamp(Instant.now())
                .build();
    }

    // Fallback: inventory check queued async
    public static OrderResponse provisional(String orderId) {
        return OrderResponse.builder()
                .orderId(orderId)
                .status("PENDING_INVENTORY")
                .message("Order received. Inventory service is temporarily unavailable. " +
                         "We are confirming your order — you will be notified shortly.")
                .inventoryConfirmed(false)
                .timestamp(Instant.now())
                .build();
    }

    public static OrderResponse failed(String orderId, String reason) {
        return OrderResponse.builder()
                .orderId(orderId)
                .status("FAILED")
                .message("Order could not be placed: " + reason)
                .inventoryConfirmed(false)
                .timestamp(Instant.now())
                .build();
    }
}
