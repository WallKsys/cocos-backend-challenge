package com.cocos.challenge.infrastructure.adapter.in.rest.portfolio;

import com.cocos.challenge.domain.model.Portfolio.PortfolioWarning;

record WarningResponse(String code, String message) {

    static WarningResponse from(PortfolioWarning warning) {
        return new WarningResponse(warning.code().name(), warning.message());
    }
}
