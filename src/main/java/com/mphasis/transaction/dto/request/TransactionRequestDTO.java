
package com.mphasis.transaction.dto.request;

import com.mphasis.transaction.enums.TransactionType;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransactionRequestDTO(

        @NotBlank(message = "Transaction ID is required")
        String transactionId,

        @NotBlank(message = "Request ID is required")
        String requestId,

        @NotBlank(message = "Idempotency key is required")
        String idempotencyKey,

        @NotBlank(message = "Account ID is required")
        String accountId,

        @NotNull(message = "Transaction type is required")
        TransactionType transactionType,

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be positive")
        BigDecimal amount,

        @NotBlank(message = "Currency is required")
        @Pattern(
                regexp = "^[A-Z]{3}$",
                message = "Currency must be a 3-letter code"
        )
        String currency,

        @NotNull(message = "Sequence number is required")
        @Positive(message = "Sequence number must be positive")
        Long sequenceNumber,

        @NotNull(message = "Event time is required")
        LocalDateTime eventTime,

        // Required when transaction type is REVERSAL
        String originalTransactionId

) {
}
