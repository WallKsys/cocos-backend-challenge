package com.cocos.challenge.infrastructure.adapter.in.rest.internal;

import com.cocos.challenge.application.port.in.InternalToolsUseCase;
import com.cocos.challenge.infrastructure.adapter.in.rest.order.OrderRecordResponse;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(name = "cocos.internal-tools.enabled", havingValue = "true")
public class InternalOrderToolsController {

    private final InternalToolsUseCase internalTools;

    public InternalOrderToolsController(InternalToolsUseCase internalTools) {
        this.internalTools = internalTools;
    }

    @DeleteMapping("/api/internal/orders/{orderId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable @Positive long orderId) {
        internalTools.deleteOrder(orderId);
    }

    @PostMapping("/api/internal/orders/{orderId}/execute")
    public OrderRecordResponse execute(@PathVariable @Positive long orderId) {
        return OrderRecordResponse.from(internalTools.executeOrder(orderId));
    }
}
