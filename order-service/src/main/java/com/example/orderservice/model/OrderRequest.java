package com.example.orderservice.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderRequest {
    private String productId;
    private int quantity;
    private String customerId;

    // Auto-generate an orderId for tracking
    @Builder.Default
    private String orderId = UUID.randomUUID().toString();
}
