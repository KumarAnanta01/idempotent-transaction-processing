
package com.mphasis.transaction.repository;

import com.mphasis.transaction.entity.TransactionRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;

public interface TransactionRequestRepository
        extends JpaRepository<TransactionRequest, Long> {

    // Check whether request ID already exists
    boolean existsByRequestId(String requestId);

    // Check whether idempotency key already exists
    boolean existsByIdempotencyKey(String idempotencyKey);

    // Find request using idempotency key
    Optional<TransactionRequest> findByIdempotencyKey(
            String idempotencyKey
    );

    // Find request using request ID
    Optional<TransactionRequest> findByRequestId(
            String requestId
    );
    List<TransactionRequest> findAllByTransactionId(String transactionId);
}
