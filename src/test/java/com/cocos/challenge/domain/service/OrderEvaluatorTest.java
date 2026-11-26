package com.cocos.challenge.domain.service;

import com.cocos.challenge.domain.model.Account;
import com.cocos.challenge.domain.model.CashBalance;
import com.cocos.challenge.domain.model.Instrument;
import com.cocos.challenge.domain.model.InstrumentId;
import com.cocos.challenge.domain.model.InstrumentType;
import com.cocos.challenge.domain.model.MarketQuote;
import com.cocos.challenge.domain.model.Money;
import com.cocos.challenge.domain.model.OrderRequest;
import com.cocos.challenge.domain.model.OrderStatus;
import com.cocos.challenge.domain.model.RejectionReason;
import com.cocos.challenge.domain.model.ShareBalance;
import com.cocos.challenge.domain.model.UserId;
import com.cocos.challenge.domain.service.OrderEvaluator.OrderEvaluation;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 005 / plan.md § Order evaluation, steps 1, 2, 3 and 5: reference price, quantity, total
 * and final status, with the numbers of the CU-03 scenarios.
 */
@Tag("unit")
class OrderEvaluatorTest {

    private static final Account ACCOUNT = new Account(new UserId(1), "10001");
    private static final Instrument PAMP = new Instrument(new InstrumentId(47), "PAMP", InstrumentType.ACCIONES);
    private static final Instrument PGR = new Instrument(new InstrumentId(3), "PGR", InstrumentType.ACCIONES);
    private static final Instrument BMA = new Instrument(new InstrumentId(31), "BMA", InstrumentType.ACCIONES);
    private static final Instrument GGAL = new Instrument(new InstrumentId(34), "GGAL", InstrumentType.ACCIONES);
    private static final MarketQuote PAMP_QUOTE = new MarketQuote(Money.of("925.85"), Money.of("921.80"));
    private static final CashBalance SEED_CASH = new CashBalance(Money.of("753000.00"), Money.of("125500.00"));
    private static final ShareBalance PAMP_SHARES = new ShareBalance(40, 0);

    private final OrderEvaluator evaluator = new OrderEvaluator();

    @Test
    @Tag("CU-03.01")
    void buyAtMarketByQuantityIsFilledAtTheLastPrice() {
        OrderEvaluation evaluation = evaluate(OrderRequest.of(47L, "BUY", "MARKET", 10, null, null));

        assertThat(evaluation.status()).isEqualTo(OrderStatus.FILLED);
        assertThat(evaluation.quantity()).isEqualTo(10);
        assertThat(evaluation.price()).isEqualTo(Money.of("925.85"));
        assertThat(evaluation.rejection()).isNull();
    }

    @Test
    @Tag("CU-03.02")
    void buyAtMarketByAmountUsesWholeSharesRoundedDown() {
        OrderEvaluation evaluation = evaluate(OrderRequest.of(47L, "BUY", "MARKET", null, "50000.00", null));

        assertThat(evaluation.status()).isEqualTo(OrderStatus.FILLED);
        assertThat(evaluation.quantity()).isEqualTo(54);
        assertThat(evaluation.price()).isEqualTo(Money.of("925.85"));
        assertThat(total(evaluation)).isEqualTo(Money.of("49995.90"));
    }

    @Test
    @Tag("CU-03.05")
    void sellAtMarketByQuantityIsFilledAtTheLastPrice() {
        OrderEvaluation evaluation = evaluate(OrderRequest.of(47L, "SELL", "MARKET", 10, null, null));

        assertThat(evaluation.status()).isEqualTo(OrderStatus.FILLED);
        assertThat(evaluation.quantity()).isEqualTo(10);
        assertThat(evaluation.price()).isEqualTo(Money.of("925.85"));
    }

    @Test
    @Tag("CU-03.10")
    void sellAtMarketByAmountUsesWholeSharesRoundedDown() {
        OrderEvaluation evaluation = evaluate(OrderRequest.of(47L, "SELL", "MARKET", null, "20000.00", null));

        assertThat(evaluation.status()).isEqualTo(OrderStatus.FILLED);
        assertThat(evaluation.quantity()).isEqualTo(21);
        assertThat(total(evaluation)).isEqualTo(Money.of("19442.85"));
    }

    @Test
    @Tag("CU-03.11")
    void buyAmountBelowThePriceOfOneShareIsRejected() {
        OrderEvaluation evaluation = evaluate(OrderRequest.of(47L, "BUY", "MARKET", null, "500.00", null));

        assertThat(evaluation.status()).isEqualTo(OrderStatus.REJECTED);
        assertThat(evaluation.quantity()).isZero();
        assertThat(evaluation.price()).isEqualTo(Money.of("925.85"));
        assertThat(evaluation.rejection().reason()).isEqualTo(RejectionReason.AMOUNT_BELOW_PRICE);
        assertThat(evaluation.rejection().message())
                .isEqualTo("The amount ARS 500.00 is not enough to buy one share of PAMP at ARS 925.85.");
    }

    @Test
    @Tag("CU-03.12")
    void marketOrderWithoutMarketDataIsRejectedWithoutPrice() {
        OrderEvaluation evaluation = evaluate(
                OrderRequest.of(3L, "BUY", "MARKET", 1, null, null), PGR, null, new ShareBalance(0, 0));

        assertThat(evaluation.status()).isEqualTo(OrderStatus.REJECTED);
        assertThat(evaluation.quantity()).isEqualTo(1);
        assertThat(evaluation.price()).isNull();
        assertThat(evaluation.rejection().reason()).isEqualTo(RejectionReason.NO_MARKET_PRICE);
        assertThat(evaluation.rejection().message())
                .isEqualTo("No market price is available for PGR; MARKET orders cannot be executed.");
    }

    @Test
    @Tag("CU-03.13")
    void limitBuyStaysNewAtTheLimitPrice() {
        OrderEvaluation evaluation = evaluate(OrderRequest.of(47L, "BUY", "LIMIT", 100, null, "900.00"));

        assertThat(evaluation.status()).isEqualTo(OrderStatus.NEW);
        assertThat(evaluation.quantity()).isEqualTo(100);
        assertThat(evaluation.price()).isEqualTo(Money.of("900.00"));
        assertThat(total(evaluation)).isEqualTo(Money.of("90000.00"));
    }

    @Test
    @Tag("CU-03.14")
    void limitBuyByAmountUsesTheLimitPriceAndWholeShares() {
        OrderEvaluation evaluation = evaluate(OrderRequest.of(47L, "BUY", "LIMIT", null, "50000.00", "925.85"));

        assertThat(evaluation.status()).isEqualTo(OrderStatus.NEW);
        assertThat(evaluation.quantity()).isEqualTo(54);
        assertThat(evaluation.price()).isEqualTo(Money.of("925.85"));
        assertThat(total(evaluation)).isEqualTo(Money.of("49995.90"));
    }

    @Test
    @Tag("CU-03.15")
    void limitSellStaysNewAtTheLimitPrice() {
        OrderEvaluation evaluation = evaluate(OrderRequest.of(47L, "SELL", "LIMIT", 30, null, "950.00"));

        assertThat(evaluation.status()).isEqualTo(OrderStatus.NEW);
        assertThat(evaluation.quantity()).isEqualTo(30);
        assertThat(evaluation.price()).isEqualTo(Money.of("950.00"));
    }

    @Test
    @Tag("CU-03.17")
    void limitBuyBetterThanTheMarketIsNotExecutedImmediately() {
        OrderEvaluation evaluation = evaluate(OrderRequest.of(47L, "BUY", "LIMIT", 10, null, "1000.00"));

        assertThat(evaluation.status()).isEqualTo(OrderStatus.NEW);
        assertThat(evaluation.price()).isEqualTo(Money.of("1000.00"));
    }

    @Test
    @Tag("CU-03.19")
    void limitOrderDoesNotNeedMarketData() {
        OrderEvaluation evaluation = evaluate(
                OrderRequest.of(3L, "BUY", "LIMIT", 10, null, "50.00"), PGR, null, new ShareBalance(0, 0));

        assertThat(evaluation.status()).isEqualTo(OrderStatus.NEW);
        assertThat(evaluation.price()).isEqualTo(Money.of("50.00"));
        assertThat(total(evaluation)).isEqualTo(Money.of("500.00"));
    }

    @Test
    @Tag("CU-03.38")
    void limitSellByAmountReservesWholeShares() {
        OrderEvaluation evaluation = evaluate(OrderRequest.of(47L, "SELL", "LIMIT", null, "20000.00", "950.00"));

        assertThat(evaluation.status()).isEqualTo(OrderStatus.NEW);
        assertThat(evaluation.quantity()).isEqualTo(21);
        assertThat(total(evaluation)).isEqualTo(Money.of("19950.00"));
    }

    @Test
    @Tag("CU-03.39")
    void sellAmountBelowThePriceOfOneShareIsRejected() {
        OrderEvaluation evaluation = evaluate(OrderRequest.of(47L, "SELL", "MARKET", null, "500.00", null));

        assertThat(evaluation.status()).isEqualTo(OrderStatus.REJECTED);
        assertThat(evaluation.rejection().reason()).isEqualTo(RejectionReason.AMOUNT_BELOW_PRICE);
        assertThat(evaluation.rejection().message())
                .isEqualTo("The amount ARS 500.00 is not enough to sell one share of PAMP at ARS 925.85.");
    }

    @Test
    @Tag("CU-03.40")
    void limitBuyByAmountUsesTheLimitPriceToComputeTheQuantity() {
        OrderEvaluation evaluation = evaluate(OrderRequest.of(47L, "BUY", "LIMIT", null, "800.00", "1000.00"));

        assertThat(evaluation.status()).isEqualTo(OrderStatus.REJECTED);
        assertThat(evaluation.quantity()).isZero();
        assertThat(evaluation.price()).isEqualTo(Money.of("1000.00"));
        assertThat(evaluation.rejection().reason()).isEqualTo(RejectionReason.AMOUNT_BELOW_PRICE);
        assertThat(evaluation.rejection().message())
                .isEqualTo("The amount ARS 800.00 is not enough to buy one share of PAMP at ARS 1000.00.");
    }

    @Test
    @Tag("CU-03.03")
    void buyAboveTheAvailableCashIsRejectedAndKeepsTheComputedValues() {
        OrderEvaluation evaluation = evaluate(OrderRequest.of(47L, "BUY", "MARKET", 1000, null, null));

        assertThat(evaluation.status()).isEqualTo(OrderStatus.REJECTED);
        assertThat(evaluation.quantity()).isEqualTo(1000);
        assertThat(evaluation.price()).isEqualTo(Money.of("925.85"));
        assertThat(evaluation.rejection().reason()).isEqualTo(RejectionReason.INSUFFICIENT_FUNDS);
        assertThat(evaluation.rejection().message()).isEqualTo(
                "Insufficient available cash in account 10001: required ARS 925850.00, available ARS 627500.00.");
    }

    @Test
    @Tag("CU-03.04")
    void reservedCashCannotBeSpent() {
        OrderEvaluation evaluation = evaluate(OrderRequest.of(47L, "BUY", "MARKET", 700, null, null));

        assertThat(evaluation.status()).isEqualTo(OrderStatus.REJECTED);
        assertThat(evaluation.rejection().reason()).isEqualTo(RejectionReason.INSUFFICIENT_FUNDS);
        assertThat(evaluation.rejection().message()).isEqualTo(
                "Insufficient available cash in account 10001: required ARS 648095.00, available ARS 627500.00.");
    }

    @Test
    @Tag("CU-03.06")
    void sellAboveTheAvailableSharesIsRejected() {
        OrderEvaluation evaluation = evaluate(OrderRequest.of(47L, "SELL", "MARKET", 41, null, null));

        assertThat(evaluation.status()).isEqualTo(OrderStatus.REJECTED);
        assertThat(evaluation.rejection().reason()).isEqualTo(RejectionReason.INSUFFICIENT_SHARES);
        assertThat(evaluation.rejection().message()).isEqualTo(
                "Insufficient available shares of PAMP in account 10001: required 41, available 40.");
    }

    @Test
    @Tag("CU-03.07")
    void sellingAnInstrumentThatIsNotHeldIsRejected() {
        OrderEvaluation evaluation = evaluate(
                OrderRequest.of(34L, "SELL", "MARKET", 1, null, null), GGAL, PAMP_QUOTE, new ShareBalance(0, 0));

        assertThat(evaluation.status()).isEqualTo(OrderStatus.REJECTED);
        assertThat(evaluation.rejection().reason()).isEqualTo(RejectionReason.INSUFFICIENT_SHARES);
        assertThat(evaluation.rejection().message()).isEqualTo(
                "Insufficient available shares of GGAL in account 10001: required 1, available 0.");
    }

    @Test
    @Tag("CU-03.08")
    void sellingFromTheNegativeSeedPositionIsRejected() {
        OrderEvaluation evaluation = evaluate(
                OrderRequest.of(31L, "SELL", "MARKET", 1, null, null), BMA, PAMP_QUOTE, new ShareBalance(-10, 0));

        assertThat(evaluation.status()).isEqualTo(OrderStatus.REJECTED);
        assertThat(evaluation.rejection().reason()).isEqualTo(RejectionReason.INSUFFICIENT_SHARES);
        assertThat(evaluation.rejection().message()).isEqualTo(
                "Insufficient available shares of BMA in account 10001: required 1, available -10.");
    }

    @Test
    @Tag("CU-03.09")
    void sellByAmountIsNeverReducedToTheAvailableShares() {
        OrderEvaluation evaluation = evaluate(OrderRequest.of(47L, "SELL", "MARKET", null, "50000.00", null));

        assertThat(evaluation.status()).isEqualTo(OrderStatus.REJECTED);
        assertThat(evaluation.quantity()).isEqualTo(54);
        assertThat(evaluation.rejection().reason()).isEqualTo(RejectionReason.INSUFFICIENT_SHARES);
        assertThat(evaluation.rejection().message()).isEqualTo(
                "Insufficient available shares of PAMP in account 10001: required 54, available 40.");
    }

    @Test
    @Tag("CU-03.16")
    void reservedSharesCannotBeSold() {
        OrderEvaluation evaluation = evaluate(
                OrderRequest.of(47L, "SELL", "MARKET", 11, null, null), PAMP, PAMP_QUOTE, new ShareBalance(40, 30));

        assertThat(evaluation.status()).isEqualTo(OrderStatus.REJECTED);
        assertThat(evaluation.rejection().reason()).isEqualTo(RejectionReason.INSUFFICIENT_SHARES);
        assertThat(evaluation.rejection().message()).isEqualTo(
                "Insufficient available shares of PAMP in account 10001: required 11, available 10.");
    }

    @Test
    @Tag("CU-03.18")
    void limitBuyAboveTheAvailableCashIsRejected() {
        OrderEvaluation evaluation = evaluate(OrderRequest.of(47L, "BUY", "LIMIT", 1000, null, "900.00"));

        assertThat(evaluation.status()).isEqualTo(OrderStatus.REJECTED);
        assertThat(evaluation.rejection().reason()).isEqualTo(RejectionReason.INSUFFICIENT_FUNDS);
        assertThat(evaluation.rejection().message()).isEqualTo(
                "Insufficient available cash in account 10001: required ARS 900000.00, available ARS 627500.00.");
    }

    @Test
    void quantityOfAnAmountOrderRoundsDownToWholeShares() {
        assertThat(evaluate(OrderRequest.of(47L, "BUY", "MARKET", null, "50000.00", null)).quantity())
                .isEqualTo(54);
        assertThat(evaluate(OrderRequest.of(47L, "SELL", "LIMIT", null, "20000.00", "950.00")).quantity())
                .isEqualTo(21);
        assertThat(evaluate(OrderRequest.of(47L, "BUY", "LIMIT", null, "19999.00", "950.00")).quantity())
                .isEqualTo(21);
        assertThat(evaluate(OrderRequest.of(47L, "BUY", "LIMIT", null, "949.99", "950.00")).quantity())
                .isZero();
    }

    @Test
    void buyWhoseTotalEqualsTheAvailableCashIsFilled() {
        OrderRequest request = OrderRequest.of(47L, "BUY", "MARKET", 2, null, null);
        CashBalance cash = new CashBalance(Money.of("1851.70"), Money.of("0.00"));

        OrderEvaluation evaluation = evaluator.evaluate(
                request, PAMP, Optional.of(PAMP_QUOTE), ACCOUNT, cash, PAMP_SHARES);

        assertThat(evaluation.status()).isEqualTo(OrderStatus.FILLED);
    }

    @Test
    void sellOfExactlyTheAvailableSharesIsFilled() {
        OrderEvaluation evaluation = evaluate(OrderRequest.of(47L, "SELL", "MARKET", 40, null, null));

        assertThat(evaluation.status()).isEqualTo(OrderStatus.FILLED);
    }

    @Test
    @Tag("CU-03.43")
    void rejectsLimitPriceAboveTheBand() {
        assertOutsidePampBand("BUY", "1111.03");
    }

    @Test
    @Tag("CU-03.44")
    void rejectsLimitPriceBelowTheBand() {
        assertOutsidePampBand("SELL", "740.67");
    }

    @Test
    @Tag("CU-03.45")
    void acceptsLimitPriceAtTheUpperBound() {
        OrderEvaluation evaluation = evaluate(OrderRequest.of(47L, "BUY", "LIMIT", 10, null, "1111.02"));

        assertThat(evaluation.status()).isEqualTo(OrderStatus.NEW);
        assertThat(evaluation.rejection()).isNull();
        assertThat(total(evaluation)).isEqualTo(Money.of("11110.20"));
        assertThat(SEED_CASH.available().minus(total(evaluation))).isEqualTo(Money.of("616389.80"));
    }

    @Test
    @Tag("CU-03.46")
    void rejectsBuyByAmountAboveTheMaximumQuantity() {
        assertQuantityTooLarge("BUY", "buy");
    }

    @Test
    void rejectsSellByAmountAboveTheMaximumQuantity() {
        assertQuantityTooLarge("SELL", "sell");
    }

    @Test
    void roundsTheBandInwardsAndIncludesBothBounds() {
        MarketQuote quote = new MarketQuote(Money.of("1.02"), Money.of("1.02"));
        for (String price : new String[]{"0.82", "0.83", "1.21", "1.22"}) {
            OrderEvaluation evaluation = evaluate(
                    OrderRequest.of(47L, "BUY", "LIMIT", 10, null, price), PAMP, quote, PAMP_SHARES);
            assertThat(evaluation.status()).as("limit price %s", price).isEqualTo(OrderStatus.NEW);
        }
        for (String price : new String[]{"0.81", "1.23"}) {
            // The amount alone would fail with AMOUNT_BELOW_PRICE.
            OrderEvaluation evaluation = evaluate(
                    OrderRequest.of(47L, "BUY", "LIMIT", null, "0.01", price), PAMP, quote, PAMP_SHARES);
            assertThat(evaluation.status()).as("limit price %s", price).isEqualTo(OrderStatus.REJECTED);
            assertThat(evaluation.quantity()).isZero();
            assertThat(evaluation.price()).isEqualTo(Money.of(price));
            assertThat(evaluation.rejection().reason()).isEqualTo(RejectionReason.PRICE_OUT_OF_RANGE);
        }
    }

    @Test
    void acceptsCalculatedQuantityAtTheMaximum() {
        OrderEvaluation evaluation = evaluate(
                OrderRequest.of(3L, "BUY", "LIMIT", null, "2147483647.99", "1.00"),
                PGR, null, new ShareBalance(0, 0));

        assertThat(evaluation.quantity()).isEqualTo(Integer.MAX_VALUE);
        assertThat(evaluation.rejection().reason()).isEqualTo(RejectionReason.INSUFFICIENT_FUNDS);
    }

    private void assertOutsidePampBand(String side, String price) {
        OrderEvaluation evaluation = evaluate(OrderRequest.of(47L, side, "LIMIT", 10, null, price));

        assertThat(evaluation.status()).isEqualTo(OrderStatus.REJECTED);
        assertThat(evaluation.quantity()).isEqualTo(10);
        assertThat(evaluation.price()).isEqualTo(Money.of(price));
        assertThat(evaluation.rejection().reason()).isEqualTo(RejectionReason.PRICE_OUT_OF_RANGE);
        assertThat(evaluation.rejection().message()).isEqualTo(
                "The limit price ARS " + price + " is outside the allowed range for PAMP: "
                        + "ARS 740.68 to ARS 1111.02 (20% around the last price ARS 925.85).");
    }

    private void assertQuantityTooLarge(String side, String verb) {
        OrderEvaluation evaluation = evaluate(
                OrderRequest.of(3L, side, "LIMIT", null, "9999999999.00", "0.01"),
                PGR, null, new ShareBalance(0, 0));

        assertThat(evaluation.status()).isEqualTo(OrderStatus.REJECTED);
        assertThat(evaluation.quantity()).isZero();
        assertThat(evaluation.price()).isEqualTo(Money.of("0.01"));
        assertThat(evaluation.rejection().reason()).isEqualTo(RejectionReason.QUANTITY_TOO_LARGE);
        assertThat(evaluation.rejection().message()).isEqualTo(
                "The amount ARS 9999999999.00 would " + verb + " more than 2147483647 shares of PGR at ARS 0.01.");
    }

    @Test
    void roundsBothBandBoundsInwardWhenHalfUpWouldWidenTheBand() {
        MarketQuote quote = new MarketQuote(Money.of("1.03"), Money.of("1.03"));
        for (String price : new String[]{"0.82", "1.24"}) {
            OrderEvaluation evaluation = evaluate(
                    OrderRequest.of(47L, "BUY", "LIMIT", 10, null, price), PAMP, quote, PAMP_SHARES);
            assertThat(evaluation.status()).as("limit price %s", price).isEqualTo(OrderStatus.REJECTED);
            assertThat(evaluation.rejection().reason()).isEqualTo(RejectionReason.PRICE_OUT_OF_RANGE);
            assertThat(evaluation.rejection().message()).isEqualTo(
                    "The limit price ARS " + price + " is outside the allowed range for PAMP: "
                            + "ARS 0.83 to ARS 1.23 (20% around the last price ARS 1.03).");
        }
        for (String price : new String[]{"0.83", "1.23"}) {
            OrderEvaluation evaluation = evaluate(
                    OrderRequest.of(47L, "BUY", "LIMIT", 10, null, price), PAMP, quote, PAMP_SHARES);
            assertThat(evaluation.status()).as("limit price %s", price).isEqualTo(OrderStatus.NEW);
        }
    }

    private static Money total(OrderEvaluation evaluation) {
        return evaluation.price().times(BigDecimal.valueOf(evaluation.quantity()));
    }

    private OrderEvaluation evaluate(OrderRequest request) {
        return evaluate(request, PAMP, PAMP_QUOTE, PAMP_SHARES);
    }

    private OrderEvaluation evaluate(
            OrderRequest request, Instrument instrument, MarketQuote quote, ShareBalance shares) {
        return evaluator.evaluate(request, instrument, Optional.ofNullable(quote), ACCOUNT, SEED_CASH, shares);
    }
}
