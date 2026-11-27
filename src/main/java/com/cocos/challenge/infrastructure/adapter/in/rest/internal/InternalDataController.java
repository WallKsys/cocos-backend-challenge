package com.cocos.challenge.infrastructure.adapter.in.rest.internal;

import com.cocos.challenge.application.port.in.InternalToolsUseCase;
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

    private final InternalToolsUseCase internalTools;

    public InternalDataController(InternalToolsUseCase internalTools) {
        this.internalTools = internalTools;
    }

    @PostMapping("/api/internal/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reset() {
        internalTools.restoreSeedData();
    }

    @PostMapping("/api/internal/users")
    @ResponseStatus(HttpStatus.CREATED)
    public UserView createUser(@Valid @RequestBody CreateUserBody body) {
        return UserView.from(internalTools.createUser(body.email()));
    }
}
