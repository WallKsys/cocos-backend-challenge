package com.cocos.challenge.application.service;

import com.cocos.challenge.application.port.in.UpdateMarketDataUseCase;
import com.cocos.challenge.application.port.out.InstrumentRepository;
import com.cocos.challenge.application.port.out.MarketDataWriter;
import com.cocos.challenge.domain.exception.InvalidRequestException;
import com.cocos.challenge.domain.exception.NotFoundException;
import com.cocos.challenge.domain.model.Instrument;
import com.cocos.challenge.domain.model.InstrumentId;
import com.cocos.challenge.domain.model.InstrumentType;
import com.cocos.challenge.domain.model.Money;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Tag("unit")
class UpdateMarketDataServiceTest {

    private static final InstrumentId PAMP_ID = new InstrumentId(47);
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 25);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-25T12:00:00Z"), ZoneOffset.UTC);

    private final InstrumentRepository instruments = mock(InstrumentRepository.class);
    private final MarketDataWriter writer = mock(MarketDataWriter.class);
    private final UpdateMarketDataService service = new UpdateMarketDataService(instruments, writer, CLOCK);

    @Test
    @Tag("CU-I.06")
    void updatesTradableInstrumentUsingUtcDate() {
        when(instruments.findById(PAMP_ID)).thenReturn(Optional.of(new Instrument(PAMP_ID, "PAMP", InstrumentType.ACCIONES)));
        when(writer.upsert(PAMP_ID, Money.of("1000.00"), null, TODAY))
                .thenReturn(new MarketDataWriter.StoredMarketData(TODAY, Money.of("1000.00"), Money.of("921.80")));

        var result = service.update(PAMP_ID, Money.of("1000.00"), null);

        verify(writer).upsert(PAMP_ID, Money.of("1000.00"), null, TODAY);
        assertThat(result).isEqualTo(new UpdateMarketDataUseCase.MarketDataResult(
                PAMP_ID, "PAMP", TODAY, Money.of("1000.00"), Money.of("921.80")));
    }

    @Test
    @Tag("CU-I.06")
    void rejectsUnknownInstrument() {
        when(instruments.findById(new InstrumentId(999))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(new InstrumentId(999), Money.of("10.00"), null))
                .isInstanceOf(NotFoundException.class).hasMessage("Instrument 999 was not found.");
        verifyNoInteractions(writer);
    }

    @Test
    @Tag("CU-I.06")
    void rejectsCurrencyInstrument() {
        InstrumentId ars = new InstrumentId(66);
        when(instruments.findById(ars)).thenReturn(Optional.of(new Instrument(ars, "ARS", InstrumentType.MONEDA)));

        assertThatThrownBy(() -> service.update(ars, Money.of("10.00"), null))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Request validation failed: 'instrumentId' must reference a tradable instrument; ARS has no market data.");
        verifyNoInteractions(writer);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"0.00", "-1.00"})
    @Tag("CU-I.06")
    void rejectsNonPositiveClose(String close) {
        assertThatThrownBy(() -> service.update(PAMP_ID, close == null ? null : Money.of(close), null))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Request validation failed: 'close' must be greater than 0.");
        verifyNoInteractions(instruments, writer);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.00", "-5.00"})
    @Tag("CU-I.06")
    void rejectsNonPositivePreviousClose(String previousClose) {
        assertThatThrownBy(() -> service.update(PAMP_ID, Money.of("10.00"), Money.of(previousClose)))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Request validation failed: 'previousClose' must be greater than 0.");
        verifyNoInteractions(instruments, writer);
    }
}
