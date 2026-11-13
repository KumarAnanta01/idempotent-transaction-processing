
package com.mphasis.transaction.service.impl;

import com.mphasis.transaction.entity.Account;
import com.mphasis.transaction.repository.AccountRepository;
import com.mphasis.transaction.service.AccountService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@Transactional
public class AccountServiceImpl implements AccountService {

    private final AccountRepository accountRepository;

    public AccountServiceImpl(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    // Create account with initial zero balance
    @Override
    public Account createAccount(String accountId) {

        if (accountRepository.existsById(accountId)) {
            throw new IllegalArgumentException(
                    "Account already exists: " + accountId);
        }

        Account account = Account.builder()
                .accountId(accountId)
                .balance(BigDecimal.ZERO)
                .lastProcessedSequence(0L)
                .build();

        return accountRepository.save(account);
    }

    // Retrieve account by ID
    @Override
    @Transactional(readOnly = true)
    public Account getAccount(String accountId) {

        return accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Account not found: " + accountId));
    }
}
