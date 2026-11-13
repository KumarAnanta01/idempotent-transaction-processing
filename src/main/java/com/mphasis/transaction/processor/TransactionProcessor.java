
package com.mphasis.transaction.processor;

import com.mphasis.transaction.entity.Transaction;
import com.mphasis.transaction.enums.TransactionStatus;
import com.mphasis.transaction.exception.TransientProcessingException;
import com.mphasis.transaction.repository.TransactionRepository;
import com.mphasis.transaction.service.TransactionExecutionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class TransactionProcessor {

    private final TransactionRepository transactionRepository;
    private final TransactionExecutionService executionService;

    public void processPendingTransactions() {

        // Fetch transactions eligible for processing.
        List<Transaction> transactions =
                transactionRepository
                        .findByStatusInOrderByAccountIdAscSequenceNumberAsc(
                                List.of(
                                        TransactionStatus.RECEIVED,
                                        TransactionStatus.PENDING,
                                        TransactionStatus.RETRY_PENDING
                                )
                        );

        log.info("Found {} transactions to process",
                transactions.size());

        for (Transaction transaction : transactions) {

            String transactionId = transaction.getTransactionId();

            try {

                // Atomically claim the transaction.
                int claimed = transactionRepository.claimForProcessing(
                        transactionId,
                        TransactionStatus.PROCESSING,
                        List.of(
                                TransactionStatus.RECEIVED,
                                TransactionStatus.PENDING,
                                TransactionStatus.RETRY_PENDING
                        )
                );

                // Another application instance may have claimed it.
                if (claimed == 0) {

                    log.info("Transaction already claimed or not eligible: {}",
                            transactionId);

                    continue;
                }

                log.info("Transaction successfully claimed: {}",
                        transactionId);

                // Execute only after successfully claiming.
                executionService.processOne(transactionId);

                log.info("Transaction processing completed: {}",
                        transactionId);

            } catch (TransientProcessingException ex) {

                log.warn("Temporary failure for transaction {}: {}",
                        transactionId, ex.getMessage());

                handleProcessingFailure(transactionId, ex);

            } catch (Exception ex) {

                log.error("Unexpected error processing transaction {}",
                        transactionId, ex);

                handleProcessingFailure(transactionId, ex);
            }
        }
    }

    // Handle failure without stopping remaining transactions.
    private void handleProcessingFailure(
            String transactionId,
            Exception exception) {

        try {

            executionService.handleFailure(
                    transactionId,
                    exception.getMessage() != null
                            ? exception.getMessage()
                            : "Unknown processing error"
            );

        } catch (Exception handlingException) {

            log.error(
                    "Could not update failure status for transaction {}",
                    transactionId,
                    handlingException
            );
        }
    }
}
