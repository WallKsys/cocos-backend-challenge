package com.cocos.challenge.infrastructure.adapter.in.rest.internal;

import com.cocos.challenge.application.port.in.CreateUserUseCase;
import com.cocos.challenge.application.port.in.RestoreSeedDataUseCase;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(name = "cocos.internal-tools.enabled", havingValue = "true")
public class InternalDataController {

    private final RestoreSeedDataUseCase restoreSeedData;
    private final CreateUserUseCase createUser;

    public InternalDataController(RestoreSeedDataUseCase restoreSeedData, CreateUserUseCase createUser) {
        this.restoreSeedData = restoreSeedData;
        this.createUser = createUser;
    }

    @PostMapping("/api/internal/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reset() {
        restoreSeedData.restore();
    }

    @PostMapping("/api/internal/users")
    @ResponseStatus(HttpStatus.CREATED)
    public UserView createUser(@Valid @RequestBody CreateUserBody body) {
        return UserView.from(createUser.create(body.email()));
    }
}
