package com.cocos.challenge.domain.model;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public record Portfolio(Money totalValue, CashBalance cash, List<Position> positions, List<PortfolioWarning> warnings) {

    public Portfolio {
        positions = List.copyOf(positions);
        warnings = List.copyOf(warnings);
    }

    public static Portfolio from(CashBalance cash, List<Position> positions) {
        List<Position> openPositions = positions.stream()
                .filter(position -> position.quantity() != 0)
                .sorted((left, right) -> Arrays.compareUnsigned(left.ticker().getBytes(StandardCharsets.UTF_8),
                        right.ticker().getBytes(StandardCharsets.UTF_8)))
                .toList();
        Money total = cash.cash();
        List<String> unpricedTickers = new ArrayList<>();
        for (Position position : openPositions) {
            if (position.marketValue().isPresent()) {
                total = total.plus(position.marketValue().get());
            } else {
                unpricedTickers.add(position.ticker());
            }
        }
        List<PortfolioWarning> warnings = new ArrayList<>();
        if (!unpricedTickers.isEmpty()) {
            warnings.add(new PortfolioWarning(PortfolioWarningCode.TOTAL_EXCLUDES_UNPRICED,
                    "The total value excludes positions without market data: " + String.join(", ", unpricedTickers) + "."));
        }
        if (cash.cash().isNegative()) {
            warnings.add(new PortfolioWarning(PortfolioWarningCode.NEGATIVE_CASH,
                    "Inconsistent account: the cash balance is negative (ARS " + cash.cash().amount().toPlainString() + ")."));
        }
        return new Portfolio(total, cash, openPositions, warnings);
    }

    public record PortfolioWarning(PortfolioWarningCode code, String message) {
    }

    public enum PortfolioWarningCode {
        NEGATIVE_POSITION,
        MISSING_MARKET_DATA,
        TOTAL_EXCLUDES_UNPRICED,
        NEGATIVE_CASH
    }
}
