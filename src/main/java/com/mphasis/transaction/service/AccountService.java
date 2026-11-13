
package com.mphasis.transaction.service;

import com.mphasis.transaction.entity.Account;

public interface AccountService {

    // Create a new account
    Account createAccount(String accountId);

    // Get account details
    Account getAccount(String accountId);
}
