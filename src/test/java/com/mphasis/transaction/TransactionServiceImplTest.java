
package com.mphasis.transaction;

import com.mphasis.transaction.dto.request.TransactionRequestDTO;
import com.mphasis.transaction.dto.response.TransactionResponseDTO;
import com.mphasis.transaction.entity.Account;
import com.mphasis.transaction.entity.Transaction;
import com.mphasis.transaction.entity.TransactionRequest;
import com.mphasis.transaction.enums.TransactionStatus;
import com.mphasis.transaction.enums.TransactionType;
import com.mphasis.transaction.repository.AccountRepository;
import com.mphasis.transaction.repository.TransactionRepository;
import com.mphasis.transaction.repository.TransactionRequestRepository;
import com.mphasis.transaction.service.impl.AccountServiceImpl;
import com.mphasis.transaction.service.impl.TransactionServiceImpl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionServiceImplTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private TransactionRequestRepository requestRepository;

    @InjectMocks
    private TransactionServiceImpl transactionService;

    @InjectMocks
    private AccountServiceImpl accountService;

    // ==========================================
    // TRANSACTION SERVICE TESTS
    // ==========================================

    // Test 1: Same idempotency key should return existing transaction
    @Test
    void shouldReturnExistingTransactionForSameIdempotencyKey() {

        TransactionRequestDTO request = new TransactionRequestDTO(
                "TXN-3001",
                "REQ-3001",
                "IDEMP-3001",
                "ACC-101",
                TransactionType.CREDIT,
                new BigDecimal("100"),
                "INR",
                1L,
                LocalDateTime.now(),
                null
        );

        TransactionRequest existingRequest = TransactionRequest.builder()
                .requestId("REQ-3001")
                .transactionId("TXN-3001")
                .idempotencyKey("IDEMP-3001")
                .status(TransactionStatus.PROCESSED)
                .build();

        Transaction existingTransaction = Transaction.builder()
                .transactionId("TXN-3001")
                .accountId("ACC-101")
                .transactionType(TransactionType.CREDIT)
                .amount(new BigDecimal("100"))
                .currency("INR")
                .sequenceNumber(1L)
                .status(TransactionStatus.PROCESSED)
                .retryCount(0)
                .build();

        when(accountRepository.existsById("ACC-101"))
                .thenReturn(true);

        when(requestRepository.findByIdempotencyKey("IDEMP-3001"))
                .thenReturn(Optional.of(existingRequest));

        when(transactionRepository.findById("TXN-3001"))
                .thenReturn(Optional.of(existingTransaction));

        TransactionResponseDTO response =
                transactionService.submitTransaction(request);

        assertEquals("TXN-3001", response.transactionId());
        assertEquals(TransactionStatus.PROCESSED, response.status());

        verify(transactionRepository, never())
                .save(any(Transaction.class));

        verify(requestRepository, never())
                .save(any(TransactionRequest.class));
    }

    // Test 2: Same transaction ID should be marked as duplicate
    @Test
    void shouldMarkTransactionAsDuplicateWhenTransactionIdAlreadyExists() {

        TransactionRequestDTO request = new TransactionRequestDTO(
                "TXN-3002",
                "REQ-3002",
                "IDEMP-3002",
                "ACC-101",
                TransactionType.CREDIT,
                new BigDecimal("100"),
                "INR",
                2L,
                LocalDateTime.now(),
                null
        );

        when(accountRepository.existsById("ACC-101"))
                .thenReturn(true);

        when(requestRepository.findByIdempotencyKey("IDEMP-3002"))
                .thenReturn(Optional.empty());

        when(requestRepository.existsByRequestId("REQ-3002"))
                .thenReturn(false);

        when(transactionRepository.existsById("TXN-3002"))
                .thenReturn(true);

        TransactionResponseDTO response =
                transactionService.submitTransaction(request);

        assertEquals("TXN-3002", response.transactionId());
        assertEquals(TransactionStatus.DUPLICATE, response.status());

        assertEquals(
                "Duplicate transaction detected",
                response.message()
        );

        verify(requestRepository).save(any(TransactionRequest.class));

        verify(transactionRepository, never())
                .save(any(Transaction.class));
    }

    // Test 3: Reusing idempotency key for another request should throw exception
    @Test
    void shouldRejectWhenIdempotencyKeyIsReusedForDifferentRequest() {

        TransactionRequestDTO request = new TransactionRequestDTO(
                "TXN-3003",
                "REQ-3003",
                "IDEMP-EXISTING",
                "ACC-101",
                TransactionType.CREDIT,
                new BigDecimal("100"),
                "INR",
                3L,
                LocalDateTime.now(),
                null
        );

        TransactionRequest existingRequest = TransactionRequest.builder()
                .requestId("REQ-OLD")
                .transactionId("TXN-OLD")
                .idempotencyKey("IDEMP-EXISTING")
                .status(TransactionStatus.PROCESSED)
                .build();

        when(accountRepository.existsById("ACC-101"))
                .thenReturn(true);

        when(requestRepository.findByIdempotencyKey("IDEMP-EXISTING"))
                .thenReturn(Optional.of(existingRequest));

        assertThrows(
                IllegalArgumentException.class,
                () -> transactionService.submitTransaction(request)
        );

        verify(transactionRepository, never())
                .save(any(Transaction.class));

        verify(requestRepository, never())
                .save(any(TransactionRequest.class));
    }

    // ==========================================
    // ACCOUNT SERVICE TESTS
    // ==========================================

    // Test 4: Create account successfully
    @Test
    void shouldCreateAccountSuccessfully() {

        when(accountRepository.existsById("ACC-101"))
                .thenReturn(false);

        when(accountRepository.save(any(Account.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Account account = accountService.createAccount("ACC-101");

        assertEquals("ACC-101", account.getAccountId());

        assertEquals(
                0,
                BigDecimal.ZERO.compareTo(account.getBalance())
        );

        assertEquals(0L, account.getLastProcessedSequence());

        verify(accountRepository).save(any(Account.class));
    }

    // Test 5: Reject duplicate account creation
    @Test
    void shouldThrowExceptionWhenAccountAlreadyExists() {

        when(accountRepository.existsById("ACC-101"))
                .thenReturn(true);

        assertThrows(
                IllegalArgumentException.class,
                () -> accountService.createAccount("ACC-101")
        );

        verify(accountRepository, never())
                .save(any(Account.class));
    }

    // Test 6: Retrieve existing account
    @Test
    void shouldReturnExistingAccount() {

        Account account = Account.builder()
                .accountId("ACC-101")
                .balance(new BigDecimal("500"))
                .lastProcessedSequence(2L)
                .build();

        when(accountRepository.findById("ACC-101"))
                .thenReturn(Optional.of(account));

        Account result = accountService.getAccount("ACC-101");

        assertNotNull(result);
        assertEquals("ACC-101", result.getAccountId());

        assertEquals(
                0,
                new BigDecimal("500").compareTo(result.getBalance())
        );

        assertEquals(2L, result.getLastProcessedSequence());
    }

    // Test 7: Account not found
    @Test
    void shouldThrowExceptionWhenAccountDoesNotExist() {

        when(accountRepository.findById("ACC-999"))
                .thenReturn(Optional.empty());

        assertThrows(
                IllegalArgumentException.class,
                () -> accountService.getAccount("ACC-999")
        );
    }
}
