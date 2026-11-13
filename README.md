# Idempotent Background Transaction Processing Service

## 1. Project Overview

The Idempotent Background Transaction Processing Service is a Spring Boot application designed to process financial transactions safely and reliably.

In real-world applications, transaction requests may arrive multiple times, arrive out of order, or fail during processing. This application handles these scenarios while maintaining transaction consistency and preventing duplicate balance updates.

The application supports:

- Idempotent transaction processing.
- Duplicate transaction detection.
- Background transaction processing.
- Sequence-based transaction ordering.
- Concurrent transaction protection.
- CREDIT, DEBIT and REVERSAL operations.
- Retry handling for temporary failures.
- Transaction status tracking.
- Processing summary and operational logging.

## 2. Technology Stack

| Technology | Purpose |
|---|---|
| Java 21 | Programming language |
| Spring Boot 4.1.2 | Application framework |
| Spring Web MVC | REST API development |
| Spring Data JPA | Database operations |
| Hibernate | ORM framework |
| H2 Database | Local database persistence |
| Maven | Build and dependency management |
| JUnit 5 | Unit and integration testing |
| Mockito | Mocking dependencies |
| MockMvc | REST API integration testing |
| Swagger / OpenAPI | API documentation |
| SLF4J / Logback | Application logging |
| IntelliJ IDEA | Development environment |

## 3. System Architecture

The application follows a layered architecture.

```text
                  Client / Postman
                         |
                         v
                  REST Controllers
                         |
                         v
                       Services
                         |
             +-----------+-----------+
             |                       |
             v                       v
      Transaction Service       Account Service
             |
             v
     Transaction Processor
             |
             v
    Transaction Execution Service
             |
             v
         Repositories
             |
             v
          H2 Database


       Background Scheduler
          (Every 5 Seconds)
                 |
                 v
       Transaction Processor
```

### Main Components

**Controller Layer**
- Receives HTTP requests.
- Validates and forwards requests to services.
- Returns appropriate API responses.

**Service Layer**
- Implements business logic.
- Handles transaction submission and account operations.
- Coordinates transaction execution.

**Transaction Processor**
- Fetches eligible transactions.
- Attempts to claim each transaction atomically.
- Executes only successfully claimed transactions.

**Transaction Execution Service**
- Processes CREDIT, DEBIT and REVERSAL transactions.
- Validates transaction sequence.
- Updates account balance and transaction status.
- Handles business failures.

**Repository Layer**
- Performs database operations using Spring Data JPA.
- Provides atomic transaction claiming and account locking operations.

**Background Scheduler**
- Runs periodically every five seconds.
- Triggers transaction processing automatically.

## 4. Project Features

### 4.1 Idempotency

Idempotency ensures that processing the same request multiple times does not create duplicate business effects.

Implementation:

- Uses transaction ID, request ID and idempotency key.
- Detects repeated requests.
- Returns the existing transaction result for an idempotent replay.
- Prevents duplicate balance updates.

### 4.2 Duplicate Transaction Detection

The application checks whether a transaction has already been submitted or processed.

If a duplicate transaction is detected, it is handled using the DUPLICATE status.

This prevents the same business transaction from being applied more than once.

### 4.3 Atomic Transaction Claiming

The application uses an atomic database update to claim transactions before execution.

Eligible transaction statuses:

- RECEIVED
- PENDING
- RETRY_PENDING

The processor changes the status to PROCESSING using a conditional database update.

Only the processor instance that successfully claims the transaction proceeds with execution.

This reduces the possibility of multiple processor instances executing the same transaction simultaneously.

### 4.4 Concurrent Processing Protection

The application uses pessimistic database locking on account records.

When processing a transaction, the application locks the corresponding account record.

This prevents concurrent transactions from updating the same account balance simultaneously.

The application also uses optimistic versioning through JPA's `@Version` field.

### 4.5 Sequence-Based Transaction Ordering

Each transaction contains a sequence number.

Each account maintains its last successfully or permanently processed sequence number.

Expected sequence:

```text
expectedSequence = lastProcessedSequence + 1
```

Processing rules:

| Condition | Action |
|---|---|
| Transaction sequence is greater than expected | Mark PENDING |
| Transaction sequence matches expected | Process transaction |
| Transaction sequence is less than expected | Mark FAILED as stale |

When an earlier transaction is processed, pending transactions can be picked up during a subsequent processing cycle.

### 4.6 CREDIT Transaction

A CREDIT transaction adds money to the account balance.

Example:

```text
Initial Balance = 100
Credit Amount   = 50
Final Balance   = 150
```

### 4.7 DEBIT Transaction

A DEBIT transaction deducts money from the account balance.

Before deduction, the application checks whether sufficient balance is available.

Example:

```text
Initial Balance = 100
Debit Amount    = 30
Final Balance   = 70
```

If sufficient balance is unavailable, the transaction is marked FAILED without deducting money.

### 4.8 REVERSAL Transaction

A REVERSAL transaction reverses a previously processed CREDIT or DEBIT transaction.

Validation includes:

- Original transaction must exist.
- Original transaction must be PROCESSED.
- Original transaction cannot itself be a reversal.
- Original transaction must belong to the same account.
- Currency must match.
- Reversal amount must match the original amount.
- The original transaction must not already have been reversed.

A database unique constraint on `original_transaction_id` provides additional protection against multiple reversals of the same original transaction.

### 4.9 Retry Handling

The application supports retry handling for temporary processing failures.

Implementation:

- Tracks retry count.
- Simulates a temporary failure for transaction `TXN-2005`.
- Changes the transaction status to RETRY_PENDING after a failed attempt.
- Allows a maximum of three attempts.
- Marks the transaction FAILED after retry exhaustion.

Retry processing does not intentionally apply the transaction balance update before a successful execution.

### 4.10 Transaction Status Tracking

The application maintains transaction statuses throughout the processing lifecycle.

| Status | Description |
|---|---|
| RECEIVED | Transaction request received |
| PROCESSING | Transaction claimed for execution |
| PENDING | Waiting for an earlier sequence |
| PROCESSED | Transaction completed successfully |
| DUPLICATE | Duplicate request detected |
| RETRY_PENDING | Waiting for another processing attempt |
| FAILED | Transaction permanently failed |

### 4.11 Processing Summary

The application provides a summary API that returns transaction counts by status.

Supported counts:

- Received
- Processing
- Pending
- Processed
- Duplicate
- Retry pending
- Failed
- Total

### 4.12 Logging and Correlation ID

The application uses SLF4J logging for important processing events.

A correlation ID filter supports the `X-Correlation-ID` request header.

The correlation ID is stored in MDC and included in application log output.

This helps trace related operations across application logs.

## 5. Transaction Processing Flow

```text
       Client Submits Transaction
                  |
                  v
          Validate Request
                  |
                  v
          Check Idempotency
                  |
             +----+----+
             |         |
          Duplicate   New Request
             |         |
             v         v
         DUPLICATE   RECEIVED
                       |
                       v
               Background Scheduler
                       |
                       v
               Transaction Processor
                       |
                       v
                Atomic Claim
                       |
                       v
                  PROCESSING
                       |
                       v
               Lock Account Record
                       |
                       v
               Validate Sequence
                       |
              +--------+--------+
              |        |        |
              v        v        v
           Correct   Future   Stale
              |        |        |
              v        v        v
          Execute   PENDING   FAILED
              |
              v
       CREDIT / DEBIT / REVERSAL
              |
              v
        Update Account Balance
              |
              v
           PROCESSED
```

Temporary processing failures are handled separately through the retry mechanism.

## 6. Project Structure

```text
transaction-processing
|
+-- src
|   |
|   +-- main
|   |   |
|   |   +-- java
|   |   |   |
|   |   |   +-- com.mphasis.transaction
|   |   |       |
|   |   |       +-- controller
|   |   |       |   +-- AccountController.java
|   |   |       |   +-- TransactionController.java
|   |   |       |
|   |   |       +-- service
|   |   |       |   +-- impl
|   |   |       |   +-- TransactionExecutionService.java
|   |   |       |   +-- TransactionProcessor.java
|   |   |       |   +-- TransactionScheduler.java
|   |   |       |
|   |   |       +-- repository
|   |   |       |
|   |   |       +-- entity
|   |   |
|   |   |       +-- dto
|   |   |       |   +-- request
|   |   |       |   +-- response
|   |   |       |
|   |   |       +-- enums
|   |   |       |
|   |   |       +-- exception
|   |   |       |
|   |   |       +-- config
|   |   |
|   |   +-- resources
|   |       +-- application.yml
|   |
|   +-- test
|       |
|       +-- java
|           |
|           +-- com.mphasis.transaction
|               +-- TransactionExecutionServiceTest.java
|               +-- TransactionServiceImplTest.java
|               +-- TransactionIntegrationTest.java
|
+-- data
+-- README.md
+-- DESIGN.md
+-- pom.xml
+-- mvnw
+-- mvnw.cmd
```

## 7. Prerequisites

Before running the application, install:

- Java 21
- IntelliJ IDEA or another Java IDE
- Maven (optional because the project includes Maven Wrapper)
- Postman (for API testing)

## 8. Application Setup

### Step 1: Clone the Repository

```bash
git clone <your-github-repository-url>
```

### Step 2: Navigate to the Project

```bash
cd transaction-processing
```

### Step 3: Build the Application

Windows:

```powershell
.\mvnw.cmd clean install
```

Linux / macOS:

```bash
./mvnw clean install
```

### Step 4: Run the Application

Windows:

```powershell
.\mvnw.cmd spring-boot:run
```

Linux / macOS:

```bash
./mvnw spring-boot:run
```

Application URL:

```text
http://localhost:8080
```

## 9. Database Configuration

The application uses an H2 file-based database for local persistence.

Database configuration:

```yaml
spring:
  datasource:
    url: jdbc:h2:file:./data/transactiondb;DB_CLOSE_ON_EXIT=FALSE
    driver-class-name: org.h2.Driver
    username: sa
    password: ""
```

### H2 Console

Open:

```text
http://localhost:8080/h2-console
```

Connection details:

```text
JDBC URL:
jdbc:h2:file:./data/transactiondb;DB_CLOSE_ON_EXIT=FALSE

Username: sa
Password: (empty)
```

The database file is stored locally in the project's `data` directory.

## 10. REST API Documentation

### 10.1 Account APIs

| HTTP Method | Endpoint | Description |
|---|---|---|
| POST | `/api/v1/accounts?accountId=ACC-1001` | Create an account |
| GET | `/api/v1/accounts/{accountId}` | Get account details |

### 10.2 Transaction APIs

| HTTP Method | Endpoint | Description |
|---|---|---|
| POST | `/api/v1/transactions` | Submit a transaction |
| POST | `/api/v1/transactions/batch` | Submit multiple transactions |
| GET | `/api/v1/transactions/{transactionId}` | Get transaction details |
| POST | `/api/v1/transactions/process` | Manually trigger processing |
| GET | `/api/v1/transactions/summary` | Get transaction summary |

### 10.3 Swagger UI

Swagger UI provides an interactive interface for exploring and testing REST APIs.

URL:

```text
http://localhost:8080/swagger-ui/index.html
```

## 11. Sample Transaction Request

Submit a CREDIT transaction using:

**POST**

```text
http://localhost:8080/api/v1/transactions
```

Request body:

```json
{
  "transactionId": "TXN-1001",
  "requestId": "REQ-1001",
  "idempotencyKey": "IDEMP-1001",
  "accountId": "ACC-1001",
  "transactionType": "CREDIT",
  "amount": 100,
  "currency": "INR",
  "sequenceNumber": 1,
  "eventTime": "2026-11-13T10:00:00"
}
```

Example processing result:

```json
{
  "transactionId": "TXN-1001",
  "accountId": "ACC-1001",
  "status": "PROCESSED",
  "retryCount": 0,
  "message": "Transaction processed successfully"
}
```

The exact response fields depend on the response DTO.

## 12. Testing

The project includes unit tests and REST API integration tests.

### Run All Tests

Windows:

```powershell
.\mvnw.cmd clean test
```

Linux / macOS:

```bash
./mvnw clean test
```

### Run Individual Test Classes

Transaction execution tests:

```powershell
.\mvnw.cmd test -Dtest=TransactionExecutionServiceTest
```

Transaction service tests:

```powershell
.\mvnw.cmd test -Dtest=TransactionServiceImplTest
```

Integration tests:

```powershell
.\mvnw.cmd test -Dtest=TransactionIntegrationTest
```

### Test Coverage

The implemented tests cover:

1. Successful CREDIT transaction.
2. Successful DEBIT transaction.
3. Insufficient account balance.
4. Out-of-order transaction handling.
5. Temporary processing failure.
6. Retry handling.
7. Maximum retry exhaustion.
8. Successful CREDIT reversal.
9. Successful DEBIT reversal.
10. Invalid reversal handling.
11. Duplicate reversal prevention.
12. Transaction submission and API validation.
13. Account creation and retrieval.
14. Transaction summary API.
15. REST API processing flow.

**Latest recorded test evidence:**

- 25 existing tests passed.
- 6 integration tests passed in a separate execution.
- Both test runs completed with BUILD SUCCESS.

Run the complete Maven test suite to verify all tests together before submission.

## 13. Production Hardening and Future Improvements

The current application is designed as a lightweight assessment implementation.

The following improvements should be considered before production deployment:

### Reliability
- Add recovery for transactions left in PROCESSING after an application crash.
- Introduce a claim lease or timeout mechanism.
- Implement retry backoff and configurable retry policies.
- Define how later transactions proceed after retry exhaustion.

### Scalability
- Integrate Kafka or RabbitMQ for asynchronous messaging.
- Support multiple processing instances.
- Add configurable batch sizes and worker pools.

### Database
- Use PostgreSQL or another production-grade database.
- Add database indexes based on query patterns.
- Review transaction isolation and locking behavior under concurrent load.

### Security
- Implement authentication and authorization.
- Secure API endpoints.
- Use external secret management.

### Observability
- Add Micrometer metrics.
- Integrate Prometheus and Grafana.
- Add distributed tracing with OpenTelemetry.
- Monitor retry counts, processing latency and failed transactions.

## 14. AI-Assisted Development

AI assistance was used during development to:

- Understand the assessment requirements.
- Explore architecture and implementation approaches.
- Generate and review Java code.
- Improve exception handling and transaction processing.
- Create and refine unit and integration tests.
- Review edge cases and production-hardening considerations.

Generated code was reviewed and validated through Maven tests and REST API testing.

## 15. Conclusion

The Idempotent Background Transaction Processing Service demonstrates a practical approach to reliable transaction processing.

It implements idempotency, duplicate detection, atomic claiming, sequence-based ordering, account-level concurrency protection, transaction reversals, retry handling and operational visibility.

The project provides a foundation for extending the solution into a more scalable, distributed and production-ready transaction processing system.