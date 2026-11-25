package com.cocos.challenge.infrastructure.adapter.in.rest.order;

import com.cocos.challenge.application.port.in.CancelOrderUseCase;
import com.cocos.challenge.application.port.out.AccountLock;
import com.cocos.challenge.application.port.out.OrderLedgerRepository;
import com.cocos.challenge.domain.exception.OrderNotCancellableException;
import com.cocos.challenge.domain.model.OrderStatus;
import com.cocos.challenge.domain.model.UserId;
import com.cocos.challenge.support.ForcedExecutionSimulator;
import com.cocos.challenge.support.IntegrationTest;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
@Tag("integration")
class CancelVersusExecutionIT extends OrderLoadSupport {

    private static final UserId USER = new UserId(1);
    private static final long ORDER = 7;

    @Autowired
    private CancelOrderUseCase cancellation;
    @Autowired
    private OrderLedgerRepository ledger;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private AccountLock lock;

    @RepeatedTest(20)
    @Tag("CU-04.08")
    void exactlyOneOperationChangesTheOrder() throws Exception {
        var execution = new ForcedExecutionSimulator(new TransactionTemplate(transactionManager), lock, jdbc);

        var updates = simultaneously(2, worker -> worker == 0 ? cancel() : execution.execute(USER, ORDER));

        assertThat(updates).containsExactlyInAnyOrder(0, 1);
        assertThat(ledger.findRecord(USER, ORDER).orElseThrow().status())
                .isEqualTo(updates.getFirst() == 1 ? OrderStatus.CANCELLED : OrderStatus.FILLED);
    }

    private int cancel() {
        try {
            var result = cancellation.cancel(USER, ORDER);
            assertThat(result.order().status()).isEqualTo(OrderStatus.CANCELLED);
            assertThat(result.alreadyCancelled()).isFalse();
            return 1;
        } catch (OrderNotCancellableException exception) {
            assertThat(exception).hasMessage("Order 7 cannot be cancelled because its status is FILLED."
                    + " Only NEW orders can be cancelled.");
            return 0;
        }
    }
}
