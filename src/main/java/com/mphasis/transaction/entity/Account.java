
package com.mphasis.transaction.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "accounts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Account {

    // Unique account identifier (example: ACC-201)
    @Id
    @Column(name = "account_id", nullable = false)
    private String accountId;

    // Current account balance
    @Column(name = "balance", nullable = false,
            precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal balance = BigDecimal.ZERO;

    // Last successfully processed sequence number
    @Column(name = "last_processed_sequence",
            nullable = false)
    @Builder.Default
    private Long lastProcessedSequence = 0L;

    // Used to prevent concurrent balance update conflicts
    @Version
    private Long version;

    // Account creation timestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    // Last modification timestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

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
