
package com.mphasis.transaction.service;

import com.mphasis.transaction.dto.request.TransactionRequestDTO;
import com.mphasis.transaction.dto.response.TransactionResponseDTO;

public interface TransactionService {

    // Accept and store an incoming transaction
    TransactionResponseDTO submitTransaction(
            TransactionRequestDTO request
    );

    // Retrieve transaction details
    TransactionResponseDTO getTransaction(String transactionId);
}
