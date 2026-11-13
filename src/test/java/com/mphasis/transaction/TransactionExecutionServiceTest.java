
package com.mphasis.transaction;

import com.mphasis.transaction.entity.Account;
import com.mphasis.transaction.entity.Transaction;
import com.mphasis.transaction.enums.TransactionStatus;
import com.mphasis.transaction.enums.TransactionType;
import com.mphasis.transaction.repository.AccountRepository;
import com.mphasis.transaction.repository.TransactionRepository;
import com.mphasis.transaction.repository.TransactionRequestRepository;
import com.mphasis.transaction.service.TransactionExecutionService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionExecutionServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private TransactionRequestRepository requestRepository;

    @InjectMocks
    private TransactionExecutionService executionService;

    // ==========================================
    // NORMAL TRANSACTION TESTS
    // ==========================================

    // Test 1: Successful CREDIT transaction
    @Test
    void shouldProcessCreditTransactionSuccessfully() {

        Account account = Account.builder()
                .accountId("ACC-101")
                .balance(new BigDecimal("100"))
                .lastProcessedSequence(0L)
                .build();

        Transaction transaction = Transaction.builder()
                .transactionId("TXN-2001")
                .accountId("ACC-101")
                .transactionType(TransactionType.CREDIT)
                .amount(new BigDecimal("50"))
                .currency("INR")
                .sequenceNumber(1L)
                .status(TransactionStatus.PROCESSING)
                .retryCount(0)
                .build();

        when(transactionRepository.findById("TXN-2001"))
                .thenReturn(Optional.of(transaction));

        when(accountRepository.findByAccountIdForUpdate("ACC-101"))
                .thenReturn(Optional.of(account));

        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(requestRepository.findAllByTransactionId("TXN-2001"))
                .thenReturn(List.of());

        executionService.processOne("TXN-2001");

        assertEquals(0, new BigDecimal("150").compareTo(account.getBalance()));
        assertEquals(1L, account.getLastProcessedSequence());
        assertEquals(TransactionStatus.PROCESSED, transaction.getStatus());

        verify(accountRepository).save(account);
        verify(transactionRepository).save(transaction);
    }

    // Test 2: Successful DEBIT transaction
    @Test
    void shouldProcessDebitTransactionSuccessfully() {

        Account account = Account.builder()
                .accountId("ACC-101")
                .balance(new BigDecimal("100"))
                .lastProcessedSequence(0L)
                .build();

        Transaction transaction = Transaction.builder()
                .transactionId("TXN-2002")
                .accountId("ACC-101")
                .transactionType(TransactionType.DEBIT)
                .amount(new BigDecimal("30"))
                .currency("INR")
                .sequenceNumber(1L)
                .status(TransactionStatus.PROCESSING)
                .retryCount(0)
                .build();

        when(transactionRepository.findById("TXN-2002"))
                .thenReturn(Optional.of(transaction));

        when(accountRepository.findByAccountIdForUpdate("ACC-101"))
                .thenReturn(Optional.of(account));

        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(requestRepository.findAllByTransactionId("TXN-2002"))
                .thenReturn(List.of());

        executionService.processOne("TXN-2002");

        assertEquals(0, new BigDecimal("70").compareTo(account.getBalance()));
        assertEquals(1L, account.getLastProcessedSequence());
        assertEquals(TransactionStatus.PROCESSED, transaction.getStatus());

        verify(accountRepository).save(account);
        verify(transactionRepository).save(transaction);
    }

    // Test 3: Insufficient account balance
    @Test
    void shouldFailDebitTransactionWhenBalanceIsInsufficient() {

        Account account = Account.builder()
                .accountId("ACC-101")
                .balance(new BigDecimal("100"))
                .lastProcessedSequence(0L)
                .build();

        Transaction transaction = Transaction.builder()
                .transactionId("TXN-2003")
                .accountId("ACC-101")
                .transactionType(TransactionType.DEBIT)
                .amount(new BigDecimal("150"))
                .currency("INR")
                .sequenceNumber(1L)
                .status(TransactionStatus.PROCESSING)
                .retryCount(0)
                .build();

        when(transactionRepository.findById("TXN-2003"))
                .thenReturn(Optional.of(transaction));

        when(accountRepository.findByAccountIdForUpdate("ACC-101"))
                .thenReturn(Optional.of(account));

        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(requestRepository.findAllByTransactionId("TXN-2003"))
                .thenReturn(List.of());

        executionService.processOne("TXN-2003");

        assertEquals(TransactionStatus.FAILED, transaction.getStatus());
        assertEquals(0, new BigDecimal("100").compareTo(account.getBalance()));

        // Permanently failed transaction consumes its sequence.
        assertEquals(1L, account.getLastProcessedSequence());

        verify(accountRepository).save(account);
        verify(transactionRepository).save(transaction);
    }

    // Test 4: Out-of-order transaction
    @Test
    void shouldKeepTransactionPendingWhenSequenceIsOutOfOrder() {

        Account account = Account.builder()
                .accountId("ACC-101")
                .balance(new BigDecimal("100"))
                .lastProcessedSequence(0L)
                .build();

        Transaction transaction = Transaction.builder()
                .transactionId("TXN-2004")
                .accountId("ACC-101")
                .transactionType(TransactionType.CREDIT)
                .amount(new BigDecimal("50"))
                .currency("INR")
                .sequenceNumber(2L)
                .status(TransactionStatus.PROCESSING)
                .retryCount(0)
                .build();

        when(transactionRepository.findById("TXN-2004"))
                .thenReturn(Optional.of(transaction));

        when(accountRepository.findByAccountIdForUpdate("ACC-101"))
                .thenReturn(Optional.of(account));

        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(requestRepository.findAllByTransactionId("TXN-2004"))
                .thenReturn(List.of());

        executionService.processOne("TXN-2004");

        assertEquals(TransactionStatus.PENDING, transaction.getStatus());
        assertEquals(0, new BigDecimal("100").compareTo(account.getBalance()));
        assertEquals(0L, account.getLastProcessedSequence());

        verify(transactionRepository).save(transaction);
        verify(accountRepository, never()).save(any(Account.class));
    }

    // Test 5: Transient failure should schedule a retry
    @Test
    void shouldMarkTransactionForRetryAfterTransientFailure() {

        Transaction transaction = Transaction.builder()
                .transactionId("TXN-2005")
                .accountId("ACC-101")
                .transactionType(TransactionType.CREDIT)
                .amount(new BigDecimal("500"))
                .currency("INR")
                .sequenceNumber(1L)
                .status(TransactionStatus.PROCESSING)
                .retryCount(0)
                .build();

        when(transactionRepository.findById("TXN-2005"))
                .thenReturn(Optional.of(transaction));

        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(requestRepository.findAllByTransactionId("TXN-2005"))
                .thenReturn(List.of());

        executionService.handleFailure(
                "TXN-2005",
                "Temporary database connection failure"
        );

        assertEquals(1, transaction.getRetryCount());
        assertEquals(TransactionStatus.RETRY_PENDING, transaction.getStatus());
        assertEquals(
                "Temporary database connection failure",
                transaction.getLastError()
        );

        verify(transactionRepository).save(transaction);
    }

    // Test 6: Transaction should fail after maximum retries
    @Test
    void shouldMarkTransactionFailedAfterMaximumRetries() {

        Transaction transaction = Transaction.builder()
                .transactionId("TXN-2006")
                .accountId("ACC-101")
                .transactionType(TransactionType.CREDIT)
                .amount(new BigDecimal("500"))
                .currency("INR")
                .sequenceNumber(1L)
                .status(TransactionStatus.RETRY_PENDING)
                .retryCount(2)
                .build();

        when(transactionRepository.findById("TXN-2006"))
                .thenReturn(Optional.of(transaction));

        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(requestRepository.findAllByTransactionId("TXN-2006"))
                .thenReturn(List.of());

        executionService.handleFailure(
                "TXN-2006",
                "Temporary database connection failure"
        );

        assertEquals(3, transaction.getRetryCount());
        assertEquals(TransactionStatus.FAILED, transaction.getStatus());

        verify(transactionRepository).save(transaction);
    }

    // ==========================================
    // REVERSAL TRANSACTION TESTS
    // ==========================================

    // Test 7: Successfully reverse a CREDIT transaction
    @Test
    void shouldReverseCreditTransactionSuccessfully() {

        Account account = Account.builder()
                .accountId("ACC-101")
                .balance(new BigDecimal("150"))
                .lastProcessedSequence(1L)
                .build();

        Transaction original = Transaction.builder()
                .transactionId("TXN-ORIGINAL")
                .accountId("ACC-101")
                .transactionType(TransactionType.CREDIT)
                .amount(new BigDecimal("50"))
                .currency("INR")
                .status(TransactionStatus.PROCESSED)
                .build();

        Transaction reversal = Transaction.builder()
                .transactionId("TXN-REV-1")
                .accountId("ACC-101")
                .transactionType(TransactionType.REVERSAL)
                .originalTransactionId("TXN-ORIGINAL")
                .amount(new BigDecimal("50"))
                .currency("INR")
                .sequenceNumber(2L)
                .status(TransactionStatus.PROCESSING)
                .retryCount(0)
                .build();

        when(transactionRepository.findById("TXN-REV-1"))
                .thenReturn(Optional.of(reversal));

        when(transactionRepository.findById("TXN-ORIGINAL"))
                .thenReturn(Optional.of(original));

        when(accountRepository.findByAccountIdForUpdate("ACC-101"))
                .thenReturn(Optional.of(account));

        when(transactionRepository.existsByOriginalTransactionId("TXN-ORIGINAL"))
                .thenReturn(false);

        when(requestRepository.findAllByTransactionId("TXN-REV-1"))
                .thenReturn(List.of());

        executionService.processOne("TXN-REV-1");

        assertEquals(0, new BigDecimal("100").compareTo(account.getBalance()));
        assertEquals(2L, account.getLastProcessedSequence());
        assertEquals(TransactionStatus.PROCESSED, reversal.getStatus());

        verify(accountRepository).save(account);
        verify(transactionRepository).save(reversal);
    }

    // Test 8: Successfully reverse a DEBIT transaction
    @Test
    void shouldReverseDebitTransactionSuccessfully() {

        Account account = Account.builder()
                .accountId("ACC-101")
                .balance(new BigDecimal("70"))
                .lastProcessedSequence(1L)
                .build();

        Transaction original = Transaction.builder()
                .transactionId("TXN-ORIGINAL-2")
                .accountId("ACC-101")
                .transactionType(TransactionType.DEBIT)
                .amount(new BigDecimal("30"))
                .currency("INR")
                .status(TransactionStatus.PROCESSED)
                .build();

        Transaction reversal = Transaction.builder()
                .transactionId("TXN-REV-2")
                .accountId("ACC-101")
                .transactionType(TransactionType.REVERSAL)
                .originalTransactionId("TXN-ORIGINAL-2")
                .amount(new BigDecimal("30"))
                .currency("INR")
                .sequenceNumber(2L)
                .status(TransactionStatus.PROCESSING)
                .retryCount(0)
                .build();

        when(transactionRepository.findById("TXN-REV-2"))
                .thenReturn(Optional.of(reversal));

        when(transactionRepository.findById("TXN-ORIGINAL-2"))
                .thenReturn(Optional.of(original));

        when(accountRepository.findByAccountIdForUpdate("ACC-101"))
                .thenReturn(Optional.of(account));

        when(transactionRepository.existsByOriginalTransactionId("TXN-ORIGINAL-2"))
                .thenReturn(false);

        when(requestRepository.findAllByTransactionId("TXN-REV-2"))
                .thenReturn(List.of());

        executionService.processOne("TXN-REV-2");

        assertEquals(0, new BigDecimal("100").compareTo(account.getBalance()));
        assertEquals(2L, account.getLastProcessedSequence());
        assertEquals(TransactionStatus.PROCESSED, reversal.getStatus());

        verify(accountRepository).save(account);
        verify(transactionRepository).save(reversal);
    }

    // Test 9: Reject reversal when original transaction does not exist
    @Test
    void shouldRejectReversalWhenOriginalTransactionDoesNotExist() {

        Account account = Account.builder()
                .accountId("ACC-101")
                .balance(new BigDecimal("100"))
                .lastProcessedSequence(0L)
                .build();

        Transaction reversal = Transaction.builder()
                .transactionId("TXN-REV-3")
                .accountId("ACC-101")
                .transactionType(TransactionType.REVERSAL)
                .originalTransactionId("TXN-MISSING")
                .amount(new BigDecimal("50"))
                .currency("INR")
                .sequenceNumber(1L)
                .status(TransactionStatus.PROCESSING)
                .retryCount(0)
                .build();

        when(transactionRepository.findById("TXN-REV-3"))
                .thenReturn(Optional.of(reversal));

        when(transactionRepository.findById("TXN-MISSING"))
                .thenReturn(Optional.empty());

        when(accountRepository.findByAccountIdForUpdate("ACC-101"))
                .thenReturn(Optional.of(account));

        when(requestRepository.findAllByTransactionId("TXN-REV-3"))
                .thenReturn(List.of());

        executionService.processOne("TXN-REV-3");

        assertEquals(TransactionStatus.FAILED, reversal.getStatus());
        assertEquals(0, new BigDecimal("100").compareTo(account.getBalance()));

        // Invalid permanent reversal consumes its sequence.
        assertEquals(1L, account.getLastProcessedSequence());

        verify(accountRepository).save(account);
        verify(transactionRepository).save(reversal);
    }

    // Test 10: Reject reversal when original transaction is not processed
    @Test
    void shouldRejectReversalWhenOriginalTransactionIsNotProcessed() {

        Account account = Account.builder()
                .accountId("ACC-101")
                .balance(new BigDecimal("100"))
                .lastProcessedSequence(0L)
                .build();

        Transaction original = Transaction.builder()
                .transactionId("TXN-ORIGINAL-3")
                .accountId("ACC-101")
                .transactionType(TransactionType.CREDIT)
                .amount(new BigDecimal("50"))
                .currency("INR")
                .status(TransactionStatus.RECEIVED)
                .build();

        Transaction reversal = Transaction.builder()
                .transactionId("TXN-REV-4")
                .accountId("ACC-101")
                .transactionType(TransactionType.REVERSAL)
                .originalTransactionId("TXN-ORIGINAL-3")
                .amount(new BigDecimal("50"))
                .currency("INR")
                .sequenceNumber(1L)
                .status(TransactionStatus.PROCESSING)
                .retryCount(0)
                .build();

        when(transactionRepository.findById("TXN-REV-4"))
                .thenReturn(Optional.of(reversal));

        when(transactionRepository.findById("TXN-ORIGINAL-3"))
                .thenReturn(Optional.of(original));

        when(accountRepository.findByAccountIdForUpdate("ACC-101"))
                .thenReturn(Optional.of(account));

        when(requestRepository.findAllByTransactionId("TXN-REV-4"))
                .thenReturn(List.of());

        executionService.processOne("TXN-REV-4");

        assertEquals(TransactionStatus.FAILED, reversal.getStatus());
        assertEquals(0, new BigDecimal("100").compareTo(account.getBalance()));

        // Invalid permanent reversal consumes its sequence.
        assertEquals(1L, account.getLastProcessedSequence());

        verify(accountRepository).save(account);
        verify(transactionRepository).save(reversal);
    }

    // Test 11: Reject duplicate reversal
    @Test
    void shouldRejectDuplicateReversal() {

        Account account = Account.builder()
                .accountId("ACC-101")
                .balance(new BigDecimal("100"))
                .lastProcessedSequence(1L)
                .build();

        Transaction original = Transaction.builder()
                .transactionId("TXN-ORIGINAL-4")
                .accountId("ACC-101")
                .transactionType(TransactionType.CREDIT)
                .amount(new BigDecimal("50"))
                .currency("INR")
                .status(TransactionStatus.PROCESSED)
                .build();

        Transaction reversal = Transaction.builder()
                .transactionId("TXN-REV-5")
                .accountId("ACC-101")
                .transactionType(TransactionType.REVERSAL)
                .originalTransactionId("TXN-ORIGINAL-4")
                .amount(new BigDecimal("50"))
                .currency("INR")
                .sequenceNumber(2L)
                .status(TransactionStatus.PROCESSING)
                .retryCount(0)
                .build();

        when(transactionRepository.findById("TXN-REV-5"))
                .thenReturn(Optional.of(reversal));

        when(transactionRepository.findById("TXN-ORIGINAL-4"))
                .thenReturn(Optional.of(original));

        when(accountRepository.findByAccountIdForUpdate("ACC-101"))
                .thenReturn(Optional.of(account));

        when(transactionRepository.existsByOriginalTransactionId("TXN-ORIGINAL-4"))
                .thenReturn(true);

        when(requestRepository.findAllByTransactionId("TXN-REV-5"))
                .thenReturn(List.of());

        executionService.processOne("TXN-REV-5");

        assertEquals(TransactionStatus.FAILED, reversal.getStatus());
        assertEquals(0, new BigDecimal("100").compareTo(account.getBalance()));

        // Duplicate reversal consumes its sequence.
        assertEquals(2L, account.getLastProcessedSequence());

        verify(accountRepository).save(account);
        verify(transactionRepository).save(reversal);
    }
}
