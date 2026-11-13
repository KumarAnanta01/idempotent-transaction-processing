
package com.mphasis.transaction.entity;

import com.mphasis.transaction.enums.TransactionStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "transaction_requests",
        indexes = {
                @Index(
                        name = "idx_request_transaction",
                        columnList = "transaction_id"
                )
        },
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_request_id",
                        columnNames = "request_id"
                ),
                @UniqueConstraint(
                        name = "uk_idempotency_key",
                        columnNames = "idempotency_key"
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Unique incoming request ID
    @Column(name = "request_id", nullable = false)
    private String requestId;

    // Business transaction ID
    @Column(name = "transaction_id", nullable = false)
    private String transactionId;

    // Prevents repeated processing of the same API request
    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    // Store enum values as VARCHAR instead of native H2 ENUM
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 30)
    private TransactionStatus status;

    // Response or duplicate information
    @Column(name = "response_message", length = 1000)
    private String responseMessage;

    // Request received timestamp
    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;

    @PrePersist
    protected void onCreate() {
        receivedAt = LocalDateTime.now();
    }
}
