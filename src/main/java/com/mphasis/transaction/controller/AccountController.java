
package com.mphasis.transaction.controller;

import com.mphasis.transaction.entity.Account;
import com.mphasis.transaction.service.AccountService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
@Tag(
        name = "Account APIs",
        description = "APIs for creating accounts and retrieving account balances"
)
public class AccountController {

    private final AccountService accountService;

    // Create new account
    @PostMapping
    @Operation(summary = "Create a new account")
    public ResponseEntity<Account> createAccount(
            @RequestParam String accountId) {

        Account account = accountService.createAccount(accountId);

        return ResponseEntity.status(HttpStatus.CREATED).body(account);
    }

    // Get account details and balance
    @GetMapping("/{accountId}")
    @Operation(summary = "Get account details and balance")
    public ResponseEntity<Account> getAccount(
            @PathVariable String accountId) {

        return ResponseEntity.ok(
                accountService.getAccount(accountId));
    }
}
