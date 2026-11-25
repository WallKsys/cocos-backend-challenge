package com.cocos.challenge.infrastructure.adapter.in.rest.internal;

import com.cocos.challenge.domain.model.CreatedUser;

public record UserView(int userId, String email, String accountNumber) {

    static UserView from(CreatedUser user) {
        return new UserView(Math.toIntExact(user.id().value()), user.email(), user.accountNumber());
    }
}
