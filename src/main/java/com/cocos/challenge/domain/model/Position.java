package com.cocos.challenge.domain.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public record Position(InstrumentId instrumentId, String ticker, String name, long quantity, long reservedQuantity,
                       Optional<Money> lastPrice, Optional<Money> marketValue, Optional<Money> averageCost,
                       Optional<Percentage> totalReturnPct, Optional<Percentage> dailyChangePct,
                       List<PortfolioWarning> warnings) {

    public static Position from(InstrumentId instrumentId, String ticker, String name, long quantity,
                                long reservedQuantity, Optional<MarketQuote> quote, Optional<BigDecimal> average) {
        throw new UnsupportedOperationException();
    }
}
