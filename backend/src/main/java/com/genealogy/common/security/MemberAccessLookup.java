package com.genealogy.common.security;

import java.util.Optional;

/** Reads an account's current access state, implemented by the member feature so {@code common} needs none of it. */
// Every request asks: a token alone kept a disabled, demoted or reset account working for 15 minutes (§8.10 #10).
public interface MemberAccessLookup {

    /**
     * Finds an account's current role, active flag and credential version.
     *
     * @param memberId the member id the token names
     * @return the access state, or empty when the account no longer exists
     */
    Optional<MemberAccess> find(Long memberId);
}
