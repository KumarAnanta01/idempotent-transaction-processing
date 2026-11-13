
package com.mphasis.transaction.processor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class TransactionScheduler {

    private final TransactionProcessor transactionProcessor;

    // Run background processing every 5 seconds
    // after the previous execution completes.
    @Scheduled(fixedDelay = 5000)
    public void processTransactions() {

        log.info("Background transaction processing started");

        try {

            transactionProcessor.processPendingTransactions();

            log.info("Background transaction processing completed");

        } catch (Exception ex) {

            // Prevent unexpected errors from escaping the scheduled method.
            log.error("Unexpected error during background processing", ex);
        }
    }
}
