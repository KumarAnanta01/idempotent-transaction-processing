
package com.mphasis.transaction.entity;

import com.mphasis.transaction.enums.TransactionStatus;
import com.mphasis.transaction.enums.TransactionType;

import jakarta.persistence.*;

import lombok.*;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "transactions",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_transaction_id",
                        columnNames = "transaction_id"
                ),
                // Only one reversal can reference an original transaction.
                @UniqueConstraint(
                        name = "uk_original_transaction_reversal",
                        columnNames = "original_transaction_id"
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {

    // Unique business transaction ID
    @Id
    @Column(name = "transaction_id", nullable = false)
    private String transactionId;

    // Account associated with the transaction
    @Column(name = "account_id", nullable = false)
    private String accountId;

    // CREDIT, DEBIT or REVERSAL
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "transaction_type", nullable = false, length = 20)
    private TransactionType transactionType;

    // Original transaction ID for REVERSAL transactions
    @Column(name = "original_transaction_id", length = 100)
    private String originalTransactionId;

    // Transaction amount
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    // Currency code (example: INR, USD)
    @Column(nullable = false, length = 3)
    private String currency;

    // Per-account ordering sequence
    @Column(name = "sequence_number", nullable = false)
    private Long sequenceNumber;

    // Transaction processing status
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private TransactionStatus status = TransactionStatus.RECEIVED;

    // Number of processing attempts
    @Builder.Default
    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;

    // Stores the last processing error
    @Column(name = "last_error", length = 1000)
    private String lastError;

    // Transaction creation timestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    // Last modification timestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "event_time", nullable = false)
    private LocalDateTime eventTime;

    // Optimistic locking
    @Version
    private Long version;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
