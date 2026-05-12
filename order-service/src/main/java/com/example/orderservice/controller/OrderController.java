package com.example.orderservice.controller;

import com.example.orderservice.model.OrderRequest;
import com.example.orderservice.model.OrderResponse;
import com.example.orderservice.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /**
     * POST /api/orders
     *
     * Example request body:
     * {
     *   "productId": "PROD-001",
     *   "quantity": 2,
     *   "customerId": "CUST-123"
     * }
     */
    @PostMapping
    public ResponseEntity<OrderResponse> placeOrder(@RequestBody OrderRequest orderRequest) {
        log.info("Received order request for productId={}", orderRequest.getProductId());
        OrderResponse response = orderService.placeOrder(orderRequest);

        // Return 202 Accepted for provisional orders, 200 OK for confirmed
        HttpStatus status = "PENDING_INVENTORY".equals(response.getStatus())
                ? HttpStatus.ACCEPTED
                : HttpStatus.OK;

        return ResponseEntity.status(status).body(response);
    }

    /**
     * GET /api/orders/health-check — simple endpoint to verify service is up
     */
    @GetMapping("/health-check")
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("Order Service is running");
    }
}
