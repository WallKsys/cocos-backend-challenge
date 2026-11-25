package com.cocos.challenge.application.port.in;

import com.cocos.challenge.domain.model.CreatedUser;

public interface CreateUserUseCase {

    CreatedUser create(String email);
}
