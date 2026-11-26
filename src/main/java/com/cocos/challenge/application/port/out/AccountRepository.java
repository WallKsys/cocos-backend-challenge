package com.cocos.challenge.application.port.out;

import com.cocos.challenge.domain.model.Account;
import com.cocos.challenge.domain.model.CreatedUser;
import com.cocos.challenge.domain.model.UserId;

import java.util.Optional;

/** The users table. */
public interface AccountRepository {

    /** Reads the user row with SELECT ... FOR UPDATE: the lock lasts until the transaction ends (TEC-16). */
    Optional<Account> lock(UserId userId);

    Optional<Account> find(UserId userId);

    CreatedUser create(String email);
}
