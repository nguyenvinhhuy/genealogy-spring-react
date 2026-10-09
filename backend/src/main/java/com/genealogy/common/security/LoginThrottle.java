package com.genealogy.common.security;

import com.genealogy.common.exception.TooManyRequestsException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

/** Limits password guesses per email and address pair, per address and per account, in memory. */
// In memory, not Redis: one backend serves one clan, and a restart forgetting the count is an acceptable loss.
@Component
public class LoginThrottle {

    // Guesses one address may make at one email, or one session at its own password, inside the window.
    static final int MAX_FAILURES = 5;

    // How long failures are remembered, and how long a lock lasts once reached.
    static final Duration WINDOW = Duration.ofMinutes(15);

    // An address is shared by a whole household or office, so it gets more room than one email.
    static final int MAX_FAILURES_PER_ADDRESS = 20;

    // How often expired keys are swept, so a burst of failures does not scan the map once per failure.
    static final Duration SWEEP_EVERY = Duration.ofMinutes(1);

    static final String LOCKED_MESSAGE =
            "Nhập sai mật khẩu quá nhiều lần. Hãy đợi " + WINDOW.toMinutes() + " phút rồi thử lại.";

    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();
    private final AtomicReference<Instant> lastSweep = new AtomicReference<>(Instant.EPOCH);
    private final Clock clock;

    /** Creates a throttle on the system clock. */
    public LoginThrottle() {
        this(Clock.systemUTC());
    }

    /**
     * Creates a throttle on a given clock, for tests.
     *
     * @param clock the clock
     */
    LoginThrottle(Clock clock) {
        this.clock = clock;
    }

    /**
     * Counts a sign-in attempt before its password is checked, refusing it when its pair or address is locked.
     *
     * @param email the email being signed in to
     * @param address where the attempt comes from, or null
     * @return the attempt, to be marked as succeeded when the password matched
     */
    public Attempt beginLogin(String email, String address) {
        // Keyed on the pair, not the email: a stranger typing a con cháu's email must not lock the owner out.
        return begin(List.of(new Limit(pairKey(email, address), MAX_FAILURES),
                new Limit(addressKey(address), MAX_FAILURES_PER_ADDRESS)));
    }

    /**
     * Counts a check of a signed-in member's current password, refusing it when that account is locked.
     *
     * @param memberId the account whose password is being checked
     * @return the attempt, to be marked as succeeded when the password matched
     */
    public Attempt beginPasswordCheck(Long memberId) {
        // Apart from sign-in, so a stranger's failed sign-ins cannot stop the owner changing their password.
        return begin(List.of(new Limit("member:" + memberId, MAX_FAILURES)));
    }

    /**
     * Reserves one failure under every limit, refusing as soon as one of them is used up.
     *
     * @param limits the keys to count under, with what each may have
     * @return the attempt holding the reservations
     */
    private Attempt begin(List<Limit> limits) {
        Instant now = clock.instant();
        sweepIfDue(now);
        List<String> reserved = new ArrayList<>();
        for (Limit limit : limits) {
            if (limit.key() == null) {
                continue;
            }
            // Check and count are one compute per key: checked first and counted after, concurrent guesses all got in.
            if (!reserve(limit, now)) {
                reserved.forEach(this::refund);
                throw new TooManyRequestsException(LOCKED_MESSAGE);
            }
            reserved.add(limit.key());
        }
        return new Attempt(this, List.copyOf(reserved));
    }

    /**
     * Adds one failure to a key unless it is already locked.
     *
     * @param limit the key and what it may have
     * @param now the current instant
     * @return true when the failure was counted, false when the key is locked
     */
    private boolean reserve(Limit limit, Instant now) {
        boolean[] locked = {false};
        attempts.compute(limit.key(), (ignored, current) -> {
            if (current == null || current.expired(now)) {
                return new Attempts(1, now.plus(WINDOW));
            }
            if (current.failures() >= limit.max()) {
                locked[0] = true;
                return current;
            }
            return new Attempts(current.failures() + 1, now.plus(WINDOW));
        });
        return !locked[0];
    }

    /**
     * Takes back one failure reserved under a key.
     *
     * @param key the key
     */
    private void refund(String key) {
        attempts.computeIfPresent(key, (ignored, current) -> current.failures() <= 1
                ? null
                : new Attempts(current.failures() - 1, current.until()));
    }

    /**
     * Drops expired keys at most once per sweep interval.
     *
     * @param now the current instant
     */
    private void sweepIfDue(Instant now) {
        Instant last = lastSweep.get();
        if (now.isBefore(last.plus(SWEEP_EVERY)) || !lastSweep.compareAndSet(last, now)) {
            return;
        }
        // A long-lived process must not keep every address that ever failed once.
        attempts.values().removeIf(entry -> entry.expired(now));
    }

    /**
     * Builds the key of an email tried from an address, ignoring the email's case.
     *
     * @param email the email, or null
     * @param address the address, or null
     * @return the key
     */
    private static String pairKey(String email, String address) {
        String normalised = email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
        return "pair:" + normalised + "|" + (address == null ? "" : address);
    }

    /**
     * Builds the key of an address.
     *
     * @param address the address, or null
     * @return the key, or null
     */
    private static String addressKey(String address) {
        return address == null ? null : "address:" + address;
    }

    /**
     * One guess in progress: counted as a failure until it is marked as succeeded.
     *
     * @param throttle the throttle that counted it
     * @param keys the keys a failure was reserved under, the guessed account's own key first
     */
    public record Attempt(LoginThrottle throttle, List<String> keys) {

        /** Marks the guess as right: forgets the account's failures and takes it back off the address. */
        public void succeeded() {
            throttle.attempts.remove(keys.getFirst());
            keys.stream().skip(1).forEach(throttle::refund);
        }
    }

    /**
     * One key with the number of failures it may have.
     *
     * @param key the key, or null when it does not apply
     * @param max the failures it may have
     */
    private record Limit(String key, int max) {
    }

    /**
     * The failures of one key, and when they stop counting.
     *
     * @param failures how many attempts have failed
     * @param until when the window, or the lock, ends
     */
    private record Attempts(int failures, Instant until) {

        /**
         * Reports whether the window has passed.
         *
         * @param now the current instant
         * @return true when these failures no longer count
         */
        boolean expired(Instant now) {
            return !now.isBefore(until);
        }
    }
}
