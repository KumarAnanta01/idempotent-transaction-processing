
package com.mphasis.transaction.service;

import com.mphasis.transaction.entity.Account;
import com.mphasis.transaction.entity.Transaction;
import com.mphasis.transaction.enums.TransactionStatus;
import com.mphasis.transaction.enums.TransactionType;
import com.mphasis.transaction.exception.TransientProcessingException;
import com.mphasis.transaction.repository.AccountRepository;
import com.mphasis.transaction.repository.TransactionRepository;
import com.mphasis.transaction.repository.TransactionRequestRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@Slf4j
@RequiredArgsConstructor
public class TransactionExecutionService {

    private static final int MAX_RETRIES = 3;

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionRequestRepository requestRepository;

    @Transactional
    public void processOne(String transactionId) {

        log.info("Starting transaction processing: transactionId={}",
                transactionId);

        Transaction transaction = transactionRepository
                .findById(transactionId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Transaction not found: " + transactionId));

        // Execute only transactions successfully claimed by the processor.
        if (transaction.getStatus() != TransactionStatus.PROCESSING) {

            log.warn("Skipping transaction because it is not claimed: transactionId={}, status={}",
                    transactionId, transaction.getStatus());

            return;
        }

        // Lock account to prevent concurrent balance updates.
        Account account = accountRepository
                .findByAccountIdForUpdate(transaction.getAccountId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Account not found"));

        long expectedSequence =
                account.getLastProcessedSequence() + 1;

        // Wait for the missing earlier transaction.
        if (transaction.getSequenceNumber() > expectedSequence) {

            log.info("Transaction waiting for earlier sequence: transactionId={}, expectedSequence={}",
                    transactionId, expectedSequence);

            updateStatus(
                    transactionId,
                    TransactionStatus.PENDING,
                    "Waiting for sequence " + expectedSequence
            );
            return;
        }

        // Reject an outdated transaction.
        if (transaction.getSequenceNumber() < expectedSequence) {

            log.warn("Stale transaction sequence: transactionId={}, expectedSequence={}",
                    transactionId, expectedSequence);

            updateStatus(
                    transactionId,
                    TransactionStatus.FAILED,
                    "Stale transaction sequence"
            );
            return;
        }

        // Simulate one temporary failure for assessment testing.
        if ("TXN-2005".equals(transactionId)
                && transaction.getRetryCount() == 0) {

            log.warn("Simulating temporary processing failure: transactionId={}",
                    transactionId);

            throw new TransientProcessingException(
                    "Simulated temporary processing failure");
        }

        BigDecimal balance = account.getBalance();

        // Process CREDIT.
        if (transaction.getTransactionType() == TransactionType.CREDIT) {

            account.setBalance(balance.add(transaction.getAmount()));

            log.info("Credit transaction applied: transactionId={}, amount={}",
                    transactionId, transaction.getAmount());
        }

        // Process DEBIT.
        else if (transaction.getTransactionType() == TransactionType.DEBIT) {

            if (balance.compareTo(transaction.getAmount()) < 0) {

                log.warn("Insufficient balance: transactionId={}, accountId={}",
                        transactionId, account.getAccountId());

                failBusinessTransaction(
                        transaction,
                        account,
                        "Insufficient account balance"
                );
                return;
            }

            account.setBalance(balance.subtract(transaction.getAmount()));

            log.info("Debit transaction applied: transactionId={}, amount={}",
                    transactionId, transaction.getAmount());
        }

        // Process REVERSAL.
        else if (transaction.getTransactionType() == TransactionType.REVERSAL) {

            String originalTransactionId =
                    transaction.getOriginalTransactionId();

            if (originalTransactionId == null
                    || originalTransactionId.isBlank()) {

                failBusinessTransaction(
                        transaction,
                        account,
                        "Original transaction ID is required for reversal"
                );
                return;
            }

            // Find the original transaction.
            Transaction original = transactionRepository
                    .findById(originalTransactionId)
                    .orElse(null);

            if (original == null) {

                failBusinessTransaction(
                        transaction,
                        account,
                        "Original transaction not found"
                );
                return;
            }

            // Original transaction must already be processed.
            if (original.getStatus() != TransactionStatus.PROCESSED) {

                failBusinessTransaction(
                        transaction,
                        account,
                        "Original transaction is not processed"
                );
                return;
            }

            // A reversal cannot reverse another reversal.
            if (original.getTransactionType() == TransactionType.REVERSAL) {

                failBusinessTransaction(
                        transaction,
                        account,
                        "Cannot reverse another reversal"
                );
                return;
            }

            // Original transaction must belong to the same account.
            if (!original.getAccountId().equals(transaction.getAccountId())) {

                failBusinessTransaction(
                        transaction,
                        account,
                        "Original transaction belongs to another account"
                );
                return;
            }

            // Currency must match.
            if (!original.getCurrency().equals(transaction.getCurrency())) {

                failBusinessTransaction(
                        transaction,
                        account,
                        "Reversal currency does not match original transaction"
                );
                return;
            }

            // Reversal amount must match original amount.
            if (original.getAmount().compareTo(transaction.getAmount()) != 0) {

                failBusinessTransaction(
                        transaction,
                        account,
                        "Reversal amount must match original transaction amount"
                );
                return;
            }

            // Prevent multiple reversals of the same original transaction.
            boolean alreadyReversed = transactionRepository
                    .existsByOriginalTransactionId(originalTransactionId);

            if (alreadyReversed) {

                failBusinessTransaction(
                        transaction,
                        account,
                        "Original transaction has already been reversed"
                );
                return;
            }

            // Reverse the original CREDIT by subtracting the amount.
            if (original.getTransactionType() == TransactionType.CREDIT) {

                if (balance.compareTo(original.getAmount()) < 0) {

                    failBusinessTransaction(
                            transaction,
                            account,
                            "Insufficient balance to reverse original credit"
                    );
                    return;
                }

                account.setBalance(balance.subtract(original.getAmount()));
            }

            // Reverse the original DEBIT by adding the amount.
            else if (original.getTransactionType() == TransactionType.DEBIT) {

                account.setBalance(balance.add(original.getAmount()));
            }

            log.info("Reversal applied: reversalTransactionId={}, originalTransactionId={}",
                    transactionId, originalTransactionId);
        }

        // Update account sequence after successful processing.
        account.setLastProcessedSequence(
                transaction.getSequenceNumber());

        transaction.setStatus(TransactionStatus.PROCESSED);
        transaction.setLastError(null);

        accountRepository.save(account);
        transactionRepository.save(transaction);

        updateRequestStatus(
                transactionId,
                TransactionStatus.PROCESSED,
                "Transaction processed successfully"
        );

        log.info("Transaction processed successfully: transactionId={}, accountId={}, balance={}",
                transactionId,
                account.getAccountId(),
                account.getBalance());
    }

    // Mark a permanent business failure and consume its sequence number.
    private void failBusinessTransaction(
            Transaction transaction,
            Account account,
            String message) {

        transaction.setStatus(TransactionStatus.FAILED);
        transaction.setLastError(message);

        // Consume the sequence so the next transaction can proceed.
        account.setLastProcessedSequence(
                transaction.getSequenceNumber());

        accountRepository.save(account);
        transactionRepository.save(transaction);

        updateRequestStatus(
                transaction.getTransactionId(),
                TransactionStatus.FAILED,
                message
        );

        log.warn("Business transaction failed: transactionId={}, sequenceNumber={}, message={}",
                transaction.getTransactionId(),
                transaction.getSequenceNumber(),
                message);
    }

    // Update transaction and all related request records.
    private void updateStatus(
            String transactionId,
            TransactionStatus status,
            String message) {

        Transaction transaction = transactionRepository
                .findById(transactionId)
                .orElseThrow();

        transaction.setStatus(status);
        transaction.setLastError(message);

        transactionRepository.save(transaction);

        updateRequestStatus(transactionId, status, message);

        log.info("Transaction status updated: transactionId={}, status={}, message={}",
                transactionId, status, message);
    }

    // Update every request associated with this transaction.
    private void updateRequestStatus(
            String transactionId,
            TransactionStatus status,
            String message) {

        requestRepository.findAllByTransactionId(transactionId)
                .forEach(request -> {
                    request.setStatus(status);
                    request.setResponseMessage(message);
                });
    }

    // Handle failures in a separate database transaction.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleFailure(
            String transactionId,
            String errorMessage) {

        log.error("Handling transaction failure: transactionId={}, error={}",
                transactionId, errorMessage);

        Transaction transaction = transactionRepository
                .findById(transactionId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Transaction not found: " + transactionId));

        int attempts = transaction.getRetryCount() + 1;

        transaction.setRetryCount(attempts);
        transaction.setLastError(errorMessage);

        TransactionStatus status = attempts >= MAX_RETRIES
                ? TransactionStatus.FAILED
                : TransactionStatus.RETRY_PENDING;

        transaction.setStatus(status);

        transactionRepository.save(transaction);

        updateRequestStatus(transactionId, status, errorMessage);

        log.warn("Transaction failure recorded: transactionId={}, retryCount={}, status={}",
                transactionId, attempts, status);
    }
}
