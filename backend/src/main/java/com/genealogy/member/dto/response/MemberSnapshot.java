package com.genealogy.member.dto.response;

import com.genealogy.common.model.Role;

/**
 * An account as the audit trail records it, without its password hash.
 *
 * @param id member id
 * @param fullName display name
 * @param email email address
 * @param role access level
 * @param active whether the account may sign in
 * @param credentialVersion bumped by every password change or reset, so the trail shows one happened
 */
public record MemberSnapshot(
        Long id,
        String fullName,
        String email,
        Role role,
        boolean active,
        int credentialVersion) {
}
