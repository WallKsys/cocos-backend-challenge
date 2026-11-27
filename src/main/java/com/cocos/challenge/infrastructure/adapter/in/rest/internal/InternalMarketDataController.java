package com.cocos.challenge.infrastructure.adapter.in.rest.internal;

import com.cocos.challenge.application.port.in.InternalToolsUseCase;
import com.cocos.challenge.domain.model.InstrumentId;
import com.cocos.challenge.domain.model.Money;
import com.cocos.challenge.infrastructure.adapter.in.rest.format.ApiDecimals;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(name = "cocos.internal-tools.enabled", havingValue = "true")
public class InternalMarketDataController {

    private final InternalToolsUseCase internalTools;

    public InternalMarketDataController(InternalToolsUseCase internalTools) {
        this.internalTools = internalTools;
    }

    @PutMapping("/api/internal/instruments/{instrumentId}/market-data")
    public MarketDataView update(
            @PathVariable @Positive long instrumentId,
            @Valid @RequestBody MarketDataUpdateBody body) {
        Money close = Money.of(ApiDecimals.parse(body.close()));
        Money previousClose = body.previousClose() == null ? null : Money.of(ApiDecimals.parse(body.previousClose()));
        return MarketDataView.from(internalTools.updateMarketData(new InstrumentId(instrumentId), close, previousClose));
    }
}
