package com.cocos.challenge.application.port.out;

import com.cocos.challenge.domain.model.Account;
import com.cocos.challenge.domain.model.UserId;

import java.util.Optional;

public interface AccountReader {

    Optional<Account> find(UserId userId);
}
