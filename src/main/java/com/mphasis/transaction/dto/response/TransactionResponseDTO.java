
package com.mphasis.transaction.dto.response;

import com.mphasis.transaction.enums.TransactionStatus;
import java.time.LocalDateTime;

public record TransactionResponseDTO(

        String transactionId,

        String accountId,

        TransactionStatus status,

        Integer retryCount,

        String message,

        LocalDateTime processedAt

) {
}
