package com.cocos.challenge.application.service;

import com.cocos.challenge.application.port.in.CreateUserUseCase;
import com.cocos.challenge.application.port.out.UserWriter;
import com.cocos.challenge.domain.exception.InvalidRequestException;
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
        if (email == null || email.isBlank()) {
            throw new InvalidRequestException("Request validation failed: 'email' must not be blank.");
        }
        return userWriter.create(email);
    }
}
