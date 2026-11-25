package com.cocos.challenge.application.service;

import com.cocos.challenge.application.port.in.CreateUserUseCase;
import com.cocos.challenge.application.port.out.UserWriter;
import com.cocos.challenge.domain.model.CreatedUser;
import org.springframework.transaction.annotation.Transactional;

public class CreateUserService implements CreateUserUseCase {

    private final UserWriter userWriter;

    public CreateUserService(UserWriter userWriter) {
        this.userWriter = userWriter;
    }

    @Override
    @Transactional
    public CreatedUser create(String email) {
        return userWriter.create(email);
    }
}
