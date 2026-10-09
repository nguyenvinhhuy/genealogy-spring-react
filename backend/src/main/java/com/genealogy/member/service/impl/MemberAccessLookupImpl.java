package com.genealogy.member.service.impl;

import com.genealogy.common.security.MemberAccess;
import com.genealogy.common.security.MemberAccessLookup;
import com.genealogy.member.repository.MemberRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** The member feature's answer to {@link MemberAccessLookup}, read straight from the members table. */
// Its own bean: MemberServiceImpl needs SecurityConfig's PasswordEncoder, whose filter needs this — a cycle.
@Component
@RequiredArgsConstructor
public class MemberAccessLookupImpl implements MemberAccessLookup {

    private final MemberRepository memberRepository;

    /**
     * Finds an account's current role, active flag and credential version.
     *
     * @param memberId the member id the token names
     * @return the access state, or empty when the account no longer exists
     */
    @Override
    public Optional<MemberAccess> find(Long memberId) {
        // No transaction of its own: the repository query opens one, and this runs once per request.
        return memberRepository.findAccess(memberId);
    }
}
