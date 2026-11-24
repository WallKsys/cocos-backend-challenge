package com.cocos.challenge.domain.model;

import java.util.List;

public record Portfolio(Money totalValue, CashBalance cash, List<Position> positions, List<PortfolioWarning> warnings) {

    public static Portfolio from(CashBalance cash, List<Position> positions) {
        throw new UnsupportedOperationException();
    }
}
