
package com.mphasis.transaction.repository;

import com.mphasis.transaction.entity.Transaction;
import com.mphasis.transaction.enums.TransactionStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface TransactionRepository
        extends JpaRepository<Transaction, String> {

    List<Transaction> findByStatusOrderBySequenceNumberAsc(
            TransactionStatus status
    );

    List<Transaction> findByAccountIdOrderBySequenceNumberAsc(
            String accountId
    );

    List<Transaction> findByStatusInOrderByAccountIdAscSequenceNumberAsc(
            List<TransactionStatus> statuses
    );

    long countByStatus(TransactionStatus status);

    // Check whether an original transaction has already been reversed.
    boolean existsByOriginalTransactionId(String originalTransactionId);

    // Atomically claim a transaction for processing.
    @Modifying(
            clearAutomatically = true,
            flushAutomatically = true
    )
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
}
