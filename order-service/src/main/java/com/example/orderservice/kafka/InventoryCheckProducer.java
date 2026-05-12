package com.example.orderservice.kafka;

import com.example.orderservice.model.OrderRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryCheckProducer {

    private static final String TOPIC = "inventory-check-queue";

    private final KafkaTemplate<String, OrderRequest> kafkaTemplate;

    /**
     * Publishes an order to Kafka when Inventory Service is unavailable.
     * A downstream consumer will retry the inventory check and confirm the order.
     *
     * NOTE: In production, replace this with the Outbox Pattern:
     *   1. Write to a local DB outbox table (same transaction as order save)
     *   2. A separate poller reads outbox and publishes to Kafka
     *   This prevents message loss if Kafka itself is down during fallback.
     */
    public void queueForInventoryCheck(OrderRequest orderRequest) {
        log.warn("Inventory Service unavailable. Queuing order {} to Kafka topic: {}",
                orderRequest.getOrderId(), TOPIC);
        kafkaTemplate.send(TOPIC, orderRequest.getOrderId(), orderRequest)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to queue order {} to Kafka: {}",
                                orderRequest.getOrderId(), ex.getMessage());
                    } else {
                        log.info("Order {} queued successfully to topic {} partition {}",
                                orderRequest.getOrderId(),
                                result.getRecordMetadata().topic(),
                                result.getRecordMetadata().partition());
                    }
                });
    }
}
