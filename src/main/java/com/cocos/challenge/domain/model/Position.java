package com.cocos.challenge.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public record Position(InstrumentId instrumentId, String ticker, String name, long quantity, long reservedQuantity,
                       Optional<Money> lastPrice, Optional<Money> marketValue, Optional<Money> averageCost,
                       Optional<Percentage> totalReturnPct, Optional<Percentage> dailyChangePct,
                       List<PortfolioWarning> warnings) {

    public Position {
        warnings = List.copyOf(warnings);
    }

    public static Position from(InstrumentId instrumentId, String ticker, String name, long quantity,
                                long reservedQuantity, Optional<MarketQuote> quote, Optional<BigDecimal> average) {
        Optional<Money> lastPrice = quote.map(MarketQuote::close);
        Optional<Money> marketValue = lastPrice.map(price -> price.times(BigDecimal.valueOf(quantity)));
        Optional<Percentage> totalReturn = lastPrice.flatMap(price -> average
                .filter(cost -> cost.signum() != 0)
                .map(cost -> totalReturn(price, cost)));
        List<PortfolioWarning> warnings = new ArrayList<>();
        if (quantity < 0) {
            warnings.add(new PortfolioWarning(PortfolioWarningCode.NEGATIVE_POSITION,
                    "Inconsistent position: the quantity of " + ticker + " is negative (" + quantity + "); "
                            + "the recorded orders sell more shares than were bought."));
        }
        if (quote.isEmpty()) {
            warnings.add(new PortfolioWarning(PortfolioWarningCode.MISSING_MARKET_DATA,
                    "No market data is available for %s; its price, value and returns are unknown.".formatted(ticker)));
        }
        return new Position(instrumentId, ticker, name, quantity, reservedQuantity, lastPrice, marketValue,
                average.map(Money::of), totalReturn, quote.flatMap(MarketQuote::dailyChange), warnings);
    }

    private static Percentage totalReturn(Money close, BigDecimal average) {
        BigDecimal change = close.amount().subtract(average).movePointRight(2);
        return new Percentage(change.divide(average, 2, RoundingMode.HALF_UP));
    }
}
