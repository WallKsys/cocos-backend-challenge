package com.cocos.challenge.infrastructure.adapter.in.rest.internal;

import com.cocos.challenge.application.port.in.UpdateMarketDataUseCase;
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

    private final UpdateMarketDataUseCase marketData;

    public InternalMarketDataController(UpdateMarketDataUseCase marketData) {
        this.marketData = marketData;
    }

    @PutMapping("/api/internal/instruments/{instrumentId}/market-data")
    public MarketDataView update(
            @PathVariable @Positive long instrumentId,
            @Valid @RequestBody MarketDataUpdateBody body) {
        Money close = Money.of(ApiDecimals.parse(body.close()));
        Money previousClose = body.previousClose() == null ? null : Money.of(ApiDecimals.parse(body.previousClose()));
        return MarketDataView.from(marketData.update(new InstrumentId(instrumentId), close, previousClose));
    }
}
