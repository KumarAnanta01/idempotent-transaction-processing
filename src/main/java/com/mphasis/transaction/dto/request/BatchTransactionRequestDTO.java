
package com.mphasis.transaction.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record BatchTransactionRequestDTO(

        @NotEmpty(message = "Transaction list cannot be empty")
        List<@Valid TransactionRequestDTO> transactions

) {
}
