# Spring Boot Microservice Resilience — Approach 2 Demo

## Project Structure

```
order-service/      → Port 8080  (has all Resilience4j layers)
inventory-service/  → Port 8081  (has simulation toggles for testing)
```

## Prerequisites

- Java 17+
- Maven 3.8+
- Kafka (optional — comment out spring.kafka config if not running)

---

## Quick Start (without Kafka)

In `order-service/src/main/resources/application.yml`, comment out the Kafka block:
```yaml
# spring:
#   kafka:
#     bootstrap-servers: localhost:9092
```

And in `InventoryCheckProducer.java`, the Kafka send will simply log a warning if Kafka is absent.

### Terminal 1 — Start Inventory Service
```bash
cd inventory-service
mvn spring-boot:run
```

### Terminal 2 — Start Order Service
```bash
cd order-service
mvn spring-boot:run
```

---

## Test Scenarios

### Scenario 1 — Happy Path (circuit CLOSED, inventory available)
```bash
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{"productId": "PROD-001", "quantity": 2, "customerId": "CUST-123"}'
```
**Expected:** `200 OK` with status `CONFIRMED`

---

### Scenario 2 — Insufficient Stock (hard fail, no fallback)
```bash
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{"productId": "PROD-002", "quantity": 1, "customerId": "CUST-123"}'
```
**Expected:** `200 OK` with status `FAILED` (PROD-002 has 0 stock)

---

### Scenario 3 — TimeLimiter triggers (Inventory responds slowly)

Enable slow mode on Inventory Service:
```bash
curl -X POST "http://localhost:8081/api/inventory/simulate/slow?enabled=true"
```

Place an order:
```bash
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{"productId": "PROD-001", "quantity": 1, "customerId": "CUST-123"}'
```
**Expected:** `202 Accepted` with status `PENDING_INVENTORY`
**Order-Service logs:** `TimeLimiter` fires after 250ms, fallback triggered

Reset slow mode:
```bash
curl -X POST "http://localhost:8081/api/inventory/simulate/slow?enabled=false"
```

---

### Scenario 4 — CircuitBreaker OPENS (Inventory Service failing)

Enable failure mode:
```bash
curl -X POST "http://localhost:8081/api/inventory/simulate/failure?enabled=true"
```

Send 6+ requests to cross the 50% failure threshold (5 of last 10 calls):
```bash
for i in {1..7}; do
  curl -s -X POST http://localhost:8080/api/orders \
    -H "Content-Type: application/json" \
    -d "{\"productId\": \"PROD-001\", \"quantity\": 1, \"customerId\": \"CUST-$i\"}" | jq .status
done
```
**Expected:** First few → `PENDING_INVENTORY` (retry + fallback). After threshold → circuit OPENS, subsequent calls fail immediately (no waiting 250ms) → still `PENDING_INVENTORY` but much faster.

Check circuit breaker state via Actuator:
```bash
curl http://localhost:8080/actuator/circuitbreakers | jq .
```

---

### Scenario 5 — Circuit HALF_OPEN → CLOSED (recovery)

After 30 seconds (wait-duration-in-open-state), the circuit enters HALF_OPEN.

Disable failure mode:
```bash
curl -X POST "http://localhost:8081/api/inventory/simulate/failure?enabled=false"
```

Send 3 probe requests (permitted-number-of-calls-in-half-open-state = 3):
```bash
for i in {1..3}; do
  curl -s -X POST http://localhost:8080/api/orders \
    -H "Content-Type: application/json" \
    -d '{"productId": "PROD-001", "quantity": 1, "customerId": "CUST-recovery"}' | jq .status
done
```
**Expected:** All 3 succeed → circuit transitions back to CLOSED → `CONFIRMED`

---

### Scenario 6 — Bulkhead (concurrent call limit)

Send 15 concurrent requests (bulkhead allows max 10):
```bash
for i in {1..15}; do
  curl -s -X POST http://localhost:8080/api/orders \
    -H "Content-Type: application/json" \
    -d "{\"productId\": \"PROD-001\", \"quantity\": 1, \"customerId\": \"CUST-$i\"}" &
done
wait
```
**Expected:** 10 calls proceed normally, 5 hit the bulkhead → fallback triggered → `PENDING_INVENTORY`

---

## Actuator Endpoints

| Endpoint | What it shows |
|---|---|
| `GET /actuator/health` | Circuit breaker health state (CLOSED/OPEN/HALF_OPEN) |
| `GET /actuator/circuitbreakers` | Detailed CB stats (failure rate, call counts) |
| `GET /actuator/metrics/resilience4j.circuitbreaker.state` | Prometheus-compatible metric |
| `GET /actuator/prometheus` | All metrics for Prometheus scraping |

---

## Key Classes

| Class | Purpose |
|---|---|
| `InventoryServiceClient` | All 5 Resilience4j layers (`@TimeLimiter`, `@Retry`, `@CircuitBreaker`, `@Bulkhead`) |
| `OrderService` | Business logic — interprets inventory response vs provisional |
| `InventoryCheckProducer` | Kafka fallback queue |
| `WebClientConfig` | WebClient with HTTP-level timeout |
| `application.yml` | All Resilience4j thresholds configured here (no code change needed) |
