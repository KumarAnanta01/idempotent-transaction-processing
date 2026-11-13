
package com.mphasis.transaction.service.impl;

import com.mphasis.transaction.dto.request.TransactionRequestDTO;
import com.mphasis.transaction.dto.response.TransactionResponseDTO;
import com.mphasis.transaction.entity.Transaction;
import com.mphasis.transaction.entity.TransactionRequest;
import com.mphasis.transaction.enums.TransactionStatus;
import com.mphasis.transaction.repository.AccountRepository;
import com.mphasis.transaction.repository.TransactionRepository;
import com.mphasis.transaction.repository.TransactionRequestRepository;
import com.mphasis.transaction.service.TransactionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class TransactionServiceImpl implements TransactionService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionRequestRepository requestRepository;

    @Override
    public TransactionResponseDTO submitTransaction(TransactionRequestDTO request) {

        log.info("Received transaction request: transactionId={}, accountId={}, requestId={}",
                request.transactionId(), request.accountId(), request.requestId());

        // Step 1: Validate account existence.
        if (!accountRepository.existsById(request.accountId())) {
            log.warn("Account not found: accountId={}", request.accountId());
            throw new IllegalArgumentException(
                    "Account not found: " + request.accountId());
        }

        // Step 2: Validate original transaction ID for REVERSAL.
        if (request.transactionType().name().equals("REVERSAL")
                && (request.originalTransactionId() == null
                || request.originalTransactionId().isBlank())) {

            throw new IllegalArgumentException(
                    "Original transaction ID is required for REVERSAL");
        }

        // Step 3: Check whether this idempotency key was already used.
        var existingKey = requestRepository
                .findByIdempotencyKey(request.idempotencyKey());

        if (existingKey.isPresent()) {

            TransactionRequest previous = existingKey.get();

            // Reject reuse of the key for a different transaction.
            if (!previous.getTransactionId().equals(request.transactionId())
                    || !previous.getRequestId().equals(request.requestId())) {

                log.warn("Idempotency key reuse rejected: transactionId={}",
                        request.transactionId());

                throw new IllegalArgumentException(
                        "Idempotency key already used for another request");
            }

            // Return the existing transaction without processing again.
            log.info("Idempotent request detected. Returning existing transaction: transactionId={}",
                    request.transactionId());

            return getTransaction(request.transactionId());
        }

        // Step 4: Prevent duplicate request IDs.
        if (requestRepository.existsByRequestId(request.requestId())) {

            log.warn("Duplicate request ID detected: requestId={}",
                    request.requestId());

            throw new IllegalArgumentException(
                    "Request ID already exists");
        }

        // Step 5: Check duplicate business transaction ID.
        if (transactionRepository.existsById(request.transactionId())) {

            log.warn("Duplicate transaction detected: transactionId={}",
                    request.transactionId());

            TransactionRequest duplicateRequest =
                    TransactionRequest.builder()
                            .requestId(request.requestId())
                            .transactionId(request.transactionId())
                            .idempotencyKey(request.idempotencyKey())
                            .status(TransactionStatus.DUPLICATE)
                            .responseMessage("Duplicate transaction detected")
                            .build();

            requestRepository.save(duplicateRequest);

            return new TransactionResponseDTO(
                    request.transactionId(),
                    request.accountId(),
                    TransactionStatus.DUPLICATE,
                    0,
                    "Duplicate transaction detected",
                    null
            );
        }

        // Step 6: Create a new transaction.
        Transaction transaction = Transaction.builder()
                .transactionId(request.transactionId())
                .accountId(request.accountId())
                .transactionType(request.transactionType())
                .originalTransactionId(request.originalTransactionId())
                .amount(request.amount())
                .currency(request.currency())
                .sequenceNumber(request.sequenceNumber())
                .eventTime(request.eventTime())
                .status(TransactionStatus.RECEIVED)
                .retryCount(0)
                .build();

        transactionRepository.save(transaction);

        // Step 7: Save request tracking information.
        TransactionRequest transactionRequest =
                TransactionRequest.builder()
                        .requestId(request.requestId())
                        .transactionId(request.transactionId())
                        .idempotencyKey(request.idempotencyKey())
                        .status(TransactionStatus.RECEIVED)
                        .responseMessage("Transaction received successfully")
                        .build();

        requestRepository.save(transactionRequest);

        log.info("Transaction accepted successfully: transactionId={}, status={}",
                transaction.getTransactionId(), transaction.getStatus());

        // Step 8: Return the initial transaction response.
        return new TransactionResponseDTO(
                transaction.getTransactionId(),
                transaction.getAccountId(),
                transaction.getStatus(),
                transaction.getRetryCount(),
                "Transaction received successfully",
                null
        );
    }

    // Retrieve the latest transaction status.
    @Override
    @Transactional(readOnly = true)
    public TransactionResponseDTO getTransaction(String transactionId) {

        log.debug("Fetching transaction details: transactionId={}", transactionId);

        Transaction transaction = transactionRepository
                .findById(transactionId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Transaction not found: " + transactionId));

        return new TransactionResponseDTO(
                transaction.getTransactionId(),
                transaction.getAccountId(),
                transaction.getStatus(),
                transaction.getRetryCount(),
                transaction.getLastError(),
                null
        );
    }
}
