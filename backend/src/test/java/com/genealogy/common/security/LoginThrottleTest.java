package com.genealogy.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.genealogy.common.exception.TooManyRequestsException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link LoginThrottle}. */
class LoginThrottleTest {

    private static final String EMAIL = "concháu@genealogy.vn";
    private static final String HOME = "198.51.100.4";

    @Test
    void aStrangerCannotLockTheOwnerOutByTypingTheirEmail() {
        LoginThrottle throttle = new LoginThrottle();
        for (int attempt = 0; attempt < LoginThrottle.MAX_FAILURES; attempt++) {
            throttle.beginLogin(EMAIL, "203.0.113.9");
        }
        assertThatThrownBy(() -> throttle.beginLogin(EMAIL, "203.0.113.9"))
                .isInstanceOf(TooManyRequestsException.class);

        // The owner, from home, is not the stranger (§8.11 #3).
        throttle.beginLogin(EMAIL, HOME).succeeded();
    }

    @Test
    void theEmailIsMatchedWithoutItsCase() {
        LoginThrottle throttle = new LoginThrottle();
        for (int attempt = 0; attempt < LoginThrottle.MAX_FAILURES; attempt++) {
            throttle.beginLogin(EMAIL.toUpperCase(), HOME);
        }

        assertThatThrownBy(() -> throttle.beginLogin(EMAIL, HOME)).isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void anAddressGuessingManyEmailsIsLockedAtItsOwnLimit() {
        LoginThrottle throttle = new LoginThrottle();
        for (int attempt = 0; attempt < LoginThrottle.MAX_FAILURES_PER_ADDRESS; attempt++) {
            throttle.beginLogin("nguoi" + attempt + "@genealogy.vn", HOME);
        }

        assertThatThrownBy(() -> throttle.beginLogin("moi@genealogy.vn", HOME))
                .isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void successfulSignInsDoNotUseUpTheAddress() {
        LoginThrottle throttle = new LoginThrottle();
        // A household signing in all day must never reach the per-address lock.
        for (int attempt = 0; attempt < LoginThrottle.MAX_FAILURES_PER_ADDRESS * 2; attempt++) {
            throttle.beginLogin("nguoi" + attempt + "@genealogy.vn", HOME).succeeded();
        }

        throttle.beginLogin(EMAIL, HOME);
    }

    @Test
    void aRightPasswordForgetsTheEarlierWrongOnes() {
        LoginThrottle throttle = new LoginThrottle();
        for (int attempt = 0; attempt < LoginThrottle.MAX_FAILURES - 1; attempt++) {
            throttle.beginLogin(EMAIL, HOME);
        }
        throttle.beginLogin(EMAIL, HOME).succeeded();

        for (int attempt = 0; attempt < LoginThrottle.MAX_FAILURES; attempt++) {
            throttle.beginLogin(EMAIL, HOME);
        }
    }

    @Test
    void theLockEndsWhenTheWindowPasses() {
        MutableClock clock = new MutableClock(Instant.parse("2026-10-02T00:00:00Z"));
        LoginThrottle throttle = new LoginThrottle(clock);
        for (int attempt = 0; attempt < LoginThrottle.MAX_FAILURES; attempt++) {
            throttle.beginLogin(EMAIL, HOME);
        }

        clock.now = clock.now.plus(LoginThrottle.WINDOW);

        throttle.beginLogin(EMAIL, HOME).succeeded();
    }

    @Test
    void concurrentGuessesGetNoMoreThanTheLimit() throws Exception {
        LoginThrottle throttle = new LoginThrottle();
        int guesses = 50;
        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger admitted = new AtomicInteger();
        List<Future<?>> done = new ArrayList<>();
        for (int guess = 0; guess < guesses; guess++) {
            done.add(pool.submit(() -> {
                start.await();
                try {
                    throttle.beginLogin(EMAIL, HOME);
                    admitted.incrementAndGet();
                } catch (TooManyRequestsException refused) {
                    // Expected once the limit is reached.
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> future : done) {
            future.get();
        }
        pool.shutdown();

        // Checked first and counted after, a burst of 50 all reached the password check (§8.11 #4).
        assertThat(admitted.get()).isEqualTo(LoginThrottle.MAX_FAILURES);
    }

    @Test
    void aPasswordCheckIsCountedPerAccountApartFromSignIn() {
        LoginThrottle throttle = new LoginThrottle();
        for (int attempt = 0; attempt < LoginThrottle.MAX_FAILURES; attempt++) {
            throttle.beginPasswordCheck(1L);
        }

        assertThatThrownBy(() -> throttle.beginPasswordCheck(1L)).isInstanceOf(TooManyRequestsException.class);
        throttle.beginPasswordCheck(2L);
        throttle.beginLogin(EMAIL, HOME);
    }

    /** A clock a test can move forward. */
    private static final class MutableClock extends Clock {

        private Instant now;

        /**
         * Creates a clock stopped at an instant.
         *
         * @param now the instant
         */
        MutableClock(Instant now) {
            this.now = now;
        }

        /**
         * Returns UTC.
         *
         * @return the zone
         */
        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        /**
         * Returns this clock, whose zone does not matter to the throttle.
         *
         * @param zone ignored
         * @return this clock
         */
        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        /**
         * Returns the instant the test last set.
         *
         * @return the instant
         */
        @Override
        public Instant instant() {
            return now;
        }
    }
}
