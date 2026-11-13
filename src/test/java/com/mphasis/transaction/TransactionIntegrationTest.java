
package com.mphasis.transaction;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class TransactionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;


    // Test 1: Complete account and transaction processing flow
    @Test
    void shouldProcessTransactionSuccessfully() throws Exception {

        String accountId = "ACC-IT-" + UUID.randomUUID();
        String transactionId = "TXN-IT-" + UUID.randomUUID();

        mockMvc.perform(post("/api/v1/accounts")
                        .param("accountId", accountId))
                .andExpect(status().is2xxSuccessful());

        String requestBody = """
                {
                    "transactionId": "%s",
                    "requestId": "REQ-%s",
                    "idempotencyKey": "IDEMP-%s",
                    "accountId": "%s",
                    "transactionType": "CREDIT",
                    "amount": 100,
                    "currency": "INR",
                    "sequenceNumber": 1,
                    "eventTime": "2026-11-13T10:00:00"
                }
                """.formatted(
                transactionId, transactionId, transactionId, accountId
        );

        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().is2xxSuccessful());

        mockMvc.perform(post("/api/v1/transactions/process"))
                .andExpect(status().is2xxSuccessful());

        mockMvc.perform(get("/api/v1/transactions/{transactionId}",
                        transactionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSED"));

        mockMvc.perform(get("/api/v1/accounts/{accountId}", accountId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(100));
    }


    // Test 2: Duplicate transaction through REST API
    @Test
    void shouldRejectDuplicateTransactionThroughRestApi() throws Exception {

        String accountId = "ACC-DUP-" + UUID.randomUUID();
        String transactionId = "TXN-DUP-" + UUID.randomUUID();

        mockMvc.perform(post("/api/v1/accounts")
                        .param("accountId", accountId))
                .andExpect(status().is2xxSuccessful());

        String firstRequest = """
                {
                    "transactionId": "%s",
                    "requestId": "REQ-FIRST-%s",
                    "idempotencyKey": "IDEMP-FIRST-%s",
                    "accountId": "%s",
                    "transactionType": "CREDIT",
                    "amount": 100,
                    "currency": "INR",
                    "sequenceNumber": 1,
                    "eventTime": "2026-11-13T10:00:00"
                }
                """.formatted(transactionId, accountId, accountId, accountId);

        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(firstRequest))
                .andExpect(status().is2xxSuccessful());

        String duplicateRequest = """
                {
                    "transactionId": "%s",
                    "requestId": "REQ-SECOND-%s",
                    "idempotencyKey": "IDEMP-SECOND-%s",
                    "accountId": "%s",
                    "transactionType": "CREDIT",
                    "amount": 100,
                    "currency": "INR",
                    "sequenceNumber": 1,
                    "eventTime": "2026-11-13T10:00:00"
                }
                """.formatted(transactionId, accountId, accountId, accountId);

        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(duplicateRequest))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.transactionId").value(transactionId))
                .andExpect(jsonPath("$.status").value("DUPLICATE"));
    }


    // Test 3: Reject transaction with negative amount
    @Test
    void shouldRejectTransactionWithNegativeAmount() throws Exception {

        String requestBody = """
                {
                    "transactionId": "TXN-INVALID-%s",
                    "requestId": "REQ-INVALID-%s",
                    "idempotencyKey": "IDEMP-INVALID-%s",
                    "accountId": "ACC-101",
                    "transactionType": "CREDIT",
                    "amount": -100,
                    "currency": "INR",
                    "sequenceNumber": 1,
                    "eventTime": "2026-11-13T10:00:00"
                }
                """.formatted(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID()
        );

        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }


    // Test 4: Reject transaction when account does not exist
    @Test
    void shouldRejectTransactionForNonExistingAccount() throws Exception {

        String accountId = "ACC-MISSING-" + UUID.randomUUID();

        String requestBody = """
                {
                    "transactionId": "TXN-MISSING-%s",
                    "requestId": "REQ-MISSING-%s",
                    "idempotencyKey": "IDEMP-MISSING-%s",
                    "accountId": "%s",
                    "transactionType": "CREDIT",
                    "amount": 100,
                    "currency": "INR",
                    "sequenceNumber": 1,
                    "eventTime": "2026-11-13T10:00:00"
                }
                """.formatted(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                accountId
        );

        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }


    // Test 5: Return error when transaction does not exist
    @Test
    void shouldReturnBadRequestWhenTransactionDoesNotExist() throws Exception {

        String transactionId = "TXN-NOT-FOUND-" + UUID.randomUUID();

        mockMvc.perform(get("/api/v1/transactions/{transactionId}",
                        transactionId))
                .andExpect(status().isBadRequest());
    }
    // Test 6: Verify transaction summary API
    @Test
    void shouldReturnTransactionSummary() throws Exception {

        mockMvc.perform(get("/api/v1/transactions/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.received").exists())
                .andExpect(jsonPath("$.processing").exists())
                .andExpect(jsonPath("$.pending").exists())
                .andExpect(jsonPath("$.processed").exists())
                .andExpect(jsonPath("$.duplicate").exists())
                .andExpect(jsonPath("$.retryPending").exists())
                .andExpect(jsonPath("$.failed").exists())
                .andExpect(jsonPath("$.total").exists());
    }
}
