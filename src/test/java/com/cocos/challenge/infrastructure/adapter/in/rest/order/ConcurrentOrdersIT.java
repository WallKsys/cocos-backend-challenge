package com.cocos.challenge.infrastructure.adapter.in.rest.order;

import com.cocos.challenge.support.IntegrationTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
@Tag("integration")
class ConcurrentOrdersIT extends OrderLoadSupport {

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @Tag("CU-03.36")
    void simultaneousBuysCannotSpendTheSameCash() throws Exception {
        long before = orderCount();
        assertThat(availableCash()).isEqualByComparingTo("627500.00");

        var responses = simultaneously(2, index -> {
            var response = buy("competing-" + index, 400);
            assertThat(availableCash()).isNotNegative();
            return response;
        });

        assertThat(responses).allSatisfy(response -> assertThat(response.getStatus().value()).isEqualTo(201));
        var orders = responses.stream().map(EntityExchangeResult::getResponseBody).toList();
        assertThat(orders).extracting(order -> order.required("status").asText())
                .containsExactlyInAnyOrder("FILLED", "REJECTED");
        assertThat(orders).filteredOn(order -> order.required("status").asText().equals("REJECTED"))
                .singleElement().satisfies(order ->
                        assertThat(order.required("rejection").required("code").asText())
                                .isEqualTo("INSUFFICIENT_FUNDS"));
        assertThat(orderCount()).isEqualTo(before + 2);
        assertThat(availableCash()).isEqualByComparingTo("257160.00");
    }

    @Test
    @Tag("CU-03.36")
    void competingBuysWaitForTheAccountLockBeforeSpendingCash() throws Exception {
        long before = orderCount();
        try (var executor = Executors.newSingleThreadExecutor()) {
            var pending = new TransactionTemplate(transactionManager).execute(status -> {
                int holderPid = jdbc.sql("SELECT pg_backend_pid() FROM users WHERE id = :userId FOR UPDATE")
                        .param("userId", 1).query(Integer.class).single();
                var requests = executor.submit(() -> simultaneously(2, index -> buy("competing-" + index, 400)));
                awaitAccountLockWaiters(holderPid);
                return requests;
            });

            var responses = pending.get(20, TimeUnit.SECONDS);
            assertThat(responses).allSatisfy(response -> assertThat(response.getStatus().value()).isEqualTo(201));
            var orders = responses.stream().map(EntityExchangeResult::getResponseBody).toList();
            assertThat(orders).extracting(order -> order.required("status").asText())
                    .containsExactlyInAnyOrder("FILLED", "REJECTED");
            assertThat(orders).filteredOn(order -> order.required("status").asText().equals("REJECTED"))
                    .singleElement().satisfies(order ->
                            assertThat(order.required("rejection").required("code").asText())
                                    .isEqualTo("INSUFFICIENT_FUNDS"));
        }
        assertThat(orderCount()).isEqualTo(before + 2);
        assertThat(availableCash()).isEqualByComparingTo("257160.00");
    }

    private void awaitAccountLockWaiters(int holderPid) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            jdbc.sql("SELECT pg_stat_clear_snapshot()").query(String.class).single();
            // The second order can wait behind the first. Count account lock queries only:
            // an INSERT can also wait for the holder when checking the users foreign key.
            int waiters = jdbc.sql("""
                    WITH RECURSIVE account_lock_waits AS MATERIALIZED (
                        SELECT pid, pg_blocking_pids(pid) AS blockers
                        FROM pg_stat_activity
                        WHERE pid <> :holder AND query LIKE 'SELECT%FROM users%FOR UPDATE%'
                    ), queued(pid) AS (
                        SELECT CAST(:holder AS integer)
                        UNION
                        SELECT waits.pid
                        FROM account_lock_waits waits
                        JOIN queued ON queued.pid = ANY(waits.blockers)
                    )
                    SELECT count(*) FROM queued WHERE pid <> :holder
                    """).param("holder", holderPid).query(Integer.class).single();
            if (waiters == 2) {
                return;
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(20));
            if (Thread.currentThread().isInterrupted()) {
                throw new AssertionError("Interrupted while waiting for competing buys to queue");
            }
        }
        throw new AssertionError("Both competing buys must wait behind the held account lock within 5 seconds");
    }
}
