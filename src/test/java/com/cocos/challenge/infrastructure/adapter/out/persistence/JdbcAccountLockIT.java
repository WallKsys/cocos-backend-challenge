package com.cocos.challenge.infrastructure.adapter.out.persistence;

import com.cocos.challenge.domain.model.Account;
import com.cocos.challenge.domain.model.UserId;
import com.cocos.challenge.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class JdbcAccountLockIT {

    @Autowired
    JdbcClient jdbc;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Test
    void secondTransactionWaitsForTheFirstToReleaseTheUserRow() throws Exception {
        JdbcAccountLock accountLock = new JdbcAccountLock(jdbc);
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);
        UserId user = new UserId(1);
        Account expected = new Account(user, "10001");
        CompletableFuture<Integer> waiterPid = new CompletableFuture<>();

        try (var executor = Executors.newSingleThreadExecutor()) {
            var waiter = transactions.execute(status -> {
                assertThat(accountLock.lock(user)).contains(expected);
                int holderPid = backendPid();
                var waiting = executor.submit(() -> transactions.execute(other -> {
                    waiterPid.complete(backendPid());
                    return accountLock.lock(user);
                }));
                assertThat(waitForBlocker(holderPid, waiterPid)).isTrue();
                assertThat(waiting.isDone()).isFalse();
                return waiting;
            });
            assertThat(waiter.get(5, TimeUnit.SECONDS)).contains(expected);
        }
    }

    @Test
    void missingUserHasNoAccountToLock() {
        assertThat(new JdbcAccountLock(jdbc).lock(new UserId(999))).isEmpty();
    }

    private int backendPid() {
        return jdbc.sql("SELECT pg_backend_pid()").query(Integer.class).single();
    }

    private boolean waitForBlocker(int holderPid, CompletableFuture<Integer> waiterPid) {
        int pid;
        try {
            pid = waiterPid.get(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for the database session", e);
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException e) {
            throw new AssertionError("The second database session did not start", e);
        }
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (jdbc.sql("SELECT :holder = ANY(pg_blocking_pids(:waiter))")
                    .param("holder", holderPid).param("waiter", pid).query(Boolean.class).single()) {
                return true;
            }
            java.util.concurrent.locks.LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(20));
            if (Thread.currentThread().isInterrupted()) {
                throw new AssertionError("Interrupted while waiting for the row lock");
            }
        }
        return false;
    }
}
