
package com.mphasis.transaction.controller;

import com.mphasis.transaction.dto.request.TransactionRequestDTO;
import com.mphasis.transaction.dto.request.BatchTransactionRequestDTO;
import com.mphasis.transaction.dto.response.TransactionResponseDTO;
import com.mphasis.transaction.dto.response.TransactionSummaryDTO;
import com.mphasis.transaction.enums.TransactionStatus;
import com.mphasis.transaction.processor.TransactionProcessor;
import com.mphasis.transaction.repository.TransactionRepository;
import com.mphasis.transaction.service.TransactionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
@Tag(
        name = "Transaction APIs",
        description = "APIs for submitting, processing and tracking transactions"
)
public class TransactionController {

    private final TransactionService transactionService;
    private final TransactionRepository transactionRepository;
    private final TransactionProcessor transactionProcessor;

    // Submit single transaction
    @PostMapping
    @Operation(summary = "Submit a single transaction")
    public ResponseEntity<TransactionResponseDTO> submitTransaction(
            @Valid @RequestBody TransactionRequestDTO request) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(transactionService.submitTransaction(request));
    }

    // Submit multiple transactions
    @PostMapping("/batch")
    @Operation(summary = "Submit multiple transactions in a batch")
    public ResponseEntity<List<TransactionResponseDTO>> submitBatch(
            @Valid @RequestBody BatchTransactionRequestDTO request) {

        List<TransactionResponseDTO> responses =
                request.transactions().stream()
                        .map(transactionService::submitTransaction)
                        .toList();

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(responses);
    }

    // Get transaction details
    @GetMapping("/{transactionId}")
    @Operation(summary = "Get transaction details by transaction ID")
    public ResponseEntity<TransactionResponseDTO> getTransaction(
            @PathVariable String transactionId) {

        return ResponseEntity.ok(
                transactionService.getTransaction(transactionId));
    }

    // Manually trigger background processing
    @PostMapping("/process")
    @Operation(summary = "Manually trigger pending transaction processing")
    public ResponseEntity<String> processTransactions() {

        transactionProcessor.processPendingTransactions();

        return ResponseEntity.ok(
                "Transaction processing triggered successfully");
    }

    // Processing summary
    @GetMapping("/summary")
    @Operation(summary = "Get transaction processing summary")
    public ResponseEntity<TransactionSummaryDTO> getSummary() {

        long received = transactionRepository.countByStatus(
                TransactionStatus.RECEIVED);

        long processing = transactionRepository.countByStatus(
                TransactionStatus.PROCESSING);

        long pending = transactionRepository.countByStatus(
                TransactionStatus.PENDING);

        long processed = transactionRepository.countByStatus(
                TransactionStatus.PROCESSED);

        long duplicate = transactionRepository.countByStatus(
                TransactionStatus.DUPLICATE);

        long retryPending = transactionRepository.countByStatus(
                TransactionStatus.RETRY_PENDING);

        long failed = transactionRepository.countByStatus(
                TransactionStatus.FAILED);

        long total = received + processing + pending + processed
                + duplicate + retryPending + failed;

        return ResponseEntity.ok(
                new TransactionSummaryDTO(
                        received, processing, pending,
                        processed, duplicate, retryPending,
                        failed, total
                )
        );
    }
}
