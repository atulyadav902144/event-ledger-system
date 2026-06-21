# Event Ledger System

A distributed microservices architecture for processing financial transaction events with idempotency, out-of-order tolerance, observability, and resiliency.

## Architecture

```
┌─────────────────────┐
│  Browser / Client   │
└──────────┬──────────┘
           │ REST
           ▼
┌─────────────────────────────────────┐
│   Event Gateway API (Port 8080)     │
│   - Public-facing entry point       │
│   - Idempotency enforcement         │
│   - Event storage (H2 in-memory)    │
│   - Circuit breaker to Account Svc  │
└──────────┬──────────────────────────┘
           │ REST (with X-Trace-Id)
           ▼
┌─────────────────────────────────────┐
│  Account Service (Port 8081)        │
│  - Internal service                 │
│  - Balance management               │
│  - Transaction processing (H2)      │
└─────────────────────────────────────┘
```

### Event Gateway
- **Entry point** for all client requests
- **Validates** transaction events (amount > 0, type ∈ {CREDIT, DEBIT})
- **Enforces idempotency** via unique eventId constraint (returns 200 OK + IGNORED for duplicates)
- **Stores events** in local H2 database
- **Calls Account Service** to apply transactions (protected by circuit breaker)
- **Provides event listing** in chronological order (by eventTimestamp)
- **Returns trace IDs** in responses for observability

### Account Service
- **Internal service** (not exposed to external clients)
- **Manages account state** and balances
- **Processes transactions** (applies CREDIT/DEBIT to balances)
- **Computes balances** as: sum(CREDITS) - sum(DEBITS)
- **Runs independently** with its own H2 in-memory database

## Features

### ✅ Core Functionality
- **Idempotency**: Same `eventId` submitted multiple times → stored once, returns IGNORED on duplicates
- **Out-of-order tolerance**: Events may arrive in any order; listing always sorted by eventTimestamp
- **Balance computation**: Net balance = sum(CREDITs) - sum(DEBITs)
- **Validation**: Rejects invalid amounts (≤0), unknown types, missing fields with meaningful errors

### ✅ Distributed Tracing
- **Trace ID generation**: UUID generated per request at the Gateway
- **Propagation**: Sent via `X-Trace-Id` HTTP header to Account Service
- **Logging**: Both services include traceId in all logs (via SLF4J MDC)
- **Traceable path**: Single client request can be traced across both services

### ✅ Observability
- **Structured logging**: All logs include timestamp, service name, trace ID, log level
- **Health checks**: `GET /actuator/health` on both services (8080 and 8081)
- **Metrics**: `GET /actuator/metrics` exposes:
  - `events_processed_total` (counter)
  - `events_validation_failed_total` (counter by reason)
  - `events_processed_duplicate_total` (counter)

### ✅ Resiliency
- **Circuit Breaker** (Resilience4j): Protects calls from Gateway to Account Service
  - Sliding window: 5 requests
  - Failure threshold: 50%
  - Wait duration in open state: 10 seconds
- **Graceful degradation**:
  - `POST /events`: Returns 503 when Account Service unavailable (instead of hanging)
  - `GET /events/{id}`, `GET /events?account=...`: Still work (read from Gateway's local DB)

## Prerequisites

- **Docker & Docker Compose** (recommended), or
- **Java 8+** and **Maven 3.6+** (for manual setup)

## Quick Start

### Option 1: Docker Compose (Recommended)

```bash
# Navigate to repository root
cd event-ledger-system

# Build and start both services
docker-compose up --build

# Services will be available at:
#   Event Gateway (public):    http://localhost:8080
#   Account Service (internal - not public): runs on port 8081 for inter-service communication; not intended to be exposed to external clients
```

To stop services:
```bash
docker-compose down
```

### Option 2: Manual Setup (Local Development)

**Terminal 1 - Account Service (internal):**
```bash
cd account-service
mvn clean spring-boot:run
# Runs on port 8081 (internal service — not public-facing)
```

**Terminal 2 - Event Gateway (public):**
```bash
cd event-gateway
mvn clean spring-boot:run
# Runs on http://localhost:8080 (public-facing entry point)
```

## API Examples

### Submit a Transaction Event

```bash
curl -X POST http://localhost:8080/events \
  -H "Content-Type: application/json" \
  -d '{
    "eventId": "evt-001",
    "accountId": "acct-123",
    "type": "CREDIT",
    "amount": 150.00,
    "currency": "USD",
    "eventTimestamp": "2026-05-15T14:02:11Z",
    "metadata": {"source":"mainframe-batch"}
  }'
```

**Response (201 Created):**
```json
{
  "status": "ACCEPTED",
  "eventId": "evt-001",
  "traceId": "a1b2c3d4-e5f6-47g8-h9i0-j1k2l3m4n5o6"
}
```

### List Events for an Account

```bash
curl http://localhost:8080/events?account=acct-123
```

### Get Account Balance (internal)

```bash
# This endpoint is served by the Account Service which is internal and not intended to be exposed publicly.
curl http://localhost:8081/accounts/acct-123/balance
```

### Health Checks

```bash
curl http://localhost:8080/actuator/health
curl http://localhost:8081/actuator/health
```

## Running Tests

```bash
# Account Service tests
cd account-service
mvn test

# Event Gateway tests
cd ../event-gateway
mvn test
```

**Test Coverage:**
- ✅ Idempotency (duplicate submission)
- ✅ Validation (invalid amounts, types)
- ✅ Event listing in chronological order
- ✅ Trace ID generation and propagation
- ✅ Total: 12 passing tests

## Resiliency Strategy: Circuit Breaker

**Why Circuit Breaker?**
- Detects repeated failures from Account Service early
- Prevents cascading failures by failing fast (503 instead of timeout)
- Automatic recovery after wait period
- Battle-tested pattern

**Configuration:**
- Sliding window: 5 requests
- Failure threshold: 50%
- Wait duration: 10 seconds

**Behavior:**
1. Normal: Gateway calls Account Service
2. Failures increase: 50%+ failures detected
3. Circuit OPENS: Gateway returns 503 immediately
4. After 10s: Circuit tries one call (HALF-OPEN)
5. Success: Circuit CLOSES, normal operation resumes
6. Failure: Circuit OPENS again

**Graceful Degradation:**
- Write operations (POST /events): Return 503 (client can retry)
- Read operations (GET /events): Still work (read from local DB)
- Events preserved: Data not lost, can be retried later

## Project Structure

```
event-ledger-system/
├── docker-compose.yml
├── README.md
├── account-service/
│   ├── Dockerfile
│   ├── pom.xml
│   ├── src/main/java/com/ledger/account/
│   │   ├── controller/AccountController.java
│   │   ├── service/AccountManagerService.java
│   │   └── entity/
│   └── src/test/java/
└── event-gateway/
    ├── Dockerfile
    ├── pom.xml
    ├── src/main/java/com/ledger/gateway/
    │   ├── controller/EventController.java
    │   ├── client/AccountServiceClient.java
    │   ├── exception/GlobalExceptionHandler.java
    │   └── entity/
    └── src/test/java/
```

## Troubleshooting

**Services won't connect:**
```bash
# Check if ports are available
netstat -ano | findstr :8080
netstat -ano | findstr :8081

# Check Docker network
docker network ls
docker network inspect ledger-network
```

**Tests fail:**
```bash
# Clean and rebuild
cd account-service && mvn clean test
cd ../event-gateway && mvn clean test
```

**Account Service unreachable:**
```bash
# Restart the service
docker-compose restart account-service

# Or check logs
docker-compose logs account-service
```

