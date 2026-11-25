package com.cocos.challenge.application.port.out;

import com.cocos.challenge.domain.model.CreatedUser;

public interface UserWriter {

    CreatedUser create(String email);
}
