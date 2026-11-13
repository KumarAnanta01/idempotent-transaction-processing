
package com.mphasis.transaction.dto.response;

public record TransactionSummaryDTO(
        long received,
        long processing,
        long pending,
        long processed,
        long duplicate,
        long retryPending,
        long failed,
        long total
) {
}
