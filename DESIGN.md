# Technical Design Document
# Idempotent Background Transaction Processing Service

## 1. Introduction

### 1.1 Purpose

The purpose of this document is to explain the technical design and implementation of the Idempotent Background Transaction Processing Service.

The application is designed to process financial transactions safely when requests arrive multiple times, arrive out of order, or experience temporary processing failures.

The solution focuses on:

- Idempotent transaction processing.
- Duplicate request detection.
- Sequence-based transaction ordering.
- Concurrent transaction protection.
- Retry and failure handling.
- Transaction state management.
- Operational visibility.

### 1.2 Technology Stack

| Technology | Purpose |
|---|---|
| Java 21 | Programming language |
| Spring Boot 4.1.2 | Application framework |
| Spring Web MVC | REST API layer |
| Spring Data JPA | Database access |
| Hibernate | ORM |
| H2 Database | Local persistence |
| Maven | Build management |
| JUnit 5 | Unit and integration testing |
| Mockito | Mock-based testing |
| MockMvc | REST API integration testing |
| Swagger / OpenAPI | API documentation |
| SLF4J / Logback | Application logging |

---

## 2. System Architecture

The application follows a layered architecture to separate API handling, business logic, transaction execution and database operations.

### 2.1 High-Level Architecture

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

### 2.2 Component Responsibilities

**Controller Layer**

- Exposes REST APIs.
- Receives transaction and account requests.
- Handles request validation.
- Returns API responses.

**Transaction Service**

- Handles transaction submission.
- Performs idempotency and duplicate checks.
- Creates transaction records.
- Maintains request tracking information.

**Account Service**

- Creates accounts.
- Retrieves account information.
- Maintains account balance and sequence information.

**Transaction Processor**

- Fetches eligible transactions.
- Attempts to claim transactions atomically.
- Invokes transaction execution for successfully claimed records.
- Handles processing exceptions.

**Transaction Execution Service**

- Executes CREDIT, DEBIT and REVERSAL operations.
- Validates transaction sequence.
- Locks account records.
- Updates account balance and transaction status.
- Handles business failures and retry state changes.

**Repository Layer**

- Performs database operations.
- Supports conditional transaction claiming.
- Provides account locking operations.
- Retrieves transaction and request records.

**Background Scheduler**

- Executes periodically every five seconds.
- Invokes the transaction processor automatically.

---

## 3. Transaction Data Model

The application maintains three main database entities.

### 3.1 Account Entity

The Account entity represents a financial account.

| Field | Description |
|---|---|
| accountId | Unique account identifier |
| balance | Current account balance |
| lastProcessedSequence | Last consumed transaction sequence |
| version | Optimistic locking version |
| createdAt | Account creation timestamp |
| updatedAt | Last update timestamp |

The account entity is used to maintain balance consistency and sequence-based transaction ordering.

### 3.2 Transaction Entity

The Transaction entity stores transaction information and processing state.

| Field | Description |
|---|---|
| transactionId | Unique transaction identifier |
| accountId | Associated account |
| transactionType | CREDIT, DEBIT or REVERSAL |
| originalTransactionId | Original transaction reference for reversal |
| amount | Transaction amount |
| currency | Currency code |
| sequenceNumber | Transaction sequence |
| status | Current transaction status |
| retryCount | Number of processing attempts |
| lastError | Last processing error |
| eventTime | Original transaction event time |
| createdAt | Creation timestamp |
| updatedAt | Last update timestamp |
| version | Optimistic locking version |

Database constraints protect unique transaction identifiers and prevent multiple reversals of the same original transaction.

### 3.3 Transaction Request Entity

The TransactionRequest entity tracks incoming transaction requests.

| Field | Description |
|---|---|
| requestId | Unique request identifier |
| transactionId | Associated transaction |
| idempotencyKey | Key used for request replay detection |
| status | Current request status |
| responseMessage | Processing response message |
| receivedAt | Request received timestamp |

This entity supports request tracking and idempotent replay handling.

---

## 4. Idempotency Design

### 4.1 Problem

In distributed systems, clients may retry requests because of network failures, timeouts or uncertain responses.

Without idempotency, the same transaction could update an account balance multiple times.

Example:

```text
Initial Balance = 100

Request 1: CREDIT 50
Request 2: CREDIT 50 (Duplicate)

Incorrect Balance = 200
Expected Balance  = 150
```

### 4.2 Implementation

The application uses transaction IDs, request IDs and idempotency keys to identify repeated submissions.

The transaction submission flow checks existing request and transaction records before creating a new business transaction.

For an idempotent replay, the existing transaction result is returned instead of applying the business operation again.

### 4.3 Benefits

- Prevents duplicate balance updates.
- Supports safe client retries.
- Maintains consistent transaction state.
- Provides request-level traceability.

---

## 5. Duplicate Transaction Detection

The application checks whether the transaction has already been submitted or processed.

Duplicate requests are identified and handled using the DUPLICATE status where appropriate.

The transaction database also enforces uniqueness for transaction identifiers.

The application distinguishes between:

- An idempotent replay of an existing request.
- A duplicate business transaction submitted through a different request.

This prevents repeated requests from creating duplicate business effects.

---

## 6. Atomic Transaction Claiming

### 6.1 Problem

If multiple processor instances fetch the same eligible transaction at the same time, they may attempt to execute it simultaneously.

This can cause duplicate balance updates.

### 6.2 Solution

The application uses an atomic conditional database update to claim transactions.

Eligible statuses:

- RECEIVED
- PENDING
- RETRY_PENDING

The repository performs a conditional update:

```java
@Modifying(clearAutomatically = true, flushAutomatically = true)
@Transactional
@Query("""
        UPDATE Transaction t
        SET t.status = :processingStatus
        WHERE t.transactionId = :transactionId
          AND t.status IN :eligibleStatuses
        """)
int claimForProcessing(
        @Param("transactionId") String transactionId,
        @Param("processingStatus") TransactionStatus processingStatus,
        @Param("eligibleStatuses") List<TransactionStatus> eligibleStatuses
);
```

The update returns the number of affected rows.

- If the result is `1`, the transaction was successfully claimed.
- If the result is `0`, the transaction was already claimed or is no longer eligible.

Only a successfully claimed transaction proceeds to execution.

### 6.3 Benefits

- Reduces duplicate execution across processor instances.
- Uses database-level atomicity.
- Avoids relying only on in-memory synchronization.
- Provides a foundation for multi-instance processing.

---

## 7. Concurrent Processing Protection

### 7.1 Problem

Two transactions belonging to the same account may be processed concurrently.

Without synchronization, both transactions could read the same balance and overwrite each other's updates.

### 7.2 Pessimistic Locking

The application uses pessimistic write locking on account records.

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT a FROM Account a WHERE a.accountId = :accountId")
Optional<Account> findByAccountIdForUpdate(
        @Param("accountId") String accountId
);
```

When a transaction is executed, the corresponding account record is locked.

Other transactions attempting to lock the same account must wait until the current database transaction completes.

### 7.3 Optimistic Locking

The Account and Transaction entities also use JPA `@Version`.

This helps detect conflicting updates when the same entity is modified concurrently.

Pessimistic locking is used for account balance execution, while optimistic versioning provides an additional consistency mechanism.

---

## 8. Sequence-Based Transaction Ordering

### 8.1 Problem

Transaction requests may arrive in an order different from their intended business sequence.

Example:

```text
TXN-1002 -> Sequence 2
TXN-1001 -> Sequence 1
```

Processing sequence 2 before sequence 1 could result in incorrect account state.

### 8.2 Implementation

The application uses sequence numbers to determine processing order.

Each account maintains `lastProcessedSequence`.

Expected sequence:

```java
long expectedSequence =
        account.getLastProcessedSequence() + 1;
```

Processing rules:

**Case 1: Future Sequence**

If the transaction sequence is greater than the expected sequence:

```text
Transaction Sequence = 3
Expected Sequence    = 2
```

The transaction is marked PENDING.

**Case 2: Expected Sequence**

If the transaction sequence matches the expected sequence, the transaction can proceed.

After successful processing, the account's last processed sequence is updated.

**Case 3: Stale Sequence**

If the transaction sequence is less than the expected sequence, the transaction is marked FAILED as stale.

### 8.3 Business Failure Handling

For permanent business failures such as insufficient balance or invalid reversal, the implementation consumes the transaction sequence.

This prevents a later valid transaction from remaining blocked indefinitely by a permanently rejected business operation.

---

## 9. Transaction Processing Logic

The application supports three transaction types.

### 9.1 CREDIT

A CREDIT transaction increases the account balance.

```text
Initial Balance = 100
Credit Amount   = 50
Final Balance   = 150
```

The application adds the transaction amount to the account balance and marks the transaction PROCESSED.

### 9.2 DEBIT

A DEBIT transaction decreases the account balance.

```text
Initial Balance = 100
Debit Amount    = 30
Final Balance   = 70
```

Before deduction, the application checks whether the account has sufficient balance.

If the balance is insufficient:

- Transaction status becomes FAILED.
- Account balance remains unchanged.
- The transaction sequence is consumed.

### 9.3 REVERSAL

A REVERSAL transaction compensates for a previously processed CREDIT or DEBIT.

Rules:

1. Original transaction must exist.
2. Original transaction must be PROCESSED.
3. Original transaction cannot itself be a REVERSAL.
4. Original transaction must belong to the same account.
5. Currency must match.
6. Reversal amount must match the original amount.
7. Original transaction must not already have been reversed.

Reversing a CREDIT:

```text
Original CREDIT = 50
Current Balance = 150
Reversal Amount = 50
Final Balance   = 100
```

Reversing a DEBIT:

```text
Original DEBIT  = 30
Current Balance = 70
Reversal Amount = 30
Final Balance   = 100
```

A unique database constraint on `original_transaction_id` adds protection against multiple reversals of the same original transaction.

---

## 10. Retry and Failure Handling

### 10.1 Problem

Temporary failures may occur due to database connectivity issues, network interruptions or downstream service failures.

Such failures should not immediately result in permanent transaction rejection.

### 10.2 Implementation

The application simulates a temporary failure for transaction `TXN-2005`.

When an execution exception occurs:

1. The transaction execution database transaction is rolled back.
2. The processor catches the exception.
3. The failure handler runs in a separate transaction using `REQUIRES_NEW`.
4. Retry count is incremented.
5. The transaction is marked RETRY_PENDING or FAILED.

### 10.3 Retry Policy

Maximum attempts: 3.

| Attempt | Resulting Status |
|---|---|
| First failure | RETRY_PENDING |
| Second failure | RETRY_PENDING |
| Third failure | FAILED |

If a retry succeeds, the transaction becomes PROCESSED.

### 10.4 Retry Safety

The transaction execution method is transactional.

A temporary exception rolls back the execution transaction before the separate failure handler records the retry state.

This prevents a failed execution from committing a partial balance update.

---

## 11. Transaction Status Lifecycle

The application uses the following statuses:

| Status | Meaning |
|---|---|
| RECEIVED | Transaction request received |
| PROCESSING | Transaction successfully claimed |
| PENDING | Waiting for an earlier sequence |
| PROCESSED | Transaction completed |
| DUPLICATE | Duplicate request detected |
| RETRY_PENDING | Waiting for another processing attempt |
| FAILED | Transaction permanently failed |

### Status Flow

```text
RECEIVED
    |
    v
PROCESSING
    |
    +-------> PROCESSED
    |
    +-------> PENDING
    |
    +-------> FAILED
    |
    +-------> RETRY_PENDING
                    |
                    v
                PROCESSING
                    |
                    +-------> PROCESSED
                    |
                    +-------> FAILED
```

Duplicate detection is handled separately using DUPLICATE.

---

## 12. Background Processing

The application uses Spring's scheduling support.

The scheduler executes every five seconds.

```java
@Scheduled(fixedDelay = 5000)
public void processTransactions() {
    transactionProcessor.processPendingTransactions();
}
```

The scheduler invokes the transaction processor, which:

1. Fetches eligible transactions.
2. Attempts atomic claiming.
3. Executes successfully claimed transactions.
4. Handles processing failures.
5. Updates transaction status.

The application also exposes a manual processing endpoint for operational testing.

---

## 13. Database Transaction Management

Spring's `@Transactional` annotation is used to maintain consistency during transaction execution.

The main execution operation includes:

- Loading the transaction.
- Locking the account.
- Validating sequence.
- Applying balance changes.
- Updating transaction status.
- Updating account sequence.
- Updating related request status.

Temporary execution failures roll back the execution transaction.

The failure handler uses `Propagation.REQUIRES_NEW` to persist retry information separately.

The application uses an H2 file-based database for local persistence.

---

## 14. REST API Design

### Account APIs

| Method | Endpoint | Purpose |
|---|---|---|
| POST | `/api/v1/accounts?accountId=ACC-1001` | Create account |
| GET | `/api/v1/accounts/{accountId}` | Retrieve account |

### Transaction APIs

| Method | Endpoint | Purpose |
|---|---|---|
| POST | `/api/v1/transactions` | Submit transaction |
| POST | `/api/v1/transactions/batch` | Submit batch transactions |
| GET | `/api/v1/transactions/{transactionId}` | Retrieve transaction |
| POST | `/api/v1/transactions/process` | Manually trigger processing |
| GET | `/api/v1/transactions/summary` | Retrieve processing summary |

### API Documentation

Swagger UI:

```text
http://localhost:8080/swagger-ui/index.html
```

---

## 15. Observability and Logging

The application uses SLF4J logging to capture important transaction processing events.

Examples include:

- Transaction processing started.
- Transaction successfully claimed.
- Transaction processed.
- Transaction marked pending.
- Duplicate request detected.
- Business validation failure.
- Retry attempt recorded.
- Transaction permanently failed.

### Correlation ID

The application supports the `X-Correlation-ID` header.

A filter creates or propagates the correlation ID and stores it in MDC.

The logging pattern includes the correlation ID:

```yaml
logging:
  pattern:
    level: "%5p [correlationId:%X{correlationId:-}]"
```

This makes it easier to trace related requests through application logs.

### Processing Summary

The summary API provides counts for:

- RECEIVED
- PROCESSING
- PENDING
- PROCESSED
- DUPLICATE
- RETRY_PENDING
- FAILED
- TOTAL

---

## 16. Testing Strategy

The project includes unit tests and REST API integration tests.

### Unit Testing

Unit tests cover:

- Successful CREDIT processing.
- Successful DEBIT processing.
- Insufficient balance.
- Out-of-order transaction handling.
- Temporary failure and retry handling.
- Retry exhaustion.
- Successful CREDIT reversal.
- Successful DEBIT reversal.
- Invalid reversal.
- Duplicate reversal prevention.

Mockito is used to mock repository dependencies.

### Integration Testing

Spring Boot Test and MockMvc are used to test REST API behavior.

Integration scenarios include:

1. Complete account and transaction processing flow.
2. Duplicate transaction submission.
3. Negative amount validation.
4. Transaction submission for a missing account.
5. Retrieval of a missing transaction.
6. Transaction summary API.

### Test Execution

Run all tests:

```powershell
.\mvnw.cmd clean test
```

Run integration tests:

```powershell
.\mvnw.cmd test -Dtest=TransactionIntegrationTest
```

Run execution service tests:

```powershell
.\mvnw.cmd test -Dtest=TransactionExecutionServiceTest
```

### Latest Recorded Test Evidence

| Test Suite | Tests | Result |
|---|---:|---|
| Existing unit tests | 25 | Passed |
| Integration tests | 6 | Passed separately |
| Total unique tests | 31 | Both runs successful |

The complete suite should be executed once more before final submission to confirm all 31 tests pass together.

---

## 17. Design Decisions and Trade-offs

### 17.1 H2 Instead of PostgreSQL

H2 was selected to keep the assessment lightweight and easy to run locally.

A production deployment should use a production-grade relational database such as PostgreSQL.

### 17.2 Sequence-Based Ordering

Sequence-based processing was selected because transaction ordering is important for account balance consistency.

Future transactions are kept pending until the expected sequence becomes available.

### 17.3 Pessimistic Locking

Pessimistic account locking was selected to prevent concurrent balance modifications for the same account.

The trade-off is that transactions for the same account may wait for one another.

### 17.4 Atomic Claiming

A conditional database update was selected instead of relying on in-memory synchronization.

This makes claiming safer when multiple application instances share the same database.

### 17.5 Scheduled Processing

A Spring scheduler was selected to provide a simple background processing mechanism without requiring an external message broker.

A production system could replace or extend this with Kafka or RabbitMQ.

---

## 18. Production Hardening Recommendations

The current implementation demonstrates the core processing behavior required by the assessment. The following improvements are recommended before production use.

### 18.1 Stuck Transaction Recovery

A transaction may remain in PROCESSING if the application crashes after claiming it but before completing execution.

Introduce:

- Claim timestamp or lease expiration.
- Recovery of expired claims.
- Safe re-claiming after a timeout.

### 18.2 Retry Exhaustion and Sequence Recovery

Define how later transactions should proceed after a technical failure reaches the maximum retry count.

The sequence progression policy should be handled carefully to avoid silently skipping required business operations.

### 18.3 Retry Backoff

Introduce exponential backoff and configurable retry policies to avoid repeated immediate failures.

### 18.4 Message Broker Integration

Integrate Kafka or RabbitMQ for scalable asynchronous transaction ingestion.

### 18.5 Database Improvements

- Use PostgreSQL.
- Add indexes for frequently queried fields.
- Review locking and isolation under concurrent load.
- Add database migration management.

### 18.6 Security

- Add authentication and authorization.
- Secure transaction APIs.
- Introduce secrets management.
- Avoid logging sensitive financial information.

### 18.7 Monitoring and Tracing

- Add Micrometer metrics.
- Integrate Prometheus and Grafana.
- Add OpenTelemetry distributed tracing.
- Monitor processing latency, retries and failure rates.

### 18.8 Additional Testing

Add multi-threaded and multi-instance integration tests to validate atomic claiming, reversal uniqueness and account locking under real concurrency.

---

## 19. AI-Assisted Development

AI assistance was used during the development process to:

- Analyze assessment requirements.
- Explore architecture and implementation approaches.
- Generate and review Java implementation code.
- Improve exception and transaction handling.
- Identify edge cases.
- Create and refine unit and integration tests.
- Review production-hardening considerations.

Generated code was reviewed and validated through test execution and REST API testing.

The implementation decisions and trade-offs were reviewed to ensure they could be explained independently.

---

## 20. Conclusion

The Idempotent Background Transaction Processing Service demonstrates a practical design for reliable financial transaction processing.

The implementation addresses duplicate requests, out-of-order transactions, atomic transaction claiming, concurrent account updates, CREDIT and DEBIT processing, REVERSAL operations, retry handling and operational visibility.

The solution provides a foundation for a more scalable and production-ready distributed transaction processing platform.