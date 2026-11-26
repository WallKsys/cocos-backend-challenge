package com.cocos.challenge.application.service;

import com.cocos.challenge.application.port.out.AccountRepository;
import com.cocos.challenge.application.port.out.LedgerReader;
import com.cocos.challenge.application.port.out.MarketDataRepository;
import com.cocos.challenge.application.port.out.PortfolioReader;
import com.cocos.challenge.domain.exception.NotFoundException;
import com.cocos.challenge.domain.model.UserId;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Tag("unit")
class GetPortfolioServiceTest {

    @Test
    @Tag("CU-01.08")
    void rejectsUnknownUserBeforeReadingLedger() {
        UserId userId = new UserId(999);
        AccountRepository accounts = mock(AccountRepository.class);
        LedgerReader ledger = mock(LedgerReader.class);
        PortfolioReader portfolios = mock(PortfolioReader.class);
        MarketDataRepository marketData = mock(MarketDataRepository.class);
        when(accounts.find(userId)).thenReturn(Optional.empty());
        GetPortfolioService service = new GetPortfolioService(accounts, ledger, portfolios, marketData);

        assertThatThrownBy(() -> service.get(userId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("User 999 was not found.");
        verifyNoInteractions(ledger, portfolios, marketData);
    }

    @Test
    void readsAllPortfolioDataInOneSnapshot() throws NoSuchMethodException {
        Transactional transaction = GetPortfolioService.class.getMethod("get", UserId.class)
                .getAnnotation(Transactional.class);

        assertThat(transaction).isNotNull();
        assertThat(transaction.readOnly()).isTrue();
        assertThat(transaction.isolation()).isEqualTo(Isolation.REPEATABLE_READ);
    }
}
