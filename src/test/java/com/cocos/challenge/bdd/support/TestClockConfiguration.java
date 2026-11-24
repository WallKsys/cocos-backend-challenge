package com.cocos.challenge.bdd.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

@TestConfiguration
public class TestClockConfiguration {

    @Bean
    @Primary
    TestClock testClock() {
        return new TestClock(ZoneOffset.UTC, new AtomicReference<>());
    }

    public static final class TestClock extends Clock {

        private final Clock systemClock;
        private final AtomicReference<Instant> fixed;

        private TestClock(ZoneId zone, AtomicReference<Instant> fixed) {
            this.systemClock = Clock.systemUTC().withZone(zone);
            this.fixed = fixed;
        }

        public void fix(Instant instant) {
            fixed.set(instant);
        }

        public void reset() {
            fixed.set(null);
        }

        @Override
        public ZoneId getZone() {
            return systemClock.getZone();
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new TestClock(zone, fixed);
        }

        @Override
        public Instant instant() {
            Instant instant = fixed.get();
            return instant == null ? systemClock.instant() : instant;
        }
    }
}
