package com.cocos.challenge.infrastructure.adapter.in.rest.internal;

import com.cocos.challenge.application.port.in.GetPortfolioQuery;
import com.cocos.challenge.application.port.in.InternalToolsUseCase;
import com.cocos.challenge.application.port.in.OrderQuery;
import com.cocos.challenge.domain.model.CreatedUser;
import com.cocos.challenge.domain.model.InstrumentId;
import com.cocos.challenge.domain.model.Money;
import com.cocos.challenge.domain.model.OrderHistoryCriteria;
import com.cocos.challenge.domain.model.UserId;
import com.cocos.challenge.infrastructure.adapter.in.rest.format.ApiDecimals;
import com.cocos.challenge.infrastructure.adapter.in.rest.format.DecimalString;
import com.cocos.challenge.infrastructure.adapter.in.rest.order.OrderHistoryPageResponse;
import com.cocos.challenge.infrastructure.adapter.in.rest.order.OrderRecordResponse;
import com.cocos.challenge.infrastructure.adapter.in.rest.portfolio.PortfolioResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@ConditionalOnProperty(name = "cocos.internal-tools.enabled", havingValue = "true")
public class InternalToolsController {

    private final InternalToolsUseCase internalTools;
    private final GetPortfolioQuery portfolio;
    private final OrderQuery orders;

    public InternalToolsController(
            InternalToolsUseCase internalTools, GetPortfolioQuery portfolio, OrderQuery orders) {
        this.internalTools = internalTools;
        this.portfolio = portfolio;
        this.orders = orders;
    }

    @PostMapping("/api/internal/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void restoreSeedData() {
        internalTools.restoreSeedData();
    }

    @PostMapping("/api/internal/users")
    @ResponseStatus(HttpStatus.CREATED)
    public UserView createUser(@Valid @RequestBody CreateUserBody body) {
        return UserView.from(internalTools.createUser(body.email()));
    }

    @PutMapping("/api/internal/instruments/{instrumentId}/market-data")
    public MarketDataView updateMarketData(
            @PathVariable @Positive long instrumentId,
            @Valid @RequestBody MarketDataUpdateBody body) {
        Money close = Money.of(ApiDecimals.parse(body.close()));
        Money previousClose = body.previousClose() == null ? null : Money.of(ApiDecimals.parse(body.previousClose()));
        return MarketDataView.from(internalTools.updateMarketData(new InstrumentId(instrumentId), close, previousClose));
    }

    @GetMapping("/api/internal/users/{userId}/orders")
    public OrderHistoryPageResponse listOrders(@PathVariable @Positive int userId,
            @RequestParam(required = false) String status, @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String cursor) {
        var criteria = OrderHistoryCriteria.of(status, limit, cursor);
        return OrderHistoryPageResponse.from(orders.list(new UserId(userId), criteria));
    }

    @DeleteMapping("/api/internal/orders/{orderId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteOrder(@PathVariable @Positive long orderId) {
        internalTools.deleteOrder(orderId);
    }

    @PostMapping("/api/internal/orders/{orderId}/execute")
    public OrderRecordResponse executeOrder(@PathVariable @Positive long orderId) {
        return OrderRecordResponse.from(internalTools.executeOrder(orderId));
    }

    @GetMapping("/api/internal/users/{userId}/portfolio")
    public PortfolioResponse getPortfolio(@PathVariable @Positive int userId) {
        return PortfolioResponse.from(portfolio.get(new UserId(userId)));
    }

    public record CreateUserBody(@NotBlank @Email @Size(max = 255) String email) {
    }

    public record MarketDataUpdateBody(@NotNull @DecimalString String close, @DecimalString String previousClose) {
    }

    public record MarketDataView(
            long instrumentId, String ticker, LocalDate date, String close, String previousClose) {

        static MarketDataView from(InternalToolsUseCase.MarketDataResult result) {
            return new MarketDataView(
                    result.instrumentId().value(),
                    result.ticker(),
                    result.date(),
                    ApiDecimals.format(result.close()),
                    result.previousClose() == null ? null : ApiDecimals.format(result.previousClose()));
        }
    }

    public record UserView(int userId, String email, String accountNumber) {

        static UserView from(CreatedUser user) {
            return new UserView(Math.toIntExact(user.id().value()), user.email(), user.accountNumber());
        }
    }
}
